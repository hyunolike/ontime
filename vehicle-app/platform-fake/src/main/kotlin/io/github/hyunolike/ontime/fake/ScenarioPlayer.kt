package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
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
import kotlinx.coroutines.flow.map

/**
 * 시나리오를 [speed]배속으로 재생하며 시각, 경로, 주행 여부를 내보낸다.
 * 내보내는 값의 출처는 모두 [Source.REPLAY]다.
 */
class ScenarioPlayer(
    private val scenario: Scenario,
    private val speed: Double = 20.0,
    private val step: Duration = 5.seconds,
) : TimeSource, NaviGateway, VehicleGateway {
    private val offset = MutableStateFlow(scenario.playFrom)
    private val destinationId = MutableStateFlow(scenario.initialDestinationId)
    private val started = MutableStateFlow(false)

    override val now: Flow<Instant> = offset.map(::instantAt)

    override val route: Flow<RouteStatus> = combine(offset, destinationId, ::statusAt)

    override val driving: Flow<Boolean> =
        combine(started, route) { started, status -> started && status.remainingDrive.value > Duration.ZERO }

    /** 도착할 때까지 재생한다. */
    suspend fun run() {
        started.value = true
        while (statusAt(offset.value, destinationId.value).remainingDrive.value > Duration.ZERO) {
            delay(step / speed)
            offset.value += step
        }
    }

    override suspend fun estimateRemainingDrive(to: Destination): Duration? =
        scenario.route(to.id)?.remainingAt(offset.value)

    override suspend fun changeDestination(to: Destination): Boolean {
        if (scenario.route(to.id) == null) return false
        destinationId.value = to.id
        return true
    }

    private fun instantAt(offset: Duration): Instant = scenario.start.plus(offset.toJavaDuration())

    private fun statusAt(offset: Duration, destinationId: String): RouteStatus {
        val route = checkNotNull(scenario.route(destinationId))
        return RouteStatus(
            route.destination,
            Reading(route.remainingAt(offset), instantAt(offset), Source.REPLAY),
        )
    }
}
