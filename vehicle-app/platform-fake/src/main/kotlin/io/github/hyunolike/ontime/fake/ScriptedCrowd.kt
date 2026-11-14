package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.CrowdForecast
import io.github.hyunolike.ontime.platform.CrowdGateway
import io.github.hyunolike.ontime.platform.CrowdLevel
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 장소와 시(時)별로 적어 둔 혼잡도를 돌려준다. 예시 데이터이므로 출처는 항상 REPLAY다.
 * 단계는 혼잡도 구간으로 정한다: 40 미만 여유, 60 미만 보통, 80 미만 붐빔, 그 이상 매우 붐빔.
 */
class ScriptedCrowd(
    private val table: Map<String, Map<Int, Int>>,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) : CrowdGateway {
    override suspend fun forecast(placeId: String, at: Instant): CrowdForecast? {
        val percent = table[placeId]?.get(at.atZone(zone).hour) ?: return null
        return CrowdForecast(levelFor(percent), percent, at, Source.REPLAY)
    }

    override suspend fun hourly(placeId: String, from: Instant, hours: Int): List<CrowdForecast> {
        val start = from.truncatedTo(ChronoUnit.HOURS)
        return (0 until hours).mapNotNull { forecast(placeId, start.plus(it.toLong(), ChronoUnit.HOURS)) }
    }

    companion object {
        fun levelFor(percent: Int): CrowdLevel = when {
            percent < 40 -> CrowdLevel.RELAXED
            percent < 60 -> CrowdLevel.NORMAL
            percent < 80 -> CrowdLevel.BUSY
            else -> CrowdLevel.PACKED
        }
    }
}
