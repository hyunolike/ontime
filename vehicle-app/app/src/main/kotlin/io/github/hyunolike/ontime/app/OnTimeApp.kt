package io.github.hyunolike.ontime.app

import android.app.Application
import io.github.hyunolike.ontime.outing.OutingSession
import io.github.hyunolike.ontime.outing.OutingState
import io.github.hyunolike.ontime.outing.PlaceForecast
import io.github.hyunolike.ontime.session.OnTimeSession
import io.github.hyunolike.ontime.session.ParkingPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OnTimeApp : Application() {
    /** 화면보다 오래 사는 실행 상태. 화면이 뒤로 가도 계산과 음성 안내가 이어진다. */
    val runtime: OnTimeRuntime by lazy { OnTimeRuntime(this) }
}

/** 일정의 종류. 주말 나들이는 도착할 때의 혼잡을, 공항은 마감까지의 여유를 본다. */
enum class Mode { OUTING, AIRPORT }

class OnTimeRuntime(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val platform = Platform.create(app)
    private val session = OnTimeSession(platform.plan, platform.time, platform.navi, platform.vehicle, platform.speech)

    val plan = platform.plan
    val departureLabel = platform.departureLabel
    val canPlay = platform.play != null
    val keepAlive = platform.keepAlive
    val state = session.state

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started

    private val _previews = MutableStateFlow<List<ParkingPreview>>(emptyList())

    /** 주차장별 예상 여유. 정차 중에만 주기적으로 다시 계산한다. */
    val previews: StateFlow<List<ParkingPreview>> = _previews

    private val _choosing = MutableStateFlow<String?>(null)

    /** 사용자가 고른 뒤 경로가 바뀌기를 기다리는 주차장의 id. */
    val choosing: StateFlow<String?> = _choosing

    /** 화면에 띄운 일정. null이면 일정 목록(홈)이다. */
    private val _mode = MutableStateFlow(if (platform.outing == null) Mode.AIRPORT else null)
    val mode: StateFlow<Mode?> = _mode

    val outing = platform.outing
    private val outingSession = outing?.let { OutingSession(it.plan, it.time, it.navi, it.vehicle, it.speech, it.crowd) }
    val outingState: StateFlow<OutingState?> = outingSession?.state ?: MutableStateFlow(null)

    private val _outingStarted = MutableStateFlow(false)
    val outingStarted: StateFlow<Boolean> = _outingStarted

    private val _outingOptions = MutableStateFlow<List<PlaceForecast>>(emptyList())

    /** 장소별 도착 시각과 혼잡 예보. 정차 중에만 다시 계산한다. */
    val outingOptions: StateFlow<List<PlaceForecast>> = _outingOptions

    private val _outingChoosing = MutableStateFlow<String?>(null)
    val outingChoosing: StateFlow<String?> = _outingChoosing

    init {
        if (outingSession != null) {
            scope.launch { outingSession.run() }
            scope.launch {
                outingState.map { it != null && !it.driving }.distinctUntilChanged().collectLatest { stopped ->
                    while (stopped) {
                        _outingOptions.value = outingSession.compare()
                        delay(PREVIEW_INTERVAL_MS)
                    }
                }
            }
        }
        scope.launch { session.run() }
        scope.launch {
            // 주행 중에는 비교 화면을 보여 주지 않으므로 소요 시간 조회도 하지 않는다.
            state.map { it != null && !it.driving }.distinctUntilChanged().collectLatest { stopped ->
                while (stopped) {
                    _previews.value = session.previewParking()
                    delay(PREVIEW_INTERVAL_MS)
                }
            }
        }
    }

    fun select(mode: Mode?) {
        _mode.value = mode
    }

    fun startOutingDemo() {
        val play = outing?.play ?: return
        if (_outingStarted.compareAndSet(expect = false, update = true)) {
            scope.launch { play() }
        }
    }

    fun chooseOuting(destinationId: String) {
        val current = outingSession ?: return
        if (_outingChoosing.value != null) return
        _outingChoosing.value = destinationId
        scope.launch {
            current.choose(destinationId)
            delay(ROUTE_SETTLE_MS)
            _outingOptions.value = current.compare()
            _outingChoosing.value = null
        }
    }

    fun startGuidance() {
        scope.launch { session.startGuidance() }
    }

    fun chooseParking(destinationId: String) {
        if (_choosing.value != null) return
        _choosing.value = destinationId
        scope.launch {
            session.chooseParking(destinationId)
            delay(ROUTE_SETTLE_MS)
            _previews.value = session.previewParking()
            _choosing.value = null
        }
    }

    fun startDemo() {
        val play = platform.play ?: return
        if (_started.compareAndSet(expect = false, update = true)) {
            scope.launch { play() }
        }
    }

    private companion object {
        const val PREVIEW_INTERVAL_MS = 15_000L
        const val ROUTE_SETTLE_MS = 600L
    }
}
