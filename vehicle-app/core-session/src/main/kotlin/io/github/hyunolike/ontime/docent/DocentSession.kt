package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.ChargeState
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.LocationGateway
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/** 앱이 다시 시작돼도 이어 가려고 저장하는 것. 장소는 id로만 남긴다. */
data class DocentMemory(val destinationId: String, val toldIds: List<String>, val stampIds: List<String>) {
    /** "목적지|들은 곳,...|도장,..." 한 줄. id에는 |와 ,가 들어가지 않는다. */
    fun encode(): String = listOf(destinationId, toldIds.joinToString(","), stampIds.joinToString(",")).joinToString("|")

    companion object {
        fun decode(text: String?): DocentMemory? {
            val parts = text?.split("|")?.takeIf { it.size == 3 && it[0].isNotEmpty() } ?: return null
            fun ids(s: String) = s.split(",").filter { it.isNotEmpty() }
            return DocentMemory(parts[0], ids(parts[1]), ids(parts[2]))
        }
    }
}

/** 이야기 하나가 지금 어디까지 진행됐는지. */
enum class NarrationPhase { TELLING, ASKING, ADDED, SKIPPED }

/** [lead]는 "5분 뒤"처럼 그곳이 언제 나오는지를 이야기를 시작한 시점 기준으로 적은 말이다. */
data class Narration(
    val ahead: PoiAhead,
    val phase: NarrationPhase,
    val text: String,
    val lead: String = "",
    /** 이번에 고른 해설. 시간이 촉박하면 짧은 해설이다. */
    val story: String = "",
)

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
    /** 충전 상태. 지원하지 않으면 늘 충전하지 않는 것으로 둔다. */
    val charge: ChargeState = ChargeState.NONE,
    /** 차를 세우고 충전하는 동안 걸어서 둘러볼 곳. 달리거나 충전하지 않으면 비어 있다. */
    val walkable: List<Walkable> = emptyList(),
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
    /** 오른쪽 장소에 "조수석 쪽"을 덧붙인다. 운전석이 왼쪽인 차에서 동승자에게 보라고 알리기 위해서다. */
    private val seatHint: Boolean = false,
    /** 충전하는 동안 걸어서 둘러볼 곳을 고를 때 쓰는 지금 위치. 없으면 그 목록을 만들지 않는다. */
    private val walkFrom: LocationGateway? = null,
    /** 이 길에 있는 장소 전부. 지나온 곳도 걸어서 갈 수 있으므로 앞에 있는 곳만으로는 부족하다. */
    private val catalog: () -> List<Poi> = { emptyList() },
    /** 앱이 다시 시작되기 전에 들은 곳과 방문 도장. 목적지가 같을 때만 이어 간다. */
    private val memory: DocentMemory? = null,
    /**
     * 들를지 묻고 답을 기다리는 시간. 넘으면 지나간다. 무한이면 음성 쪽의 답(예시 음성은 정해 둔 시간 뒤 "아니요")을
     * 기다린다. 실제 음성은 답이 없으면 null을 주므로 반드시 정해 둔다.
     */
    private val askWindow: Duration = Duration.INFINITE,
) {
    private var restored = false

    private var latestCharge = ChargeState.NONE
    private var latestWalkable = emptyList<Walkable>()

    private val toldIds = mutableSetOf<String>()
    private var narrating: Job? = null
    private var touched: CompletableDeferred<Boolean>? = null
    private var closedByTurn = false

    private val _state = MutableStateFlow<DocentState?>(null)
    val state: StateFlow<DocentState?> = _state

    suspend fun run(): Unit = coroutineScope {
        // 충전 상태와 걸어서 갈 곳은 따로 모은다. 주행 판단에 쓰는 입력과 섞지 않는다.
        launch {
            val fixes: Flow<Fix?> = walkFrom?.fixes ?: flowOf(null)
            combine(vehicle.charge, vehicle.driving, fixes) { charge, driving, fix ->
                charge to if (!driving && charge.charging && fix != null) nearbyOnFoot(fix, catalog()) else emptyList()
            }.collect { (charge, walkable) ->
                latestCharge = charge
                latestWalkable = walkable
                _state.update { it?.copy(charge = charge, walkable = walkable) }
            }
        }
        combine(time.now, navi.route, vehicle.driving, road.nextManeuver, stories.ahead) { now, route, driving, maneuver, ahead ->
            Input(now, route.destination, route.remainingDrive.value, route.remainingDrive.source, driving, maneuver, ahead)
        }.collect { input ->
            val quiet = input.maneuver?.takeIf { it.distanceMeters <= quietWithin }
            if (!restored) restore(input.destination)
            _state.update { previous ->
                val waypoint = previous?.waypoint
                val arrived = !input.driving && waypoint != null &&
                    input.ahead.any { it.poi.destination.id == waypoint.id && it.distanceMeters <= arrivedWithin }
                val stamps = previous?.stamps ?: restoredStamps
                DocentState(
                    now = input.now,
                    driving = input.driving,
                    destination = input.destination,
                    remaining = input.remaining,
                    quiet = quiet,
                    narration = previous?.narration,
                    upcoming = input.ahead.filter { it.poi.destination.id !in toldIds },
                    ahead = input.ahead,
                    told = previous?.told ?: restoredTold,
                    waypoint = waypoint,
                    // 차를 실제로 세워야 들른 것으로 친다. 지나가기만 한 곳에는 찍지 않는다.
                    stamps = if (arrived && waypoint !in stamps) stamps + waypoint!! else stamps,
                    // 출처 표시는 경로를 따른다. 장소마다 출처가 달라도(관광공사 데이터 등) 재생 중이라는 표시는 남아야 한다.
                    source = input.source,
                    charge = latestCharge,
                    walkable = latestWalkable,
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
            // 해설 본문을 듣던 중이면 들려주지 않은 것으로 되돌리고, 질문이나 확인 문장 단계였으면 이미 들은 것으로 둔다.
            if (!input.driving && narrating?.isActive == true) {
                narrating?.cancel()
                narrating = null
                val narration = _state.value?.narration
                val poi = narration?.ahead?.poi
                if (poi != null && narration.phase == NarrationPhase.TELLING) {
                    toldIds -= poi.destination.id
                    _state.update { it?.copy(narration = null, told = it.told - poi) }
                } else {
                    _state.update { it?.copy(narration = null) }
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
        // 남은 시간에 맞춰 해설 길이를 고른다.
        val others = _state.value?.ahead.orEmpty().filter { it.poi.destination.id !in toldIds }
        val story = pickStory(ahead, speedNow, road.nextManeuver.first(), others, announceWithin)
        val where = if (seatHint && ahead.side == Side.RIGHT) "오른쪽, 조수석 쪽" else ahead.side.word()
        val text = "$lead ${where}에 $name. $story"

        setNarration(Narration(ahead, NarrationPhase.TELLING, text, lead, story))
        _state.update { it?.copy(told = it.told + poi, upcoming = it.upcoming.filter { a -> a.poi != poi }) }
        speech.say(text)

        // 들를 곳은 한 번에 하나만 둔다. 이미 정했으면 묻지 않는다.
        if (poi.canStop && _state.value?.heading == null && _state.value?.quiet == null) {
            closedByTurn = false
            val question = "들러 볼까요?"
            setNarration(Narration(ahead, NarrationPhase.ASKING, question, lead, story))
            if (askByVoiceOrTouch(question) && navi.addWaypoint(poi.destination)) {
                val result = "${name.withEulReul()} 경유지로 넣었어요"
                _state.update { it?.copy(waypoint = poi.destination) }
                setNarration(Narration(ahead, NarrationPhase.ADDED, result, lead, story))
                speech.say(result)
            } else {
                val passing = if (closedByTurn) "회전이 가까워 그대로 지나갑니다" else "그대로 지나갑니다"
                setNarration(Narration(ahead, NarrationPhase.SKIPPED, passing, lead, story))
            }
        }
        setNarration(null)
    }

    /** 말로 답하거나 화면의 버튼을 누르거나, 먼저 온 쪽을 답으로 삼는다. 답이 없으면 지나간다. */
    private suspend fun askByVoiceOrTouch(question: String): Boolean = coroutineScope {
        val touch = CompletableDeferred<Boolean>().also { touched = it }
        val voice = async { speech.listenYesNo(question) }
        try {
            withTimeoutOrNull(askWindow) {
                // 말로 답했으면 그 답, 알아듣지 못했으면(null) 버튼을 계속 기다린다.
                select<Boolean?> {
                    voice.onAwait { it }
                    touch.onAwait { it }
                } ?: touch.await()
            } ?: false
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

    private var restoredTold = emptyList<Poi>()
    private var restoredStamps = emptyList<Destination>()

    /** 목적지가 같으면 저장해 둔 들은 곳과 도장을 되살린다. 경유지는 되살리지 않는다(재생기와 내비에 다시 넣어야 하므로). */
    private fun restore(destination: Destination) {
        restored = true
        val saved = memory?.takeIf { it.destinationId == destination.id } ?: return
        val places = catalog()
        restoredTold = places.filter { it.destination.id in saved.toldIds }
        restoredStamps = places.map { it.destination }.filter { it.id in saved.stampIds }
        toldIds += restoredTold.map { it.destination.id }
    }

    /** 지금까지의 들은 곳과 도장. 앱이 다시 시작될 때 이어 가려고 저장한다. */
    fun snapshot(): DocentMemory? = _state.value?.let { s ->
        DocentMemory(s.destination.id, s.told.map { it.destination.id }, s.stamps.map { it.id })
    }

    /** 들은 곳, 들를 곳, 도장을 모두 지운다. 시연을 처음부터 다시 할 때 쓴다. */
    fun reset() {
        narrating?.cancel()
        narrating = null
        touched = null
        toldIds.clear()
        restoredTold = emptyList()
        restoredStamps = emptyList()
        _state.update { it?.copy(narration = null, told = emptyList(), waypoint = null, stamps = emptyList()) }
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
