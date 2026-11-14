package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.fake.ScenarioA
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.platform.DemoAdjuster
import io.github.hyunolike.ontime.slack.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DemoAdjusterTest {
    @Test
    fun `조정이 없으면 실제 값을 그대로 내보낸다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val demo = DemoAdjuster(player, player)
        val base = player.route.first()

        assertEquals(base, demo.navi.route.first())
        assertFalse(demo.vehicle.driving.first())
        assertFalse(demo.state.value.active)
    }

    @Test
    fun `정체를 더하면 남은 시간과 다른 주차장 소요 시간이 함께 늘고 출처가 조정으로 바뀐다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val demo = DemoAdjuster(player, player)
        val base = player.route.first().remainingDrive.value
        val other = player.estimateRemainingDrive(ScenarioA.SHORT_TERM)!!

        demo.addDelay(10.minutes)
        demo.addDelay(8.minutes)

        val adjusted = demo.navi.route.first().remainingDrive
        assertEquals(base + 18.minutes, adjusted.value)
        assertEquals(Source.ADJUSTED, adjusted.source)
        assertEquals(other + 18.minutes, demo.navi.estimateRemainingDrive(ScenarioA.SHORT_TERM))

        demo.clearDelay()
        assertEquals(base, demo.navi.route.first().remainingDrive.value)
    }

    @Test
    fun `가상 주행을 켜면 기어와 상관없이 주행 중이다`() = runTest {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val demo = DemoAdjuster(player, player)

        demo.setVirtualDriving(true)

        assertTrue(demo.vehicle.driving.first())
        assertTrue(demo.state.value.active)
    }
}
