package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.RoadGateway
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.platform.StoryGateway
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.session.withEulReul
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.math.ceil
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/** 이야기 하나가 지금 어디까지 진행됐는지. */
enum class NarrationPhase { TELLING, ASKING, ADDED, SKIPPED }

/** [lead]는 "5분 뒤"처럼 그곳이 언제 나오는지를 이야기를 시작한 시점 기준으로 적은 말이다. */
data class Narration(val ahead: PoiAhead, val phase: NarrationPhase, val text: String, val lead: String = "")

data class DocentState(
    val now: Instant,
    val driving: Boolean,
    val destination: Destination,
    val remaining: Duration,
    /** 회전이 가까워 말을 멈춘 상태면 그 회전. */
    val quiet: Maneuver?,
    /** 지금 들려주는 이야기. */
    val narration: Narration?,
    /** 아직 들려주지 않은, 앞에 있는 이야기. */
    val upcoming: List<PoiAhead>,
    /** 앞에 있는 곳 전부. 이미 들려줬지만 아직 지나지 않은 곳도 들어 있다. */
    val ahead: List<PoiAhead>,
    /** 이미 들려준 곳. */
    val told: List<Poi>,
    val waypoint: Destination?,
    /** 실제로 차를 세워 들른 곳. */
    val stamps: List<Destination>,
    val source: Source,
) {
    /** 들르기로 했고 아직 닿지 않은 곳. 한 번에 하나만 둔다. */
    val heading: Destination? get() = waypoint?.takeIf { it !in stamps }
}

/**
 * 차가 여행 가이드 노릇을 한다. 곧 지나갈 곳의 이야기를 미리 들려주되, 회전이 가까우면 말을 멈춘다.
 *
 * 스마트폰 오디오 가이드와 다른 점은 차가 아는 것을 쓴다는 것이다: 앞으로 갈 길(몇 분 뒤에 나오는지),
 * 어느 쪽 창밖인지, 그리고 지금 말해도 되는 순간인지.
 */
