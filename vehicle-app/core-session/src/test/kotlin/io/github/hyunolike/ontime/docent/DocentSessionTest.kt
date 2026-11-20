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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DocentSessionTest {
    private val daereungwonStory = "5분 뒤 오른쪽에 대릉원. ${ScenarioGyeongju.daereungwon.story}"
    private val cheomseongdaeStory = "4분 뒤 왼쪽에 첨성대. ${ScenarioGyeongju.cheomseongdae.story}"

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

    /** 말로 답하지 않거나 알아듣지 못하는 음성. 답이 없으면 null을 준다. */
    private class SilentVoice : io.github.hyunolike.ontime.platform.SpeechGateway {
        override suspend fun say(text: String) = kotlinx.coroutines.delay(1.seconds)
        override suspend fun askYesNo(prompt: String): Boolean = listenYesNo(prompt) == true
        override suspend fun listenYesNo(prompt: String): Boolean? {
            say(prompt)
            kotlinx.coroutines.delay(3.seconds)
            return null
        }
    }

    @Test
    fun `음성 답이 없으면 질문을 닫지 않고 버튼 답을 기다리고 정한 시간이 지나면 지나간다`() = runTest {
        for (press in listOf(true, false)) {
            val player = DocentPlayer(ScenarioGyeongju.scenario)
            val session = DocentSession(player, player, player, SilentVoice(), player, player, askWindow = 7.seconds)
            val states = mutableListOf<DocentState>()
            val runJob = launch { session.run() }
            val collectJob = launch { session.state.collect { it?.let(states::add) } }
            val driveJob = launch { player.run() }
            while (states.lastOrNull()?.narration?.phase != NarrationPhase.ASKING) advanceTimeBy(200)
            // 음성이 답 없이 끝난(1초 + 3초) 뒤에도 질문은 열려 있다. 대릉원 앞 우회전까지는 약 10초 남아 있다.
            advanceTimeBy(5_000)
            assertEquals(NarrationPhase.ASKING, states.last().narration?.phase)
            if (press) {
                session.answer(true)
                runCurrent()
                assertEquals(ScenarioGyeongju.DAEREUNGWON, states.last().waypoint)
            } else {
                // 7초가 지나면 들르지 않고 질문을 닫는다.
                advanceTimeBy(2_500)
                assertEquals(null, states.last().narration)
                assertEquals(null, states.last().waypoint)
            }
            listOf(runJob, collectJob, driveJob).forEach { it.cancel() }
        }
    }

    @Test
    fun `좌석 안내를 켜면 오른쪽 장소에만 조수석 쪽이라고 덧붙인다`() = runTest {
        val speech = ScriptedSpeech(answer = false, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val player = DocentPlayer(ScenarioGyeongju.scenario)
        val session = DocentSession(player, player, player, speech, player, player, seatHint = true)
        backgroundScope.launch { session.run() }
        launch { player.run() }
        advanceUntilIdle()
        val stories = speech.spoken.value.filter { it.contains("뒤") }
        assertTrue(stories.first { it.contains("대릉원") }.startsWith("5분 뒤 오른쪽, 조수석 쪽에 대릉원."))
        assertTrue(stories.first { it.contains("첨성대") }.startsWith("4분 뒤 왼쪽에 첨성대."))
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

    @Test
    fun `묻는 동안 화면에서 지나가기를 누르면 음성 답을 기다리지 않는다`() = runTest {
        val speech = ScriptedSpeech(answer = true, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val player = DocentPlayer(ScenarioGyeongju.scenario)
        val session = DocentSession(player, player, player, speech, player, player)
        backgroundScope.launch { session.run() }
        // 묻기 시작하면 곧바로 화면에서 거절한다.
        backgroundScope.launch { session.state.collect { if (it?.narration?.phase == NarrationPhase.ASKING) session.answer(false) } }
        launch { player.run() }
        advanceUntilIdle()
        runCurrent()

        val last = session.state.value!!
        assertNull(last.waypoint)
        assertTrue(last.stamps.isEmpty())
    }

    @Test
    fun `출발 전에 여기로 안내를 고르면 묻지 않고 그곳에 들른다`() = runTest {
        val speech = ScriptedSpeech(answer = false, answerDelay = 4.seconds, sayDelay = 3.5.seconds)
        val player = DocentPlayer(ScenarioGyeongju.scenario)
        val session = DocentSession(player, player, player, speech, player, player)
        backgroundScope.launch { session.run() }
        runCurrent()

        assertTrue(session.guideTo(ScenarioGyeongju.CHEOMSEONGDAE.id))
        // 들를 곳은 한 번에 하나다.
        assertEquals(false, session.guideTo(ScenarioGyeongju.DAEREUNGWON.id))
        launch { player.run() }
        advanceUntilIdle()
        runCurrent()

        val last = session.state.value!!
        assertEquals(listOf(ScenarioGyeongju.CHEOMSEONGDAE), last.stamps)
        assertNull(last.heading)
        assertEquals(0, speech.spoken.value.count { it == "들러 볼까요?" })
    }

    @Test
    fun `묻는 중에 회전이 가까워지면 질문을 닫고 경유지를 넣지 않는다`() = runTest {
        // 답이 아주 늦게 오는 경우. 대릉원을 묻는 동안 우회전 400m 앞에 들어선다.
        val speech = ScriptedSpeech(answer = true, answerDelay = 20.seconds, sayDelay = 3.5.seconds)
        val (states, _) = drive(speech)

        // 물었던 것은 맞고, 회전 구간에 들어선 뒤로는 묻는 상태가 남아 있지 않다.
        assertTrue(states.any { it.narration?.phase == NarrationPhase.ASKING && it.narration?.ahead?.poi == ScenarioGyeongju.daereungwon })
        assertTrue(states.filter { it.quiet != null }.drop(1).none { it.narration?.phase == NarrationPhase.ASKING })
        assertTrue(states.none { it.waypoint == ScenarioGyeongju.DAEREUNGWON })
        assertTrue(speech.spoken.value.none { it.startsWith("대릉원을 경유지로") })
    }
}
