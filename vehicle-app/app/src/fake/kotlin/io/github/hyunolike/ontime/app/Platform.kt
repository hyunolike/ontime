package io.github.hyunolike.ontime.app

import android.content.Context
import io.github.hyunolike.ontime.fake.ScenarioA
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.session.ParkingChoice
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/** 시나리오 A를 재생하는 구성. 주차와 도보 시간은 설명용 가정이다. */
object Platform {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): PlatformBundle {
        val player = ScenarioPlayer(ScenarioA.scenario)
        return PlatformBundle(
            plan = TripPlan(
                title = "인천공항 제1터미널",
                deadline = Instant.parse("2026-12-03T06:55:00Z"), // 15:55 KST
                parkingChoices = listOf(
                    // 요금은 소형차 하루 최대 요금(원). 언론과 블로그에 실린 값이라 공식 안내로 다시 확인해야 한다.
                    ParkingChoice(ScenarioA.LONG_TERM, parking = 6.minutes, walk = 8.minutes, dailyFee = 9_000),
                    ParkingChoice(ScenarioA.SHORT_TERM, parking = 6.minutes, walk = 5.minutes, dailyFee = 24_000),
                ),
            ),
            departureLabel = "16:40 출발",
            time = player,
            navi = player,
            vehicle = player,
            speech = ScriptedSpeech(),
            play = player::run,
        )
    }
}
