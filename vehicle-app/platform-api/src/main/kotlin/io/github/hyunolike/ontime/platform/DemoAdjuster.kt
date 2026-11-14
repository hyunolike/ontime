package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Source
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** 시연용 조정의 현재 상태. */
data class DemoState(val delay: Duration = Duration.ZERO, val virtualDriving: Boolean = false) {
    val active: Boolean get() = delay > Duration.ZERO || virtualDriving
}

/**
 * 실제 차량 입력 위에 시연용 조정을 얹는다.
 *
 * 에뮬레이터는 실제로 달리지 않아 정체도, 주행 상태도 만들 수 없다. 내비의 실제 남은 시간에
 * 정체 지연만 더하고 나머지(목적지 변경, 다른 주차장까지의 소요 시간)는 실제 SDK를 그대로 쓴다.
 * 조정이 들어간 값은 출처를 [Source.ADJUSTED]로 바꿔 화면에 표시되게 한다.
 */
class DemoAdjuster(private val baseNavi: NaviGateway, private val baseVehicle: VehicleGateway) {
    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state

    /** 정체를 만든다. 지연은 누적되고, 안내 중인 경로와 다른 주차장 모두에 같이 더해진다. */
    fun addDelay(delay: Duration) = _state.update { it.copy(delay = it.delay + delay) }

    fun clearDelay() = _state.update { it.copy(delay = Duration.ZERO) }

    /** 기어 값과 상관없이 주행 중으로 본다. Virtual Console로 기어를 바꿀 수 없을 때의 대비책이다. */
    fun setVirtualDriving(on: Boolean) = _state.update { it.copy(virtualDriving = on) }

    val navi: NaviGateway = object : NaviGateway {
        override val route: Flow<RouteStatus> = combine(baseNavi.route, _state) { status, demo ->
            if (demo.delay == Duration.ZERO) {
                status
            } else {
                val reading = status.remainingDrive
                status.copy(remainingDrive = reading.copy(value = reading.value + demo.delay, source = Source.ADJUSTED))
            }
        }

        override suspend fun estimateRemainingDrive(to: Destination): Duration? =
            baseNavi.estimateRemainingDrive(to)?.plus(_state.value.delay)

        override suspend fun changeDestination(to: Destination): Boolean = baseNavi.changeDestination(to)
    }

    val vehicle: VehicleGateway = object : VehicleGateway {
        override val driving: Flow<Boolean> =
            combine(baseVehicle.driving, _state) { driving, demo -> driving || demo.virtualDriving }
    }
}
