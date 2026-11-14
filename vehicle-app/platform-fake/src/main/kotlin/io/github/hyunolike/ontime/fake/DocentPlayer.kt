package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.RoadGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.platform.StoryGateway
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** 경로의 몇 미터 지점에, 어느 쪽에 무엇이 있는지. */
data class RoutePoi(val poi: Poi, val atMeters: Int, val side: Side)

data class RouteManeuver(val atMeters: Int, val instruction: String)

/** 일정한 속도로 한 길을 달리는 드라이브. 값은 모두 설명용 예시다. */
data class DocentScenario(
    val start: Instant,
    val destination: Destination,
    val totalMeters: Int,
    val speedMetersPerSecond: Double,
    val pois: List<RoutePoi>,
    val maneuvers: List<RouteManeuver>,
)

/**
 * 드라이브를 [speed]배속으로 재생하며 시각, 경로, 앞에 있는 이야기, 다음 회전을 내보낸다.
 * 경유지를 넣으면 그곳에 닿았을 때 차를 세운다.
 */
class DocentPlayer(
    private val scenario: DocentScenario,
    private val speed: Double = 10.0,
    private val step: Duration = 2.seconds,
) : TimeSource, NaviGateway, VehicleGateway, RoadGateway, StoryGateway {
    private val offset = MutableStateFlow(Duration.ZERO)
    private val started = MutableStateFlow(false)
    private val waypoint = MutableStateFlow<RoutePoi?>(null)

    private fun traveled(offset: Duration): Int =
        (offset.inWholeMilliseconds / 1000.0 * scenario.speedMetersPerSecond).toInt().coerceAtMost(stopAt())

    /** 경유지가 있으면 거기서, 없으면 목적지에서 선다. */
    private fun stopAt(): Int = waypoint.value?.atMeters ?: scenario.totalMeters

    override val now: Flow<Instant> = offset.map { scenario.start.plus(it.toJavaDuration()) }

    override val route: Flow<RouteStatus> = offset.map { current ->
        val remaining = scenario.totalMeters - traveled(current)
        RouteStatus(
            scenario.destination,
            Reading(
                (remaining / scenario.speedMetersPerSecond).seconds,
                scenario.start.plus(current.toJavaDuration()),
                Source.REPLAY,
            ),
        )
    }

    override val driving: Flow<Boolean> =
        combine(started, offset, waypoint) { started, current, _ -> started && traveled(current) < stopAt() }

    override val nextManeuver: Flow<Maneuver?> = offset.map { current ->
        val at = traveled(current)
        scenario.maneuvers
            .filter { it.atMeters > at && it.atMeters - at <= MANEUVER_HORIZON }
            .minByOrNull { it.atMeters }
            ?.let { Maneuver(it.atMeters - at, it.instruction) }
    }

    override val speedMetersPerSecond: Flow<Double> = flowOf(scenario.speedMetersPerSecond)

    override val ahead: Flow<List<PoiAhead>> = offset.map { current ->
        val at = traveled(current)
        scenario.pois
            .filter { it.atMeters >= at }
            .sortedBy { it.atMeters }
            .map { PoiAhead(it.poi, it.atMeters - at, it.side) }
    }

    /** 멈출 곳에 닿을 때까지 재생한다. 경유지에 서 있다가 다시 부르면 그 경유지를 떠나 이어서 달린다. */
    suspend fun run() {
        started.value = true
        if (waypoint.value?.atMeters == traveled(offset.value)) waypoint.value = null
        while (traveled(offset.value) < stopAt()) {
            delay(step / speed)
            offset.value += step
        }
    }

    override suspend fun estimateRemainingDrive(to: Destination): Duration? = null

    override suspend fun changeDestination(to: Destination): Boolean = false

    override suspend fun addWaypoint(to: Destination): Boolean {
        val at = traveled(offset.value)
        val target = scenario.pois.find { it.poi.destination.id == to.id && it.atMeters > at } ?: return false
        // 닿아 있던 경유지를 새 경유지로 바꾸면 차가 다시 움직인다.
        waypoint.value = target
        return true
    }

    private companion object {
        const val MANEUVER_HORIZON = 600
    }
}

/**
 * 경주 시내를 지나 불국사로 가는 길. 대릉원과 첨성대를 차례로 지난다.
 * 거리와 방향, 속도는 설명용 예시이고, 해설은 널리 알려진 사실을 짧게 줄인 예시 문장이다.
 */
object ScenarioGyeongju {
    val BULGUKSA = Destination("gyeongju-bulguksa", "불국사", 35.7900, 129.3320, "경북 경주시 불국로 385")
    val DAEREUNGWON = Destination("gyeongju-daereungwon", "대릉원", 35.8383, 129.2106, "경북 경주시 황남동 31-1")
    val CHEOMSEONGDAE = Destination("gyeongju-cheomseongdae", "첨성대", 35.8347, 129.2190, "경북 경주시 인왕동 839-1")

    val daereungwon = Poi(
        DAEREUNGWON,
        "신라의 왕과 귀족이 잠든 무덤 스물세 기가 모여 있습니다. 천마도가 나온 천마총이 이 안에 있습니다.",
        canStop = true,
        source = Source.REPLAY,
    )
    val cheomseongdae = Poi(
        CHEOMSEONGDAE,
        "선덕여왕 때 세운 천문 관측대입니다. 천삼백 년 넘게 같은 자리에 서 있습니다.",
        canStop = true,
        source = Source.REPLAY,
    )

    val scenario = DocentScenario(
        start = Instant.parse("2026-12-05T01:20:00Z"), // 토요일 10:20 KST
        destination = BULGUKSA,
        totalMeters = 21_600,
        speedMetersPerSecond = 12.0,
        pois = listOf(
            RoutePoi(daereungwon, atMeters = 3_600, side = Side.RIGHT),
            RoutePoi(cheomseongdae, atMeters = 5_400, side = Side.LEFT),
        ),
        maneuvers = listOf(RouteManeuver(atMeters = 2_700, instruction = "우회전")),
    )
}
