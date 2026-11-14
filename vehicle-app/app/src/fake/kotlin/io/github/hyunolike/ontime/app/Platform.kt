package io.github.hyunolike.ontime.app

import android.content.Context
import io.github.hyunolike.ontime.fake.ScenarioA
import io.github.hyunolike.ontime.fake.ScenarioOuting
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.outing.OutingPlan
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.session.AirportStep
import io.github.hyunolike.ontime.session.ParkingChoice
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/** 시나리오 A를 재생하는 구성. 주차와 도보 시간은 설명용 가정이다. */
object Platform {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): PlatformBundle {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val outingPlayer = ScenarioPlayer(ScenarioOuting.scenario)
        return PlatformBundle(
            plan = TripPlan(
                title = "인천공항 제1터미널",
                deadline = Instant.parse("2026-12-03T07:25:00Z"), // 탑승 마감 16:25 KST (수속 마감 15:55 + 공항 안 30분)
                // 공항 안 단계는 예시 값이다. 본선 전까지 인천공항 승객예고와 출국장 혼잡도, 운항 현황으로 바꾼다.
                deadlineLabel = "탑승 마감",
                airportSteps = listOf(
                    AirportStep("수속 카운터", 8.minutes),
                    AirportStep("출국장 대기", 14.minutes),
                    AirportStep("탑승구 이동", 8.minutes),
                ),
                parkingChoices = listOf(
                    ParkingChoice(ScenarioA.LONG_TERM, parking = 6.minutes, walk = 8.minutes),
                    ParkingChoice(ScenarioA.SHORT_TERM, parking = 6.minutes, walk = 5.minutes),
                ),
            ),
            departureLabel = "16:40 출발",
            time = player,
            navi = player,
            vehicle = player,
            speech = ScriptedSpeech(),
            play = player::run,
            // 주말 나들이: 혼잡 예보는 예시 값이다. 한적 서비스의 관광지 집중률 데이터가 들어올 자리다.
            outing = OutingBundle(
                plan = OutingPlan(
                    title = "토요일 나들이",
                    target = ScenarioOuting.GRAND_PARK,
                    alternatives = listOf(ScenarioOuting.ARBORETUM, ScenarioOuting.LAKE_PARK),
                ),
                time = outingPlayer,
                navi = outingPlayer,
                vehicle = outingPlayer,
                speech = ScriptedSpeech(),
                crowd = ScenarioOuting.crowd,
                play = outingPlayer::run,
            ),
        )
    }
}
