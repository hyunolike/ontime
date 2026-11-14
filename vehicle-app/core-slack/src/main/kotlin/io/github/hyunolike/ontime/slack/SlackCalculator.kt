package io.github.hyunolike.ontime.slack

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/**
 * 여유 계산의 입력.
 *
 * [drive]는 현재 위치에서 주차장 입구까지 남은 주행 시간이다.
 * 주차장 안 이동은 [parking]에 넣어 중복으로 빼지 않는다.
 */
data class SlackInput(
    val deadline: Instant,
    val now: Instant,
    val drive: Duration,
    val parking: Duration,
    val walk: Duration,
    /** 터미널에 들어선 뒤 마감 지점까지 걸리는 시간. 수속 카운터, 출국장(보안검색) 대기, 탑승구까지의 이동. */
    val airport: Duration = Duration.ZERO,
    val stopover: Duration = Duration.ZERO,
    val buffer: Duration,
)

data class SlackResult(val exact: Duration) {
    /** 화면에 보여 주는 분. 실제보다 낙관적으로 보이지 않게 내림한다. */
    val displayMinutes: Int = Math.floorDiv(exact.inWholeSeconds, 60L).toInt()
}

object SlackCalculator {
    /** 여유 = 마감 − 현재 − 주행 − 주차 − 도보 − 공항 안 − 경유지 정차 − 버퍼 */
    fun calculate(input: SlackInput): SlackResult {
        val untilDeadline = java.time.Duration.between(input.now, input.deadline).toKotlinDuration()
        return SlackResult(
            untilDeadline - input.drive - input.parking - input.walk - input.airport - input.stopover - input.buffer,
        )
    }
}
