package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Reading
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

data class Destination(val id: String, val name: String)

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
}

interface VehicleGateway {
    val driving: Flow<Boolean>
}

interface SpeechGateway {
    suspend fun say(text: String)

    /** [prompt]를 말하고 답을 한 번 듣는다. 애매하거나 답이 없으면 false. */
    suspend fun askYesNo(prompt: String): Boolean
}
