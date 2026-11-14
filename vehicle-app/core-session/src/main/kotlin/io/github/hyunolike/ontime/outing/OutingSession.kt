package io.github.hyunolike.ontime.outing

import io.github.hyunolike.ontime.platform.CrowdForecast
import io.github.hyunolike.ontime.platform.CrowdGateway
import io.github.hyunolike.ontime.platform.CrowdLevel
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.session.Suggestion
import io.github.hyunolike.ontime.session.SuggestionStatus
import io.github.hyunolike.ontime.session.withEunNeun
import io.github.hyunolike.ontime.session.withEuro
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 나들이 일정 하나. 가려는 곳과, 붐빌 때 대신 갈 만한 곳들. */
data class OutingPlan(val title: String, val target: Destination, val alternatives: List<Destination>) {
    val places: List<Destination> get() = listOf(target) + alternatives
}

/** 어느 곳으로 갔을 때의 도착 시각과 그 시각의 혼잡 예보. */
data class PlaceForecast(
    val destination: Destination,
    val drive: Duration,
    val arrival: Instant,
    val crowd: CrowdForecast,
    val current: Boolean,
)

data class OutingState(
    val now: Instant,
    val driving: Boolean,
    val destination: Destination,
    val drive: Duration,
    /** 내비의 남은 시간으로 구한 도착 예정 시각. 혼잡 예보는 이 시각을 기준으로 읽는다. */
    val arrival: Instant,
    val crowd: CrowdForecast,
    /** 도착 시각 앞뒤의 시간대별 예보. */
    val hourly: List<CrowdForecast>,
    val source: Source,
    val suggestion: Suggestion? = null,
)

/**
 * 내비의 도착 예정 시각에 혼잡 예보를 겹쳐, 도착했을 때 붐빌 것 같으면 덜 붐비는 곳을 말로 제안한다.
 * 지금의 혼잡이 아니라 도착할 때의 혼잡을 본다는 점이 핵심이다.
 */
