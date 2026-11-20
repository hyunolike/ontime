package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.platform.ChargeState
import io.github.hyunolike.ontime.platform.VehicleGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 주행 여부는 재생기([player])를, 충전 상태는 실제 차량 값([real])을 따른다.
 * 에뮬레이터에서 충전구를 열 방법이 없을 때를 위해 시험 값([simulateCharging])으로 충전을 켤 수 있다.
 * 실제 차량이 충전 중이면 실제 값을 쓰고, 시험 값만 켜져 있으면 [ChargeState.simulated]를 참으로 둔다.
 */
class ReplayVehicle(
    private val player: TrackPlayer,
    private val real: VehicleGateway? = null,
    /** 시험 값으로 충전할 때 보여 줄 배터리 잔량. 실제 잔량을 읽었으면 그쪽을 쓴다. */
    private val simulatedPercent: Int = 64,
) : VehicleGateway {
    private val simulated = MutableStateFlow(false)

    override val driving: Flow<Boolean> = player.driving

    override val charge: Flow<ChargeState> =
        combine(real?.charge ?: kotlinx.coroutines.flow.flowOf(ChargeState.NONE), simulated) { actual, test ->
            when {
                actual.charging -> actual
                test -> ChargeState(charging = true, batteryPercent = actual.batteryPercent ?: simulatedPercent, simulated = true)
                else -> actual
            }
        }.distinctUntilChanged()

    fun simulateCharging(on: Boolean) {
        simulated.value = on
    }

    val simulatingCharge: Boolean get() = simulated.value
}
