package io.github.hyunolike.ontime.app

import android.content.Context
import io.github.hyunolike.ontime.fake.DocentPlayer
import io.github.hyunolike.ontime.fake.ScenarioA
import io.github.hyunolike.ontime.fake.ScenarioGyeongju
import io.github.hyunolike.ontime.fake.ScenarioPlayer
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.session.ParkingChoice
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 시나리오 A를 재생하는 구성. 주차와 도보 시간은 설명용 가정이다. */
object Platform {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): PlatformBundle {
        val player = ScenarioPlayer(ScenarioA.scenario)
        val drive = DocentPlayer(ScenarioGyeongju.scenario)
        return PlatformBundle(
            plan = TripPlan(
                title = "인천공항 제1터미널",
                deadline = Instant.parse("2026-12-03T06:55:00Z"), // 15:55 KST
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
            // 경주 드라이브: 거리와 해설은 예시 데이터다. 음성은 화면 자막으로 대신한다.
            docent = DocentBundle(
                title = "경주 드라이브",
                time = drive,
                navi = drive,
                vehicle = drive,
                speech = ScriptedSpeech(answer = true, answerDelay = 3.seconds, sayDelay = 3.5.seconds),
                road = drive,
                stories = drive,
                play = drive::run,
            ),
        )
    }
}
