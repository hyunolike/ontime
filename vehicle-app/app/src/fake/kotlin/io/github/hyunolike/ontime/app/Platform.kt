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
                // 음성 인식이 없으므로 답은 화면의 버튼으로만 받는다. 누르지 않으면 지나간다.
                speech = ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds),
                road = drive,
                stories = drive,
                play = drive::run,
                // 위키미디어 공용의 CC0 사진. 실제 빌드에서는 한국관광공사 관광 데이터의 대표 사진으로 바꾼다.
                photos = mapOf(
                    ScenarioGyeongju.DAEREUNGWON.id to PlacePhoto(R.drawable.photo_daereungwon, "Bernard Gagnon, CC0"),
                    ScenarioGyeongju.CHEOMSEONGDAE.id to PlacePhoto(R.drawable.photo_cheomseongdae, "Dudva, CC0"),
                    ScenarioGyeongju.BULGUKSA.id to PlacePhoto(R.drawable.photo_bulguksa, "Bernard Gagnon, CC0"),
                ),
            ),
        )
    }
}