class DocentSession(
    private val time: TimeSource,
    private val navi: NaviGateway,
    private val vehicle: VehicleGateway,
    private val speech: SpeechGateway,
    private val road: RoadGateway,
    private val stories: StoryGateway,
    /** 이 거리 안으로 들어오면 이야기를 시작한다. */
    private val announceWithin: Int = 3_000,
    /** 이보다 가까우면 이미 늦었다고 보고 건너뛴다. 지나가며 듣는 이야기는 소용이 없다. */
    private val tooLate: Int = 500,
    /** 회전이 이 거리 안이면 말하지 않는다. */
    private val quietWithin: Int = 400,
    /** 이 거리 안에서 차를 세우면 들른 것으로 본다. */
    private val arrivedWithin: Int = 80,
) {
    private val toldIds = mutableSetOf<String>()
    private var narrating: Job? = null
    private var touched: CompletableDeferred<Boolean>? = null
    private var closedByTurn = false

    private val _state = MutableStateFlow<DocentState?>(null)
    val state: StateFlow<DocentState?> = _state

    suspend fun run(): Unit = coroutineScope {
        combine(time.now, navi.route, vehicle.driving, road.nextManeuver, stories.ahead) { now, route, driving, maneuver, ahead ->
            Input(now, route.destination, route.remainingDrive.value, route.remainingDrive.source, driving, maneuver, ahead)
        }.collect { input ->
            val quiet = input.maneuver?.takeIf { it.distanceMeters <= quietWithin }
            _state.update { previous ->
                val waypoint = previous?.waypoint
                val arrived = !input.driving && waypoint != null &&
                    input.ahead.any { it.poi.destination.id == waypoint.id && it.distanceMeters <= arrivedWithin }
                val stamps = previous?.stamps.orEmpty()
                DocentState(
                    now = input.now,
                    driving = input.driving,
                    destination = input.destination,
                    remaining = input.remaining,
                    quiet = quiet,
                    narration = previous?.narration,
                    upcoming = input.ahead.filter { it.poi.destination.id !in toldIds },
                    ahead = input.ahead,
                    told = previous?.told.orEmpty(),
                    waypoint = waypoint,
                    // 차를 실제로 세워야 들른 것으로 친다. 지나가기만 한 곳에는 찍지 않는다.
                    stamps = if (arrived && waypoint !in stamps) stamps + waypoint!! else stamps,
                    source = input.ahead.firstOrNull()?.poi?.source ?: input.source,
                )
            }
            // 묻는 중에 회전이 가까워지면 질문을 닫는다. 회전 직전에 경로 선택을 재촉하지 않는다.
            if (quiet != null) {
                touched?.let {
                    closedByTurn = true
                    it.complete(false)
                }
            }
            // 이야기 도중에 차를 세우면 멈춘다. 정차 화면에는 이야기가 보이지 않으므로, 다시 출발하면 그곳을 처음부터 들려준다.
            if (!input.driving && narrating?.isActive == true) {
                narrating?.cancel()
                narrating = null
                _state.value?.narration?.ahead?.poi?.let { poi ->
                    toldIds -= poi.destination.id
                    _state.update { it?.copy(narration = null, told = it.told - poi) }
                }
            }
            if (!input.driving || quiet != null || narrating?.isActive == true) return@collect
            val next = input.ahead.firstOrNull {
                it.poi.destination.id !in toldIds && it.settled && it.distanceMeters in tooLate..announceWithin
            } ?: return@collect
            toldIds += next.poi.destination.id
            // 곧바로 시작해 이야기 화면을 띄운다. 늦게 시작하면 그 사이에 다음 곳의 예고가 잠깐 비친다.
            narrating = launch(start = CoroutineStart.UNDISPATCHED) { narrate(next) }
        }
    }

    private suspend fun narrate(ahead: PoiAhead) {
        val poi = ahead.poi
        val name = poi.destination.name
        val speedNow = road.speedMetersPerSecond.first().coerceAtLeast(1.0)
        val minutes = ceil(ahead.distanceMeters / speedNow / 60).toInt()
        val lead = if (minutes <= 1) "곧" else "${minutes}분 뒤"
        val text = "$lead ${ahead.side.word()}에 $name. ${poi.story}"

        setNarration(Narration(ahead, NarrationPhase.TELLING, text, lead))
        _state.update { it?.copy(told = it.told + poi, upcoming = it.upcoming.filter { a -> a.poi != poi }) }
        speech.say(text)

        // 들를 곳은 한 번에 하나만 둔다. 이미 정했으면 묻지 않는다.
        if (poi.canStop && _state.value?.heading == null && _state.value?.quiet == null) {
            closedByTurn = false
            val question = "들러 볼까요?"
            setNarration(Narration(ahead, NarrationPhase.ASKING, question, lead))
            if (askByVoiceOrTouch(question) && navi.addWaypoint(poi.destination)) {
                val result = "${name.withEulReul()} 경유지로 넣었어요"
                _state.update { it?.copy(waypoint = poi.destination) }
                setNarration(Narration(ahead, NarrationPhase.ADDED, result, lead))
                speech.say(result)
            } else {
                val passing = if (closedByTurn) "회전이 가까워 그대로 지나갑니다" else "그대로 지나갑니다"
                setNarration(Narration(ahead, NarrationPhase.SKIPPED, passing, lead))
            }
        }
        setNarration(null)
    }

    /** 말로 답하거나 화면의 버튼을 누르거나, 먼저 온 쪽을 답으로 삼는다. 답이 없으면 지나간다. */
    private suspend fun askByVoiceOrTouch(question: String): Boolean = coroutineScope {
        val touch = CompletableDeferred<Boolean>().also { touched = it }
        val voice = async { speech.askYesNo(question) }
        try {
            select {
                voice.onAwait { it }
                touch.onAwait { it }
            }
        } finally {
            voice.cancel()
            touched = null
        }
    }

    /** "들러 볼까요?"에 화면으로 답한다. 묻고 있지 않을 때는 아무 일도 하지 않는다. */
    fun answer(yes: Boolean) {
        touched?.complete(yes)
    }

    /**
     * 앞에 있는 곳을 바로 경유지로 넣는다. 정차 화면의 "여기로 안내"가 부른다.
     * 이미 들르기로 한 곳이 있거나 내비가 받아 주지 않으면 false.
     */
    suspend fun guideTo(destinationId: String): Boolean {
        val current = _state.value ?: return false
        if (current.heading != null) return false
        val target = current.ahead.find { it.poi.destination.id == destinationId && it.poi.canStop } ?: return false
        if (!navi.addWaypoint(target.poi.destination)) return false
        _state.update { it?.copy(waypoint = target.poi.destination) }
        return true
    }

    private fun setNarration(narration: Narration?) {
        _state.update { it?.copy(narration = narration) }
    }

    private data class Input(
        val now: Instant,
        val destination: Destination,
        val remaining: Duration,
        val source: Source,
        val driving: Boolean,
        val maneuver: Maneuver?,
        val ahead: List<PoiAhead>,
    )
}

fun Side.word(): String = if (this == Side.LEFT) "왼쪽" else "오른쪽"
