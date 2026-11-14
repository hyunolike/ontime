package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.Destination
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 주말 나들이 시나리오: 토요일 낮 서울대공원으로 가는 길에 정체를 만나 도착이 14시를 넘기고,
 * 그 시간대의 혼잡 예보가 "붐빔"이라 덜 붐비는 곳을 제안받는다.
 *
 * 주행 시간과 혼잡도는 모두 설명용 예시 값이다. 좌표는 지도에서 읽은 대략값이다.
 */
object ScenarioOuting {
    val GRAND_PARK = Destination("seoul-grand-park", "서울대공원", 37.4275, 127.0170, "경기 과천시 대공원광장로 102")
    val ARBORETUM = Destination("mulhyanggi-arboretum", "물향기수목원", 37.1669, 127.0560, "경기 오산시 청학로 211")
    val LAKE_PARK = Destination("yuldong-park", "율동공원", 37.3795, 127.1487, "경기 성남시 분당구 문정로 145")

    val scenario = Scenario(
        start = Instant.parse("2026-12-05T03:40:00Z"), // 토요일 12:40 KST 출발
        playFrom = 0.minutes,
        routes = listOf(
            ScenarioRoute(
                GRAND_PARK,
                listOf(
                    Keyframe(0.minutes, 65.minutes), // 13:45 도착 예정
                    Keyframe(2.minutes, 63.minutes),
                    Keyframe(2.minutes + 5.seconds, 81.minutes + 55.seconds), // 정체로 14:04 도착 예정
                    Keyframe(84.minutes, 0.minutes),
                ),
            ),
            ScenarioRoute(
                ARBORETUM,
                listOf(
                    Keyframe(0.minutes, 72.minutes),
                    Keyframe(72.minutes, 0.minutes), // 정체 구간을 지나지 않는다. 13:52 도착
                ),
            ),
            ScenarioRoute(
                LAKE_PARK,
                listOf(
                    Keyframe(0.minutes, 38.minutes),
                    Keyframe(38.minutes, 0.minutes),
                ),
            ),
        ),
        initialDestinationId = GRAND_PARK.id,
    )

    /** 시(時)별 혼잡도 예시. 서울대공원은 14시부터 붐비고, 물향기수목원은 내내 여유롭다. */
    val crowd = ScriptedCrowd(
        mapOf(
            GRAND_PARK.id to mapOf(11 to 38, 12 to 46, 13 to 57, 14 to 78, 15 to 84, 16 to 71, 17 to 52),
            ARBORETUM.id to mapOf(11 to 18, 12 to 24, 13 to 31, 14 to 36, 15 to 33, 16 to 27, 17 to 20),
            LAKE_PARK.id to mapOf(11 to 41, 12 to 49, 13 to 55, 14 to 58, 15 to 61, 16 to 54, 17 to 45),
        ),
    )
}
