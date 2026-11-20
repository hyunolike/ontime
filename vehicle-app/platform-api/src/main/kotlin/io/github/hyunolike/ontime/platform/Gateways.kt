package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Reading
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

data class Destination(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
)

/** 지금 안내 중인 목적지와 거기까지 남은 주행 시간. 둘을 한 번에 내보내 서로 어긋나지 않게 한다. */
data class RouteStatus(val destination: Destination, val remainingDrive: Reading<Duration>)

interface TimeSource {
    val now: Flow<Instant>
}

interface NaviGateway {
    val route: Flow<RouteStatus>

    /** 목적지를 [to]로 바꿨을 때의 남은 주행 시간. 알 수 없으면 null. */
    suspend fun estimateRemainingDrive(to: Destination): Duration?

    /** 요청이 받아들여지면 true. 실제 변경은 [route]로 확인한다. */
    suspend fun changeDestination(to: Destination): Boolean

    /** 목적지는 그대로 두고 [to]를 경유지로 넣는다. 지원하지 않으면 false. */
    suspend fun addWaypoint(to: Destination): Boolean = false

    /** 넣어 둔 첫 경유지를 지운다. 지원하지 않으면 false. */
    suspend fun removeWaypoint(): Boolean = false
}

interface VehicleGateway {
    val driving: Flow<Boolean>

    /** 충전 상태. 지원하지 않는 차량 값이면 늘 충전하지 않는 것으로 둔다. */
    val charge: Flow<ChargeState> get() = flowOf(ChargeState.NONE)
}

/**
 * [charging]은 충전구가 열려 있으면 참이다. [batteryPercent]는 읽지 못하면 null.
 * [simulated]는 시연용으로 켠 값이면 참이다. 화면이 시험 값임을 밝히는 데 쓴다.
 */
data class ChargeState(val charging: Boolean, val batteryPercent: Int?, val simulated: Boolean) {
    companion object {
        val NONE = ChargeState(charging = false, batteryPercent = null, simulated = false)
    }
}

interface SpeechGateway {
    suspend fun say(text: String)

    /** [prompt]를 말하고 답을 한 번 듣는다. 애매하거나 답이 없으면 false. */
    suspend fun askYesNo(prompt: String): Boolean

    /**
     * [prompt]를 말하고 답을 한 번 듣는다. 애매하거나 답이 없으면 null.
     * 화면의 버튼과 함께 물을 때 쓴다. 말로 답하지 않았다고 해서 질문을 닫지 않기 위해서다.
     */
    suspend fun listenYesNo(prompt: String): Boolean? = askYesNo(prompt)
}

/** 기기 시계를 [period]마다 내보낸다. */
class SystemTimeSource(private val period: Duration = 1.seconds) : TimeSource {
    override val now: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(period)
        }
    }
}
