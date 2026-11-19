package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 경로 요청과 경유지 추가, 해제는 실제 내비([primary])에 보내고, 남은 시간은 재생기([player])로 계산한다.
 *
 * 에뮬레이터의 내비 차는 움직이지 않아 남은 시간이 줄지 않고 경유지 도착도 알 수 없다.
 * 그래서 화면에 보이는 값은 재생 값으로 두고, 내비는 경로와 경유지를 실제로 받아 화면에 그리는 일만 맡는다.
 * [primary]의 호출이 실패해도 재생은 멈추지 않는다.
 */
class ReplayBackedNavi(
    private val primary: NaviGateway,
    private val player: TrackPlayer,
    private val routeConfirmTimeout: Duration = 20.seconds,
    private val confirmRadiusMeters: Int = 300,
    /** 내비 값이 이보다 오래되지 않았으면 지금 안내 중인 경로로 본다. 내비는 10초마다 조회한다. */
    private val freshFor: Duration = 30.seconds,
    private val clock: () -> Instant = Instant::now,
) : NaviGateway {
    private val destination = player.track.destination

    /** 내비에 경로를 요청했는가. */
    private var routed = false

    /** 들른 경유지가 내비에 남아 있는가. */
    private var visited = false

    override val route: Flow<RouteStatus> = player.remainingFlow.map { (remaining, at) ->
        RouteStatus(destination, Reading(remaining, at, Source.REPLAY))
    }

    /** 출발하거나 들른 곳에서 다시 출발한다. 처음이면 내비에 경로를 요청한다. */
    suspend fun drive() {
        if (!routed) ensureRoute()
        if (player.atVisitedStop && visited) {
            safely { primary.removeWaypoint() }
            visited = false
        }
        player.run()
        visited = player.atVisitedStop
    }

    override suspend fun addWaypoint(to: Destination): Boolean {
        if (player.canStopAt(to) == null) return false
        if (!routed && !ensureRoute()) return false
        if (visited) {
            safely { primary.removeWaypoint() }
            visited = false
        }
        if (!safely { primary.addWaypoint(to) }) return false
        // 내비가 받는 사이에 재생기가 그곳을 지났으면 되돌린다.
        if (!player.stopAt(to)) {
            safely { primary.removeWaypoint() }
            return false
        }
        return true
    }

    override suspend fun removeWaypoint(): Boolean = safely { primary.removeWaypoint() }

    override suspend fun estimateRemainingDrive(to: Destination): Duration? =
        runCatching { primary.estimateRemainingDrive(to) }.getOrNull()

    override suspend fun changeDestination(to: Destination): Boolean = safely { primary.changeDestination(to) }

    /** 시험용: 경로를 이미 요청한 것으로 둔다. */
    internal fun markRouted() {
        routed = true
    }

    /**
     * 내비가 시연 목적지로 안내하게 한다. 경로가 잡혔다고 확인되면 true.
     *
     * 내비가 이미 그 목적지로 안내 중이면 다시 요청하지 않는다. 다시 요청하면 내비가 경로를 새로 잡으며 경유지를 지운다.
     * 요청했다면 요청 뒤에 받은 값만 확인으로 친다. 요청 전에 받아 둔 값으로 확인하면, 내비가 경로를 다시 잡는
     * 동안 경유지를 보내 버려 경유지가 사라진다(2026-11-20 에뮬레이터에서 확인).
     */
    private suspend fun ensureRoute(): Boolean {
        routed = true
        if (alreadyRouted()) return true
        val requestedAt = clock()
        safely { primary.changeDestination(destination) }
        return withTimeoutOrNull(routeConfirmTimeout) {
            runCatching {
                primary.route.first { isOurs(it) && !it.remainingDrive.receivedAt.isBefore(requestedAt) }
            }.isSuccess
        } == true
    }

    /** 내비가 마지막으로 알려 준 경로가 최근 값이고 시연 목적지면 true. 값이 곧바로 오지 않으면 false. */
    private suspend fun alreadyRouted(): Boolean {
        val latest = withTimeoutOrNull(LATEST_WAIT) { runCatching { primary.route.first() }.getOrNull() } ?: return false
        val age = java.time.Duration.between(latest.remainingDrive.receivedAt, clock()).toKotlinDuration()
        return isOurs(latest) && age <= freshFor
    }

    private fun isOurs(route: RouteStatus): Boolean = distanceMeters(route.destination, destination) <= confirmRadiusMeters

    private companion object {
        val LATEST_WAIT = 300.milliseconds
    }

    private suspend fun safely(call: suspend () -> Boolean): Boolean = runCatching { call() }.getOrDefault(false)

    private fun distanceMeters(a: Destination, b: Destination): Double {
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = p2 - p1
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_000.0 * asin(sqrt(h))
    }
}