class OutingSession(
    private val plan: OutingPlan,
    private val time: TimeSource,
    private val navi: NaviGateway,
    private val vehicle: VehicleGateway,
    private val speech: SpeechGateway,
    private val crowd: CrowdGateway,
    /** 대안이 이보다 오래 더 걸리면 제안하지 않는다. */
    private val maxExtraDrive: Duration = 30.minutes,
) {
    private val proposedFor = mutableSetOf<String>()
    private var proposal: Job? = null
    private var last: Triple<Instant, RouteStatus, Boolean>? = null

    private val _state = MutableStateFlow<OutingState?>(null)

    /** 첫 입력과 예보가 모이기 전에는 null이다. */
    val state: StateFlow<OutingState?> = _state

    suspend fun run(): Unit = coroutineScope {
        combine(time.now, navi.route, vehicle.driving, ::Triple).collect { input ->
            val (now, route, driving) = input
            if (plan.places.none { it.id == route.destination.id }) return@collect
            last = input
            val drive = route.remainingDrive.value
            val arrival = now.plus(drive.toJavaDuration())
            val forecast = crowd.forecast(route.destination.id, arrival) ?: return@collect
            val hourly = crowd.hourly(route.destination.id, arrival.minusSeconds(3600), HOURS_SHOWN)
            _state.update { previous ->
                OutingState(
                    now = now,
                    driving = driving,
                    destination = route.destination,
                    drive = drive,
                    arrival = arrival,
                    crowd = forecast,
                    hourly = hourly,
                    source = if (forecast.source == Source.REPLAY) Source.REPLAY else route.remainingDrive.source,
                    suggestion = previous?.suggestion,
                )
            }
            val crowded = forecast.level >= CrowdLevel.BUSY
            if (driving && crowded && route.destination.id !in proposedFor && proposal?.isActive != true) {
                proposedFor += route.destination.id
                proposal = launch { propose(now, route, forecast) }
            }
        }
    }

    /** 일정의 모든 장소에 대해 지금 출발했을 때의 도착 시각과 혼잡 예보를 구한다. */
    suspend fun compare(): List<PlaceForecast> {
        val (now, route, _) = last ?: return emptyList()
        return plan.places.mapNotNull { place -> forecastFor(place, now, route) }
    }

    /** 사용자가 직접 고른 곳으로 목적지를 바꾼다. 정차 중 화면에서만 부른다. */
    suspend fun choose(destinationId: String): Boolean {
        val target = plan.places.find { it.id == destinationId } ?: return false
        if (last?.second?.destination?.id == target.id) return true
        val accepted = navi.changeDestination(target)
        speech.say(if (accepted) "목적지를 ${target.name.withEuro()} 바꿨어요" else "경로를 바꾸지 못했습니다")
        return accepted
    }

    private suspend fun forecastFor(place: Destination, now: Instant, route: RouteStatus): PlaceForecast? {
        val current = place.id == route.destination.id
        val drive = (if (current) route.remainingDrive.value else navi.estimateRemainingDrive(place)) ?: return null
        val arrival = now.plus(drive.toJavaDuration())
        val forecast = crowd.forecast(place.id, arrival) ?: return null
        return PlaceForecast(place, drive, arrival, forecast, current)
    }

    private suspend fun propose(now: Instant, route: RouteStatus, forecast: CrowdForecast) {
        val currentDrive = route.remainingDrive.value
        // 덜 붐비는 곳 가운데 가장 한적한 곳을 고르고, 같으면 빨리 도착하는 곳을 고른다.
        val best = plan.places
            .filter { it.id != route.destination.id }
            .mapNotNull { forecastFor(it, now, route) }
            .filter { it.crowd.level <= CrowdLevel.NORMAL && it.drive - currentDrive <= maxExtraDrive }
            .minWithOrNull(compareBy<PlaceForecast> { it.crowd.level }.thenBy { it.drive })
            ?: return

        val diff = (best.drive - currentDrive).inWholeMinutes
        val timing = when {
            diff > 0 -> "${diff}분 더 걸립니다"
            diff < 0 -> "${-diff}분 일찍 도착합니다"
            else -> "도착 시각은 같습니다"
        }
        val text = "도착할 때 ${route.destination.name.withEunNeun()} ${forecast.level.sentence()} 것으로 보입니다. " +
            "${best.destination.name.withEunNeun()} ${best.crowd.level.clause()} $timing"
        fun suggestion(status: SuggestionStatus, result: String? = null) = Suggestion(
            text,
            status,
            result,
            currentLabel = forecast.level.label(),
            expectedLabel = best.crowd.level.label(),
        )
        val spoken = suggestion(SuggestionStatus.PROPOSED).text
        setSuggestion(suggestion(SuggestionStatus.PROPOSED))

        if (!speech.askYesNo(spoken)) {
            setSuggestion(suggestion(SuggestionStatus.DECLINED))
            return
        }
        if (!navi.changeDestination(best.destination)) {
            val result = "경로를 바꾸지 못했습니다"
            setSuggestion(suggestion(SuggestionStatus.FAILED, result))
            speech.say(result)
            return
        }
        val changed = state.first { it?.destination?.id == best.destination.id }!!
        val result = "목적지를 ${best.destination.name.withEuro()} 바꿨어요. 도착 예보는 ${changed.crowd.level.label()}입니다"
        setSuggestion(suggestion(SuggestionStatus.ACCEPTED, result))
        speech.say(result)
    }

    private fun setSuggestion(suggestion: Suggestion) {
        _state.update { it?.copy(suggestion = suggestion) }
    }

    private companion object {
        const val HOURS_SHOWN = 5
    }
}

fun CrowdLevel.label(): String = when (this) {
    CrowdLevel.RELAXED -> "여유"
    CrowdLevel.NORMAL -> "보통"
    CrowdLevel.BUSY -> "붐빔"
    CrowdLevel.PACKED -> "매우 붐빔"
}

/** "~ 것으로 보입니다" 앞에 오는 꼴. */
private fun CrowdLevel.sentence(): String = when (this) {
    CrowdLevel.RELAXED -> "여유로울"
    CrowdLevel.NORMAL -> "보통일"
    CrowdLevel.BUSY -> "붐빌"
    CrowdLevel.PACKED -> "매우 붐빌"
}

/** 뒤에 문장이 이어지는 꼴. */
private fun CrowdLevel.clause(): String = when (this) {
    CrowdLevel.RELAXED -> "여유롭고"
    CrowdLevel.NORMAL -> "보통 수준이고"
    CrowdLevel.BUSY -> "붐비고"
    CrowdLevel.PACKED -> "매우 붐비고"
}
