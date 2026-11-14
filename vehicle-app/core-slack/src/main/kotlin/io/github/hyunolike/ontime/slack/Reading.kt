package io.github.hyunolike.ontime.slack

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/**
 * 값이 어디서 왔는지.
 * REPLAY는 시연용으로 재생한 값이고, ADJUSTED는 실제 값에 시연용 조정(예: 정체 +18분)을 더한 값이다.
 */
enum class Source { LIVE, CACHED, REPLAY, ADJUSTED }

/** 입력 값과 함께 받은 시각, 출처를 들고 다닌다. */
data class Reading<T>(val value: T, val receivedAt: Instant, val source: Source) {
    fun isStale(now: Instant, maxAge: Duration): Boolean =
        java.time.Duration.between(receivedAt, now).toKotlinDuration() > maxAge
}
