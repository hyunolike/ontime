package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Source
import java.time.Instant

/** 장소의 혼잡 단계. 뒤로 갈수록 붐빈다. */
enum class CrowdLevel { RELAXED, NORMAL, BUSY, PACKED }

/**
 * 어느 시각에 그 장소가 얼마나 붐빌지에 대한 예보.
 * [percent]는 혼잡도(0~100)이고, [source]가 REPLAY면 실제 데이터가 아닌 예시 값이다.
 */
data class CrowdForecast(val level: CrowdLevel, val percent: Int, val at: Instant, val source: Source)

/** 장소의 시간대별 혼잡 예보. 한적 서비스의 관광지 집중률과 서울 실시간 도시데이터가 들어올 자리다. */
interface CrowdGateway {
    /** [at] 시각의 예보. 그 장소나 시간대의 데이터가 없으면 null. */
    suspend fun forecast(placeId: String, at: Instant): CrowdForecast?

    /** [from]부터 [hours]시간 동안의 시간대별 예보. 없는 시간대는 건너뛴다. */
    suspend fun hourly(placeId: String, from: Instant, hours: Int): List<CrowdForecast>
}
