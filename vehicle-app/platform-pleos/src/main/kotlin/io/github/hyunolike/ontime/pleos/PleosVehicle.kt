package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import io.github.hyunolike.ontime.platform.VehicleGateway
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 기어 상태로 주행 여부를 판단한다. P일 때만 정차로 본다.
 * 기어를 읽지 못하면 주행 중으로 둔다. 모를 때는 화면을 덜 보여 주는 쪽이 안전하다.
 */
class PleosVehicle(context: Context, private val scope: CoroutineScope) : VehicleGateway {
    private val vehicle = Vehicle(context)
    private val _driving = MutableStateFlow(true)

    override val driving: Flow<Boolean> = _driving

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            PleosLog.event("onSelected", gear?.name.orEmpty())
            _driving.value = gear != VehicleGear.GEAR_PARK
        }

        override fun onFailed(e: Exception) {
            PleosLog.error("GearSelectionListener", e)
        }
    }

    fun start() {
        PleosLog.call("Vehicle.initialize")
        vehicle.initialize()
        val gear = vehicle.getGear()
        scope.launch {
            // 서비스 연결이 끝나기 전에 부르면 실패할 수 있어 몇 번 다시 시도한다.
            repeat(GEAR_ATTEMPTS) {
                var failed = false
                gear.getCurrentGearSelectionState(
                    { current ->
                        PleosLog.event("getCurrentGearSelectionState", current?.name.orEmpty())
                        _driving.value = current != VehicleGear.GEAR_PARK
                    },
                    { cause ->
                        failed = true
                        PleosLog.error("getCurrentGearSelectionState", cause)
                    },
                )
                delay(1.seconds)
                if (!failed) return@launch
            }
        }
        gear.registerGearSelection(listener)
    }

    fun stop() {
        vehicle.getGear().unregisterGearSelection(listener)
        vehicle.release()
    }

    private companion object {
        const val GEAR_ATTEMPTS = 5
    }
}
