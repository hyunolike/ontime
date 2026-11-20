package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.platform.ChargeState
import io.github.hyunolike.ontime.platform.VehicleGateway
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ReplayVehicleTest {
    private val player = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH)

    private class RealCar(charging: Boolean, percent: Int?) : VehicleGateway {
        val state = MutableStateFlow(ChargeState(charging, percent, simulated = false))
        override val driving: Flow<Boolean> = flowOf(false)
        override val charge: Flow<ChargeState> = state
    }

    @Test
    fun `실제 차량 값이 없으면 충전하지 않는다`() = runTest {
        assertEquals(ChargeState.NONE, ReplayVehicle(player).charge.first())
    }

    @Test
    fun `시험 값으로 켜면 충전 중이고 시험 값이라고 밝힌다`() = runTest {
        val vehicle = ReplayVehicle(player, RealCar(false, 71))
        vehicle.simulateCharging(true)
        assertEquals(ChargeState(true, 71, simulated = true), vehicle.charge.first())
    }

    @Test
    fun `실제 차량이 충전 중이면 실제 값을 쓴다`() = runTest {
        val vehicle = ReplayVehicle(player, RealCar(true, 58))
        vehicle.simulateCharging(true)
        assertEquals(ChargeState(true, 58, simulated = false), vehicle.charge.first())
    }
}
