package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.ReplayVehicle
import io.github.hyunolike.ontime.fake.track.TrackPlayer
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 실제 시연 경로(판교 → 한성백제박물관)를 처음부터 끝까지 돌린다. pleos 빌드와 같은 부품 조합이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class SongpaDriveTest {
    /** 호출만 기록하는 내비. 초기화에 실패해 경로 값을 한 번도 주지 않는 내비와 같다. */
    private class RecordingNavi : NaviGateway {
        val calls = mutableListOf<String>()
        private val routes = kotlinx.coroutines.flow.MutableSharedFlow<RouteStatus>(replay = 1)
        override val route: Flow<RouteStatus> = routes

        fun confirmNow(destination: Destination) {
            routes.tryEmit(RouteStatus(destination, io.github.hyunolike.ontime.slack.Reading(Duration.ZERO, Instant.now(), Source.LIVE)))
        }
        override suspend fun estimateRemainingDrive(to: Destination): Duration? = null
        override suspend fun changeDestination(to: Destination): Boolean { calls += "route:${to.id}"; return true }
        override suspend fun addWaypoint(to: Destination): Boolean { calls += "add:${to.id}"; return true }
        override suspend fun removeWaypoint(): Boolean { calls += "remove"; return true }
    }

    /** "들러 볼까요?"에 정해 둔 장소에서만 예라고 답한다. 화면 자막 시간은 pleos 빌드와 같다. */
    private class AnswerAt(private val yesFor: String?, private val spokenLog: MutableList<String>) : SpeechGateway {
        override suspend fun say(text: String) {
            spokenLog += text
            delay(3.5.seconds)
        }

        override suspend fun askYesNo(prompt: String): Boolean {
            say(prompt)
            // 들를 곳이면 발표자가 곧바로 누른다. 아니면 10초 동안 기다리다 지나간다.
            val yes = yesFor != null && spokenLog.dropLast(1).lastOrNull()?.contains(yesFor) == true
            delay(if (yes) 2.seconds else 10.seconds)
            return yes
        }
    }

    private class Run(
        val states: List<DocentState>,
        val navi: ReplayBackedNavi,
        val primary: RecordingNavi,
        val player: TrackPlayer,
        val session: DocentSession,
        val vehicle: ReplayVehicle,
        val tracker: AheadTracker,
    )

    private fun TestScope.start(speech: SpeechGateway, memory: DocentMemory? = null): Run {
        val player = TrackPlayer(ScenarioSongpa.track, Instant.parse("2026-12-05T01:00:00Z"))
        val primary = RecordingNavi()
        val navi = ReplayBackedNavi(primary, player)
        val tracker = AheadTracker(ScenarioSongpa.pois, passedOnRoute = { player.hasPassed(it.destination) })
        val stories = LiveStories(player, tracker, player.nextTurn)
        val vehicle = ReplayVehicle(player)
        val session = DocentSession(
            player, navi, vehicle, speech, player, stories,
            quietWithin = 600, walkFrom = player, catalog = { ScenarioSongpa.pois }, memory = memory,
        )
        val states = mutableListOf<DocentState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        runCurrent()
        return Run(states, navi, primary, player, session, vehicle, tracker)
    }

    private fun TestScope.driveToNextStop(run: Run) {
        launch { run.navi.drive() }
        advanceUntilIdle()
        runCurrent()
    }

    @Test
    fun `내비 값이 없어도 첫 상태가 곧바로 나오고 출발 전에 네 곳이 모두 보인다`() = runTest {
        val run = start(ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds))
        val first = run.states.last()
        assertFalse(first.driving)
        assertEquals(Source.REPLAY, first.source)
        assertEquals(ScenarioSongpa.HANSEONG, first.destination)
        assertEquals(
            listOf(ScenarioSongpa.SEOUL_AIRBASE, ScenarioSongpa.SEOKCHON, ScenarioSongpa.BANGI, ScenarioSongpa.MONGCHON),
            first.ahead.map { it.poi.destination },
        )
    }

    @Test
    fun `들르지 않으면 네 곳을 차례로 모두 들려주고 목적지에서 남은 시간이 0이 된다`() = runTest {
        val spoken = mutableListOf<String>()
        val run = start(AnswerAt(yesFor = null, spokenLog = spoken))
        driveToNextStop(run)

        val last = run.states.last()
        assertEquals(
            listOf(ScenarioSongpa.SEOUL_AIRBASE, ScenarioSongpa.SEOKCHON, ScenarioSongpa.BANGI, ScenarioSongpa.MONGCHON),
            last.told.map { it.destination },
        )
        // 주행 중 언제든 아직 소개하지 않은 곳이 남아 있으면 목록에서 사라지지 않는다.
        // 화면이 "오늘 이야기는 모두 들었습니다"를 일찍 띄우지 않게 하기 위해서다.
        for (state in run.states.filter { it.driving }) {
            val untold = ScenarioSongpa.pois.filter { it !in state.told }
            val shown = state.ahead.map { it.poi }
            val missing = untold.filter { it !in shown }
            assertTrue(missing.isEmpty(), "남은 곳 ${missing.map { it.destination.name }}이 목록에 없음")
        }
        assertFalse(last.driving)
        assertEquals(Duration.ZERO, last.remaining)
        assertEquals(listOf("route:songpa-hanseong"), run.primary.calls)
        // 지날 때의 쪽과 같은 쪽으로 소개한다. 경로선에 수선을 내려 잰 값은 서울공항 오른쪽, 두 고분군 왼쪽이다.
        val sides = run.states.mapNotNull { it.narration?.ahead }.associate { it.poi.destination to it.side }
        assertEquals(Side.RIGHT, sides[ScenarioSongpa.SEOUL_AIRBASE])
        assertEquals(Side.LEFT, sides[ScenarioSongpa.SEOKCHON])
        assertEquals(Side.LEFT, sides[ScenarioSongpa.BANGI])
        assertTrue(spoken.first().contains("오른쪽에 서울공항."), spoken.first())
    }

    @Test
    fun `석촌동 고분군에 들르면 도장이 찍히고 다시 출발하면 내비의 경유지를 지우고 목적지까지 간다`() = runTest {
        visit(ScenarioSongpa.SEOKCHON, "석촌동")
    }

    @Test
    fun `방이동 고분군에 들르면 도장이 찍히고 다시 출발하면 내비의 경유지를 지우고 목적지까지 간다`() = runTest {
        visit(ScenarioSongpa.BANGI, "방이동")
    }

    private fun TestScope.visit(place: Destination, name: String) {
        val spoken = mutableListOf<String>()
        val run = start(AnswerAt(yesFor = name, spokenLog = spoken))
        driveToNextStop(run)

        val parked = run.states.last()
        assertFalse(parked.driving)
        assertEquals(listOf(place), parked.stamps)
        assertTrue(parked.ahead.any { it.poi.destination == place && it.distanceMeters == 0 })
        assertEquals(listOf("route:songpa-hanseong", "add:${place.id}"), run.primary.calls)

        driveToNextStop(run)
        val last = run.states.last()
        assertEquals(Duration.ZERO, last.remaining)
        assertEquals(listOf(place), last.stamps)
        assertEquals(listOf("route:songpa-hanseong", "add:${place.id}", "remove"), run.primary.calls)
        // 들른 뒤에도 남은 곳 이야기는 이어지고, 쪽은 지날 때의 쪽과 같다.
        assertTrue(last.told.map { it.destination }.containsAll(listOf(ScenarioSongpa.SEOKCHON, ScenarioSongpa.BANGI)), "told=${last.told.map { it.destination.name }} spoken=$spoken")
        assertSides(run)
    }

    @Test
    fun `출발 전에 석촌동 고분군을 경유지로 넣어도 방이동 고분군은 지날 때의 쪽으로 소개한다`() = runTest {
        val spoken = mutableListOf<String>()
        val run = start(AnswerAt(yesFor = null, spokenLog = spoken))
        // 내비가 이미 같은 목적지로 안내 중이라고 알려 둔다.
        run.primary.confirmNow(ScenarioSongpa.HANSEONG)
        val guided = async { run.session.guideTo(ScenarioSongpa.SEOKCHON.id) }
        runCurrent()
        assertTrue(guided.await())
        driveToNextStop(run)
        assertEquals(listOf(ScenarioSongpa.SEOKCHON), run.states.last().stamps)
        driveToNextStop(run)
        assertEquals(Duration.ZERO, run.states.last().remaining)
        assertSides(run)
    }

    @Test
    fun `이야기 도중에 차를 세우면 멈추고 다시 출발했을 때 처음부터 들려준다`() = runTest {
        val spoken = mutableListOf<String>()
        val run = start(AnswerAt(yesFor = null, spokenLog = spoken))
        run.primary.confirmNow(ScenarioSongpa.HANSEONG)
        val guided = async { run.session.guideTo(ScenarioSongpa.SEOKCHON.id) }
        runCurrent()
        assertTrue(guided.await())
        driveToNextStop(run)
        // 석촌동 고분군에 서 있는 동안에는 방이동 고분군 이야기를 하지 않는다.
        val parked = run.states.last()
        assertTrue(ScenarioSongpa.pois.single { it.destination == ScenarioSongpa.BANGI } !in parked.told)
        assertEquals(null, parked.narration)
        val stoppedAt = run.states.indexOfLast { it.driving }
        assertTrue(run.states.drop(stoppedAt + 1).none { it.narration != null }, "정차 중에 이야기가 이어짐")

        driveToNextStop(run)
        // 다시 출발하면 방이동 고분군을 들려주고 들를지도 묻는다.
        assertTrue(spoken.count { it.contains("방이동 고분군") } >= 1)
        assertTrue(run.states.any { it.narration?.ahead?.poi?.destination == ScenarioSongpa.BANGI && it.narration?.phase == NarrationPhase.ASKING && it.driving })
    }

    @Test
    fun `목적지에 서서 충전하면 걸어서 둘러볼 곳을 걷는 시간과 함께 보여 준다`() = runTest {
        val run = start(ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds))
        driveToNextStop(run)
        assertTrue(run.states.last().walkable.isEmpty(), "충전하지 않으면 목록이 없다")

        run.vehicle.simulateCharging(true)
        runCurrent()
        val charging = run.states.last()
        assertTrue(charging.charge.charging && charging.charge.simulated)
        assertEquals(listOf(ScenarioSongpa.MONGCHON, ScenarioSongpa.BANGI), charging.walkable.map { it.poi.destination })
        // 재생 경로는 박물관 좌표보다 약 75m 앞, 도로 위에서 끝난다. 그래서 박물관에서 잰 15분, 17분보다 1분씩 짧다.
        assertEquals(listOf(14, 16), charging.walkable.map { it.walkMinutes })

        run.vehicle.simulateCharging(false)
        runCurrent()
        assertTrue(run.states.last().walkable.isEmpty())
    }

    @Test
    fun `달리는 동안에는 충전 중이어도 걸어서 갈 곳을 보여 주지 않는다`() = runTest {
        val run = start(ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds))
        run.vehicle.simulateCharging(true)
        launch { run.navi.drive() }
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(run.states.filter { it.driving }.all { it.walkable.isEmpty() })
        advanceUntilIdle()
    }

    @Test
    fun `앱이 다시 시작되면 들은 곳과 도장을 이어 가고 같은 곳을 다시 소개하지 않는다`() = runTest {
        val spoken = mutableListOf<String>()
        val first = start(AnswerAt(yesFor = "석촌동", spokenLog = spoken))
        driveToNextStop(first)
        val saved = DocentMemory.decode(first.session.snapshot()!!.encode())!!
        assertEquals(listOf(ScenarioSongpa.SEOKCHON.id), saved.stampIds)

        spoken.clear()
        val again = start(AnswerAt(yesFor = null, spokenLog = spoken), memory = saved)
        val restored = again.states.last()
        assertEquals(listOf(ScenarioSongpa.SEOKCHON), restored.stamps)
        assertEquals(saved.toldIds, restored.told.map { it.destination.id })
        driveToNextStop(again)
        // 이미 들은 서울공항과 석촌동은 다시 말하지 않는다.
        assertTrue(spoken.none { it.contains("서울공항") || it.contains("석촌동") }, spoken.toString())
        assertTrue(spoken.any { it.contains("방이동") })
    }

    @Test
    fun `목적지가 다르면 저장한 상태를 쓰지 않는다`() = runTest {
        val run = start(ScriptedSpeech(answer = false), memory = DocentMemory("other", listOf(ScenarioSongpa.SEOUL_AIRBASE.id), emptyList()))
        assertTrue(run.states.last().told.isEmpty())
    }

    @Test
    fun `처음부터 다시 하면 경유지를 지우고 출발 전으로 돌아간다`() = runTest {
        val spoken = mutableListOf<String>()
        val run = start(AnswerAt(yesFor = "석촌동", spokenLog = spoken))
        driveToNextStop(run)
        assertTrue(run.player.atVisitedStop)
        run.navi.reset()
        run.tracker.reset()
        run.session.reset()
        runCurrent()
        val fresh = run.states.last()
        assertTrue(fresh.told.isEmpty() && fresh.stamps.isEmpty() && fresh.waypoint == null)
        assertFalse(fresh.driving)
        assertEquals("remove", run.primary.calls.last())
        // 다시 출발하면 경로를 다시 요청하고 서울공항부터 다시 들려준다.
        spoken.clear()
        driveToNextStop(run)
        assertEquals("route:songpa-hanseong", run.primary.calls[run.primary.calls.lastIndexOf("remove") + 1])
        assertTrue(spoken.first().contains("서울공항"))
    }

    private fun assertSides(run: Run) {
        val sides = run.states.mapNotNull { it.narration?.ahead }.associate { it.poi.destination to it.side }
        // 들른 곳 말고는 모두 실제로 소개돼야 한다. 소개되지 않으면 쪽을 확인할 것도 없다.
        val notVisited = ScenarioSongpa.pois.map { it.destination } - run.states.last().stamps.toSet()
        assertTrue(sides.keys.containsAll(notVisited), "소개되지 않은 곳: ${(notVisited - sides.keys).map { it.name }}")
        sides[ScenarioSongpa.SEOUL_AIRBASE]?.let { assertEquals(Side.RIGHT, it) }
        sides[ScenarioSongpa.SEOKCHON]?.let { assertEquals(Side.LEFT, it) }
        sides[ScenarioSongpa.BANGI]?.let { assertEquals(Side.LEFT, it, "방이동 고분군") }
        // 경로선에 수선을 내려 잰 쪽: 몽촌토성은 목적지 앞에서 왼쪽이다.
        sides[ScenarioSongpa.MONGCHON]?.let { assertEquals(Side.LEFT, it, "몽촌토성") }
    }

    @Test
    fun `출발 전에 여기로 안내를 누르면 경로 확인을 기다리다 20초가 지나면 넣지 않는다`() = runTest {
        // 이 내비는 경로 값을 주지 않는다. 실제 내비가 응답하지 않는 경우다.
        val run = start(ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds))
        val guided = async { run.session.guideTo(ScenarioSongpa.BANGI.id) }
        advanceUntilIdle()
        assertFalse(guided.await())
        assertEquals(listOf("route:songpa-hanseong"), run.primary.calls)
    }
}
