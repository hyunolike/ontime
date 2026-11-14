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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
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
    private val thresholds: SlackThresholds = SlackThresholds(),
    private val staleAfter: Duration = 2.minutes,
    /** 제안에 답을 기다리는 시간. 음성을 못 알아들어도 이 시간 안에는 화면으로 답할 수 있다. */
    private val answerWindow: Duration = 12.seconds,
) {
    private val tracker = SlackLevelTracker(thresholds)
    private val proposedIds = mutableSetOf<String>()
    private var proposal: Job? = null
    private var lastInput: SlackInput? = null
    private var lastRoute: RouteStatus? = null
    private var touchAnswer: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow<SessionState?>(null)

    /** 첫 입력이 모이기 전에는 null이다. */
    val state: StateFlow<SessionState?> = _state

    /** 취소될 때까지 입력을 받아 [state]를 갱신한다. */
    suspend fun run(): Unit = coroutineScope {
        combine(time.now, navi.route, vehicle.driving, ::Triple).collect { (now, route, driving) ->
            val option = plan.parkingChoices.find { it.destination.id == route.destination.id }?.option ?: return@collect
            val input = SlackInput(
                deadline = plan.deadline,
                now = now,
                drive = route.remainingDrive.value,
                parking = option.parking,
                walk = option.walk,
                buffer = plan.buffer,
            )
            lastInput = input
            lastRoute = route
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

    /**
     * 일정의 첫 번째 주차장으로 안내를 시작한다. 내비에 경로가 없을 때 쓴다.
     * 요청이 받아들여졌는지만 돌려주고, 실제 경로는 [state]로 확인한다.
     */
    suspend fun startGuidance(): Boolean {
        val target = plan.parkingChoices.firstOrNull()?.destination ?: return false
        val accepted = navi.changeDestination(target)
        speech.say(if (accepted) "${target.name.withEuro()} 안내를 시작합니다" else "경로를 설정하지 못했습니다")
        return accepted
    }

    /**
     * 주차장마다 지금 골랐을 때의 예상 여유를 계산한다. 경로가 없으면 빈 목록이다.
     * 소요 시간을 알 수 없는 주차장은 뺀다.
     */
    suspend fun previewParking(): List<ParkingPreview> {
        val input = lastInput ?: return emptyList()
        val route = lastRoute ?: return emptyList()
        return plan.parkingChoices.mapNotNull { choice ->
            val current = choice.destination.id == route.destination.id
            val drive = if (current) input.drive else navi.estimateRemainingDrive(choice.destination)
            drive?.let {
                val slack = SlackCalculator.calculate(input.copy(drive = it, parking = choice.parking, walk = choice.walk))
                ParkingPreview(choice.destination, slack, thresholds.levelFor(slack.exact), it, choice.walk, current, choice.dailyFee)
            }
        }
    }

    /** 사용자가 직접 고른 주차장으로 목적지를 바꾼다. 정차 중 화면에서만 부른다. */
    suspend fun chooseParking(destinationId: String): Boolean {
        val target = plan.parkingChoices.find { it.destination.id == destinationId }?.destination ?: return false
        if (lastRoute?.destination?.id == target.id) return true
        val accepted = navi.changeDestination(target)
        speech.say(if (accepted) "목적지를 ${target.name.withEuro()} 바꿨어요" else "경로를 바꾸지 못했습니다")
        return accepted
    }

    /**
     * 화면의 버튼으로 제안에 답한다. 음성이 실패해도 시연과 사용이 멈추지 않게 하는 대비책이다.
     * 기다리는 제안이 없으면 아무 일도 하지 않는다.
     */
    fun answerProposal(accept: Boolean) {
        touchAnswer?.complete(accept)
    }

    private suspend fun propose(input: SlackInput, route: RouteStatus) {
        val candidates = plan.parkingChoices
            .filter { it.destination.id != route.destination.id && it.destination.id !in proposedIds }
            .mapNotNull { choice ->
                navi.estimateRemainingDrive(choice.destination)?.let { Candidate(choice.option, it) }
            }
        val alternative = AlternativePicker.pick(input, candidates) ?: return
        val target = plan.parkingChoices.first { it.destination.id == alternative.option.id }.destination
        proposedIds += target.id

        val text = "${target.name.withEuro()} 바꾸면 ${alternative.gain.inWholeMinutes}분을 아낄 수 있습니다"
        val before = SlackCalculator.calculate(input).displayMinutes
        val after = alternative.newSlack.displayMinutes
        fun suggestion(status: SuggestionStatus, result: String? = null) =
            Suggestion(text, status, result, currentMinutes = before, expectedMinutes = after)
        setSuggestion(suggestion(SuggestionStatus.PROPOSED))

        if (!awaitAnswer(text)) {
            setSuggestion(suggestion(SuggestionStatus.DECLINED))
            return
        }
        if (!navi.changeDestination(target)) {
            val result = "경로를 바꾸지 못했습니다"
            setSuggestion(suggestion(SuggestionStatus.FAILED, result))
            speech.say(result)
            return
        }
        // 경로가 실제로 바뀐 뒤의 여유를 말한다.
        val changed = state.first { it?.destinationName == target.name }!!
        val result = "목적지를 ${target.name.withEuro()} 바꿨어요. 여유 ${signedMinutes(changed.slack.displayMinutes)}"
        setSuggestion(suggestion(SuggestionStatus.ACCEPTED, result))
        speech.say(result)
    }

    /**
     * 음성과 화면 중 먼저 온 답을 쓴다. 음성으로 수락하면 바로 바꾸고, 음성을 못 알아들었거나
     * 거절로 들렸으면 남은 시간 동안 화면의 답을 기다린다. 답이 없으면 경로를 유지한다.
     */
    private suspend fun awaitAnswer(prompt: String): Boolean = coroutineScope {
        val touch = CompletableDeferred<Boolean>()
        touchAnswer = touch
        val voice = async { speech.askYesNo(prompt) }
        val accepted = withTimeoutOrNull(answerWindow) {
            select {
                voice.onAwait { heardYes -> heardYes || touch.await() }
                touch.onAwait { it }
            }
        } ?: false
        touchAnswer = null
        voice.cancel()
        accepted
    }

    private fun setSuggestion(suggestion: Suggestion) {
        _state.update { it?.copy(suggestion = suggestion) }
    }
}
