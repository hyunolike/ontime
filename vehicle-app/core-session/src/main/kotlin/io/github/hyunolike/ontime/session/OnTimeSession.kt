package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.slack.AlternativePicker
import io.github.hyunolike.ontime.slack.Candidate
import io.github.hyunolike.ontime.slack.SlackCalculator
import io.github.hyunolike.ontime.slack.SlackInput
import io.github.hyunolike.ontime.slack.SlackLevelTracker
import io.github.hyunolike.ontime.slack.SlackThresholds
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 차량 입력을 모아 여유를 계산하고, 상태가 나빠지면 대안 하나를 말로 제안한다.
 * 계산은 여기서 끝나므로 서버가 없어도 숫자는 계속 움직인다.
 */
class OnTimeSession(
    private val plan: TripPlan,
    private val time: TimeSource,
    private val navi: NaviGateway,
    private val vehicle: VehicleGateway,
    private val speech: SpeechGateway,
    thresholds: SlackThresholds = SlackThresholds(),
    private val staleAfter: Duration = 2.minutes,
) {
    private val tracker = SlackLevelTracker(thresholds)
    private val proposedIds = mutableSetOf<String>()
    private var proposal: Job? = null

    private val _state = MutableStateFlow<SessionState?>(null)

    /** 첫 입력이 모이기 전에는 null이다. */
    val state: StateFlow<SessionState?> = _state

    /** 취소될 때까지 입력을 받아 [state]를 갱신한다. */
    suspend fun run(): Unit = coroutineScope {
        combine(time.now, navi.route, vehicle.driving, ::Triple).collect { (now, route, driving) ->
            val option = plan.parkingOptions.find { it.id == route.destination.id } ?: return@collect
            val input = SlackInput(
                deadline = plan.deadline,
                now = now,
                drive = route.remainingDrive.value,
                parking = option.parking,
                walk = option.walk,
                buffer = plan.buffer,
            )
            val slack = SlackCalculator.calculate(input)
            val change = tracker.update(slack.exact)
            val stale = route.remainingDrive.isStale(now, staleAfter)
            _state.update { previous ->
                SessionState(
                    now = now,
                    driving = driving,
                    destinationName = route.destination.name,
                    slack = slack,
                    level = checkNotNull(tracker.level),
                    untilDeadline = java.time.Duration.between(now, plan.deadline).toKotlinDuration(),
                    drive = input.drive,
                    parking = input.parking,
                    walk = input.walk,
                    buffer = input.buffer,
                    source = route.remainingDrive.source,
                    stale = stale,
                    suggestion = previous?.suggestion,
                )
            }
            val worsened = change?.from != null && change.to > change.from!!
            if (worsened && driving && !stale && proposal?.isActive != true) {
                proposal = launch { propose(input, route) }
            }
        }
    }

    private suspend fun propose(input: SlackInput, route: RouteStatus) {
        val candidates = plan.parkingOptions
            .filter { it.id != route.destination.id && it.id !in proposedIds }
            .mapNotNull { option ->
                navi.estimateRemainingDrive(Destination(option.id, option.name))?.let { Candidate(option, it) }
            }
        val alternative = AlternativePicker.pick(input, candidates) ?: return
        val target = Destination(alternative.option.id, alternative.option.name)
        proposedIds += target.id

        val text = "${target.name.withEuro()} 바꾸면 ${alternative.gain.inWholeMinutes}분을 아낄 수 있습니다"
        setSuggestion(Suggestion(text, SuggestionStatus.PROPOSED))

        if (!speech.askYesNo(text)) {
            setSuggestion(Suggestion(text, SuggestionStatus.DECLINED))
            return
        }
        if (!navi.changeDestination(target)) {
            val result = "경로를 바꾸지 못했습니다"
            setSuggestion(Suggestion(text, SuggestionStatus.FAILED, result))
            speech.say(result)
            return
        }
        // 경로가 실제로 바뀐 뒤의 여유를 말한다.
        val changed = state.first { it?.destinationName == target.name }!!
        val result = "목적지를 ${target.name.withEuro()} 바꿨어요. 여유 ${signedMinutes(changed.slack.displayMinutes)}"
        setSuggestion(Suggestion(text, SuggestionStatus.ACCEPTED, result))
        speech.say(result)
    }

    private fun setSuggestion(suggestion: Suggestion) {
        _state.update { it?.copy(suggestion = suggestion) }
    }
}
