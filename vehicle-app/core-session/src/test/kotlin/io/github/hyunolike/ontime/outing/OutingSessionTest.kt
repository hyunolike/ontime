package io.github.hyunolike.ontime.outing

import io.github.hyunolike.ontime.fake.ScenarioOuting
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.fake.ScriptedCrowd
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.platform.CrowdLevel
import io.github.hyunolike.ontime.session.SuggestionStatus
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OutingSessionTest {
    private val plan = OutingPlan(
        "토요일 나들이",
        ScenarioOuting.GRAND_PARK,
        listOf(ScenarioOuting.ARBORETUM, ScenarioOuting.LAKE_PARK),
    )

    private fun TestScope.play(
        speech: ScriptedSpeech,
        crowd: ScriptedCrowd = ScenarioOuting.crowd,
        plan: OutingPlan = this@OutingSessionTest.plan,
    ): List<OutingState> {
        val player = ScenarioPlayer(ScenarioOuting.scenario)
        val session = OutingSession(plan, player, player, player, speech, crowd)
        val states = mutableListOf<OutingState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        launch { player.run() }
        advanceUntilIdle()
        runCurrent()
        return states
    }

    @Test
    fun `예보는 지금이 아니라 도착할 시각을 기준으로 읽는다`() = runTest {
        val states = play(ScriptedSpeech(answer = false))
        val first = states.first()
        // 12:40에 출발해 13:45 도착 예정이면 13시대 예보(보통)를 본다. 12시대(46)가 아니다.
        assertEquals(Instant.parse("2026-12-05T04:45:00Z"), first.arrival)
        assertEquals(57, first.crowd.percent)
        assertEquals(CrowdLevel.NORMAL, first.crowd.level)
        assertEquals(Source.REPLAY, first.source)
    }

    @Test
    fun `정체로 도착이 붐비는 시간대로 밀리면 한적한 곳을 제안하고 수락하면 목적지를 바꾼다`() = runTest {
        val speech = ScriptedSpeech(answer = true)
        val states = play(speech)

        assertTrue(states.any { it.destination == ScenarioOuting.GRAND_PARK && it.crowd.level == CrowdLevel.BUSY })
        assertEquals(
            listOf(
                "도착할 때 서울대공원은 붐빌 것으로 보입니다. 물향기수목원은 여유롭고 12분 일찍 도착합니다",
                "목적지를 물향기수목원으로 바꿨어요. 도착 예보는 여유입니다",
            ),
            speech.spoken.value,
        )
        val last = states.last()
        assertEquals(ScenarioOuting.ARBORETUM, last.destination)
        assertEquals(CrowdLevel.RELAXED, last.crowd.level)
        assertEquals(SuggestionStatus.ACCEPTED, last.suggestion?.status)
        assertEquals("붐빔", last.suggestion?.currentLabel)
        assertEquals("여유", last.suggestion?.expectedLabel)
    }

    @Test
    fun `거절하면 목적지를 유지하고 다시 제안하지 않는다`() = runTest {
        val speech = ScriptedSpeech(answer = false)
        val states = play(speech)

        assertEquals(1, speech.spoken.value.size)
        assertEquals(ScenarioOuting.GRAND_PARK, states.last().destination)
        assertEquals(SuggestionStatus.DECLINED, states.last().suggestion?.status)
    }

    @Test
    fun `덜 붐비는 곳이 없으면 제안하지 않는다`() = runTest {
        val everywhereBusy = ScriptedCrowd(
            listOf(ScenarioOuting.GRAND_PARK, ScenarioOuting.ARBORETUM, ScenarioOuting.LAKE_PARK)
                .associate { it.id to (11..17).associateWith { 85 } },
        )
        val speech = ScriptedSpeech()
        val states = play(speech, everywhereBusy)

        assertTrue(speech.spoken.value.isEmpty())
        assertNull(states.last().suggestion)
    }

    @Test
    fun `비교 목록은 장소마다 도착 시각과 그 시각의 예보를 준다`() = runTest {
        val player = ScenarioPlayer(ScenarioOuting.scenario)
        val session = OutingSession(plan, player, player, player, ScriptedSpeech(), ScenarioOuting.crowd)
        assertTrue(session.compare().isEmpty())
        backgroundScope.launch { session.run() }
        runCurrent()

        val options = session.compare()

        assertEquals(listOf("서울대공원", "물향기수목원", "율동공원"), options.map { it.destination.name })
        assertEquals(listOf(57, 31, 55), options.map { it.crowd.percent }) // 13:45, 13:52, 13:18 도착
        assertEquals(listOf(true, false, false), options.map { it.current })
    }

    @Test
    fun `혼잡도 구간으로 단계를 정한다`() {
        assertEquals(CrowdLevel.RELAXED, ScriptedCrowd.levelFor(39))
        assertEquals(CrowdLevel.NORMAL, ScriptedCrowd.levelFor(40))
        assertEquals(CrowdLevel.BUSY, ScriptedCrowd.levelFor(60))
        assertEquals(CrowdLevel.PACKED, ScriptedCrowd.levelFor(80))
    }
}
