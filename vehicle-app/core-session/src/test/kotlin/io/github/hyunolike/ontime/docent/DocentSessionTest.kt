package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.DocentPlayer
import io.github.hyunolike.ontime.fake.ScenarioGyeongju
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DocentSessionTest {
    private val daereungwonStory =
        "5분 뒤 오른쪽에 대릉원. 신라의 왕과 귀족이 잠든 무덤 스물세 기가 모여 있습니다. 천마도가 나온 천마총이 이 안에 있습니다."
    private val cheomseongdaeStory =
        "4분 뒤 왼쪽에 첨성대. 선덕여왕 때 세운 천문 관측대입니다. 천삼백 년 넘게 같은 자리에 서 있습니다."

    private fun TestScope.drive(speech: ScriptedSpeech): Pair<List<DocentState>, DocentPlayer> {
        val player = DocentPlayer(ScenarioGyeongju.scenario)
        val session = DocentSession(player, player, player, speech, player, player)
        val states = mutableListOf<DocentState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        launch { player.run() }
        advanceUntilIdle()
        runCurrent()
        return states to player
    }

    @Test
    fun `곧 지나갈 곳을 몇 분 뒤 어느 쪽인지와 함께 미리 들려준다`() = runTest {
        val speech = ScriptedSpeech(answer = false, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val (states, _) = drive(speech)

        assertEquals(daereungwonStory, speech.spoken.value.first())
        val telling = states.first { it.narration?.phase == NarrationPhase.TELLING }
        assertEquals(Side.RIGHT, telling.narration!!.ahead.side)
        assertTrue(telling.narration!!.ahead.distanceMeters in 500..3000)
        assertEquals(Source.REPLAY, telling.source)
    }

    @Test
    fun `들르자고 답하면 경유지로 넣고 차를 세우면 방문 도장을 찍는다`() = runTest {
        val speech = ScriptedSpeech(answer = true, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val (states, _) = drive(speech)

        assertEquals(
            listOf(daereungwonStory, "들러 볼까요?", "대릉원을 경유지로 넣었어요", cheomseongdaeStory),
            speech.spoken.value,
        )
        val last = states.last()
        assertEquals(false, last.driving)
        assertEquals(ScenarioGyeongju.DAEREUNGWON, last.waypoint)
        assertEquals(listOf(ScenarioGyeongju.DAEREUNGWON), last.stamps)
        // 경유지를 이미 정했으므로 첨성대에서는 다시 묻지 않는다.
        assertEquals(1, speech.spoken.value.count { it == "들러 볼까요?" })
    }

    @Test
    fun `회전이 가까우면 이야기를 시작하지 않고 지난 뒤에 들려준다`() = runTest {
        val speech = ScriptedSpeech(answer = true, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val (states, _) = drive(speech)

        // 첨성대가 3km 안으로 들어온 순간은 회전 300m 전이라 말하지 않는다.
        val held = states.filter { it.quiet != null && it.upcoming.firstOrNull()?.poi == ScenarioGyeongju.cheomseongdae }
        assertTrue(held.isNotEmpty())
        // 앞 이야기를 마저 말하는 것은 괜찮지만, 새 이야기를 시작하지는 않는다.
        assertTrue(held.none { it.narration?.ahead?.poi == ScenarioGyeongju.cheomseongdae })
        assertTrue(held.any { it.upcoming.first().distanceMeters <= 3000 })
        // 회전을 지난 뒤에 시작했으므로 3km가 아니라 2.7km 앞에서 "4분 뒤"라고 말한다.
        val telling = states.first { it.narration?.ahead?.poi == ScenarioGyeongju.cheomseongdae }
        assertNull(telling.quiet)
        assertTrue(telling.narration!!.ahead.distanceMeters in 2650..2700)
    }

    @Test
    fun `들르지 않겠다고 하면 경유지 없이 목적지까지 가고 도장은 없다`() = runTest {
        val speech = ScriptedSpeech(answer = false, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val (states, _) = drive(speech)

        val last = states.last()
        assertNull(last.waypoint)
        assertTrue(last.stamps.isEmpty())
        assertEquals(0, last.remaining.inWholeSeconds)
        // 대릉원에서 거절했으므로 첨성대에서 다시 한 번 물을 수 있다.
        assertEquals(2, speech.spoken.value.count { it == "들러 볼까요?" })
    }
}
