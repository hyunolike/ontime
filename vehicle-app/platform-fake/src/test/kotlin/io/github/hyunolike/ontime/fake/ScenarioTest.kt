package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.slack.SlackCalculator
import io.github.hyunolike.ontime.slack.SlackInput
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class ScenarioTest {
    private val scenario = ScenarioA.scenario
    private val deadline = Instant.parse("2026-12-03T06:55:00Z")

    private fun slackAt(offset: Duration, destinationId: String, walk: Duration): Int =
        SlackCalculator.calculate(
            SlackInput(
                deadline = deadline,
                now = scenario.start.plus(offset.toJavaDuration()),
                drive = scenario.route(destinationId)!!.remainingAt(offset),
                parking = 6.minutes,
                walk = walk,
                buffer = 3.minutes,
            ),
        ).displayMinutes

    @Test
    fun `키프레임 사이를 보간하고 범위 밖은 끝 값을 쓴다`() {
        val route = ScenarioRoute(
            ScenarioA.LONG_TERM,
            listOf(Keyframe(10.minutes, 30.minutes), Keyframe(20.minutes, 10.minutes)),
        )
        assertEquals(30.minutes, route.remainingAt(0.minutes))
        assertEquals(20.minutes, route.remainingAt(15.minutes))
        assertEquals(19.minutes + 50.seconds, route.remainingAt(15.minutes + 5.seconds))
        assertEquals(10.minutes, route.remainingAt(99.minutes))
    }

    @Test
    fun `시나리오 A는 정체 전 28분 정체 후 9분 변경 후 21분이다`() {
        assertEquals(28, slackAt(38.minutes, ScenarioA.LONG_TERM.id, walk = 8.minutes))
        assertEquals(28, slackAt(40.minutes, ScenarioA.LONG_TERM.id, walk = 8.minutes))
        assertEquals(9, slackAt(41.minutes, ScenarioA.LONG_TERM.id, walk = 8.minutes))
        assertEquals(9, slackAt(55.minutes, ScenarioA.LONG_TERM.id, walk = 8.minutes))
        assertEquals(21, slackAt(41.minutes, ScenarioA.SHORT_TERM.id, walk = 5.minutes))
        assertEquals(21, slackAt(70.minutes, ScenarioA.SHORT_TERM.id, walk = 5.minutes))
    }

    @Test
    fun `시작 전에는 주행 중이 아니고 값의 출처는 재생이다`() = runTest {
        val player = ScenarioPlayer(scenario)
        assertFalse(player.driving.first())
        val status = player.route.first()
        assertEquals(ScenarioA.LONG_TERM, status.destination)
        assertEquals(Source.REPLAY, status.remainingDrive.source)
        assertEquals(Instant.parse("2026-12-03T05:48:00Z"), player.now.first())
    }

    @Test
    fun `도착하면 재생이 멈추고 주행이 끝난다`() = runTest {
        val player = ScenarioPlayer(scenario)
        launch { player.run() }
        testScheduler.advanceTimeBy(1.seconds)
        assertTrue(player.driving.first())
        advanceUntilIdle()
        assertFalse(player.driving.first())
        assertEquals(Instant.parse("2026-12-03T06:29:00Z"), player.now.first()) // 79분 지점
    }

    @Test
    fun `목적지를 바꾸면 남은 주행 시간이 그 경로의 값이 된다`() = runTest {
        val player = ScenarioPlayer(scenario)
        assertEquals(13.minutes, player.estimateRemainingDrive(ScenarioA.SHORT_TERM))
        assertTrue(player.changeDestination(ScenarioA.SHORT_TERM))
        val status = player.route.first()
        assertEquals(ScenarioA.SHORT_TERM, status.destination)
        assertEquals(13.minutes, status.remainingDrive.value)
    }

    @Test
    fun `모르는 목적지는 추정도 변경도 하지 않는다`() = runTest {
        val player = ScenarioPlayer(scenario)
        val unknown = io.github.hyunolike.ontime.platform.Destination("gmp", "김포공항", 37.5587, 126.7945)
        assertNull(player.estimateRemainingDrive(unknown))
        assertFalse(player.changeDestination(unknown))
        assertEquals(ScenarioA.LONG_TERM, player.route.first().destination)
    }
}
