package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.fake.ScenarioA
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.SlackLevel
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OnTimeSessionTest {
    private val long = ParkingChoice(ScenarioA.LONG_TERM, parking = 6.minutes, walk = 8.minutes)
    private val short = ParkingChoice(ScenarioA.SHORT_TERM, parking = 6.minutes, walk = 5.minutes)
    private val deadline = Instant.parse("2026-12-03T06:55:00Z")
    private val plan = TripPlan("16:40 출발", deadline, parkingChoices = listOf(long, short))

    /** 시나리오 A를 끝까지 돌리고 지나간 상태를 모두 돌려준다. */
    private fun TestScope.playScenario(speech: ScriptedSpeech, plan: TripPlan = this@OnTimeSessionTest.plan): List<SessionState> {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val session = OnTimeSession(plan, player, player, player, speech)
        val states = mutableListOf<SessionState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        launch { player.run() }
        advanceUntilIdle()
        runCurrent() // 백그라운드에서 도는 세션이 마지막 입력까지 처리하게 한다
        return states
    }

    @Test
    fun `정체를 만나면 한 번 제안하고 수락하면 단기주차장으로 바꿔 여유가 21분이 된다`() = runTest {
        val speech = ScriptedSpeech(answer = true)
        val states = playScenario(speech)

        assertEquals(28, states.first().slack.displayMinutes)
        assertEquals(SlackLevel.COMFORT, states.first().level)
        assertTrue(states.any { it.slack.displayMinutes == 9 && it.level == SlackLevel.CAUTION })
        assertEquals(
            listOf(
                "단기주차장으로 바꾸면 12분을 아낄 수 있습니다",
                "목적지를 단기주차장으로 바꿨어요. 여유 +21분",
            ),
            speech.spoken.value,
        )
        val last = states.last()
        assertEquals("단기주차장", last.destinationName)
        assertEquals(21, last.slack.displayMinutes)
        assertEquals(SlackLevel.COMFORT, last.level)
        assertEquals(SuggestionStatus.ACCEPTED, last.suggestion?.status)
        assertEquals(false, last.driving)
        assertEquals(Source.REPLAY, last.source)
    }

    @Test
    fun `거절하면 경로를 유지하고 다시 제안하지 않는다`() = runTest {
        val speech = ScriptedSpeech(answer = false)
        val states = playScenario(speech)

        assertEquals(listOf("단기주차장으로 바꾸면 12분을 아낄 수 있습니다"), speech.spoken.value)
        assertEquals("장기주차장", states.last().destinationName)
        assertEquals(9, states.last().slack.displayMinutes)
        assertEquals(SuggestionStatus.DECLINED, states.last().suggestion?.status)
    }

    @Test
    fun `바꿀 주차장이 없으면 제안하지 않는다`() = runTest {
        val speech = ScriptedSpeech()
        val states = playScenario(speech, plan.copy(parkingChoices = listOf(long)))

        assertTrue(speech.spoken.value.isEmpty())
        assertNull(states.last().suggestion)
        assertEquals(SlackLevel.CAUTION, states.last().level)
    }

    @Test
    fun `주행 시간 입력이 오래되면 오래됨으로 표시하고 제안하지 않는다`() = runTest {
        val clock = MutableStateFlow(Instant.parse("2026-12-03T05:50:00Z"))
        val receivedAt = Instant.parse("2026-12-03T05:45:00Z")
        val drive = MutableStateFlow(20.minutes)
        val navi = object : NaviGateway {
            override val route: Flow<RouteStatus> = MutableStateFlow(Unit).let {
                kotlinx.coroutines.flow.combine(drive, it) { d, _ ->
                    RouteStatus(ScenarioA.LONG_TERM, Reading(d, receivedAt, Source.LIVE))
                }
            }
            override suspend fun estimateRemainingDrive(to: Destination): Duration? = 5.minutes
            override suspend fun changeDestination(to: Destination): Boolean = true
        }
        val speech = ScriptedSpeech()
        val session = OnTimeSession(
            plan,
            object : TimeSource { override val now: Flow<Instant> = clock },
            navi,
            object : VehicleGateway { override val driving: Flow<Boolean> = flowOf(true) },
            speech,
        )
        backgroundScope.launch { session.run() }
        runCurrent()
        assertEquals(true, session.state.value?.stale)

        drive.value = 45.minutes // 여유가 주의 아래로 떨어진다
        runCurrent()
        assertEquals(SlackLevel.DANGER, session.state.value?.level)
        assertTrue(speech.spoken.value.isEmpty())
    }

    @Test
    fun `안내를 시작하면 첫 번째 주차장으로 경로를 요청하고 말로 알린다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        player.changeDestination(ScenarioA.SHORT_TERM)
        val speech = ScriptedSpeech()
        val session = OnTimeSession(plan, player, player, player, speech)

        assertTrue(session.startGuidance())

        assertEquals(ScenarioA.LONG_TERM, player.route.first().destination)
        assertEquals(listOf("장기주차장으로 안내를 시작합니다"), speech.spoken.value)
    }

    @Test
    fun `주차장마다 예상 여유를 비교해 보여 준다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val session = OnTimeSession(plan, player, player, player, ScriptedSpeech())
        assertTrue(session.previewParking().isEmpty()) // 입력이 모이기 전
        backgroundScope.launch { session.run() }
        runCurrent()

        val previews = session.previewParking()

        assertEquals(listOf("장기주차장" to 28, "단기주차장" to 40), previews.map { it.destination.name to it.slack.displayMinutes })
        assertEquals(listOf(true, false), previews.map { it.current })
    }

    @Test
    fun `정차 중 직접 고른 주차장으로 목적지를 바꾼다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val speech = ScriptedSpeech()
        val session = OnTimeSession(plan, player, player, player, speech)
        backgroundScope.launch { session.run() }
        runCurrent()

        assertTrue(session.chooseParking(ScenarioA.SHORT_TERM.id))
        runCurrent()

        assertEquals("단기주차장", session.state.value?.destinationName)
        assertEquals(listOf("목적지를 단기주차장으로 바꿨어요"), speech.spoken.value)
        assertEquals(false, session.chooseParking("unknown"))
    }

    @Test
    fun `제안에는 지금 여유와 바꾼 뒤 예상 여유가 담긴다`() = runTest {
        val states = playScenario(ScriptedSpeech(answer = true))
        val proposed = states.first { it.suggestion?.status == SuggestionStatus.PROPOSED }.suggestion!!
        // 제안에 적힌 숫자는 그 순간 화면의 숫자와 같아야 한다.
        assertEquals(9, proposed.currentMinutes)
        assertEquals(21, proposed.expectedMinutes)
    }

    @Test
    fun `음성을 못 알아들어도 화면 버튼으로 수락하면 목적지를 바꾼다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val speech = ScriptedSpeech(answer = false, answerDelay = 1.seconds)
        val session = OnTimeSession(plan, player, player, player, speech)
        backgroundScope.launch { session.run() }
        backgroundScope.launch {
            // 제안이 나오고 음성이 실패한 뒤에 버튼을 누른다.
            session.state.first { it?.suggestion?.status == SuggestionStatus.PROPOSED }
            delay(3.seconds)
            session.answerProposal(true)
        }
        launch { player.run() }
        advanceUntilIdle()
        runCurrent()

        assertEquals("단기주차장", session.state.value?.destinationName)
        assertEquals(SuggestionStatus.ACCEPTED, session.state.value?.suggestion?.status)
    }

    @Test
    fun `기다리는 제안이 없을 때 버튼 답은 무시한다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val session = OnTimeSession(plan, player, player, player, ScriptedSpeech())
        session.answerProposal(true)
        assertEquals(ScenarioA.LONG_TERM, player.route.first().destination)
    }

    @Test
    fun `받침에 따라 조사를 고른다`() {
        assertEquals("단기주차장으로", "단기주차장".withEuro())
        assertEquals("제2터미널로", "제2터미널".withEuro())
        assertEquals("휴게소로", "휴게소".withEuro())
        assertEquals("-2분", signedMinutes(-2))
    }
}
