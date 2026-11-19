package io.github.hyunolike.ontime.app

import android.content.Context
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.SystemTimeSource
import io.github.hyunolike.ontime.pleos.PleosNavi
import io.github.hyunolike.ontime.pleos.PleosSpeech
import io.github.hyunolike.ontime.pleos.PleosVehicle
import io.github.hyunolike.ontime.session.ParkingChoice
import io.github.hyunolike.ontime.session.TripPlan
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import io.github.hyunolike.ontime.pleos.PleosLocation
import io.github.hyunolike.ontime.docent.AheadTracker
import io.github.hyunolike.ontime.docent.LiveStories
import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.TrackPlayer

/**
 * Pleos Connect SDK에 연결하는 구성.
 *
 * 앱은 도슨트(길벗)로 시작한다. 판교에서 한성백제박물관으로 가는 길에서 경로 요청과 경유지 추가, 해제는 실제 내비에
 * 보내고, 차의 위치와 남은 시간, 회전은 실제 도로 모양을 따라 재생한다. 에뮬레이터의 내비 차가 판교에 서 있고
 * 위치 주입도 받지 않기 때문이다(docs/features/live-docent).
 *
 * 아래 온타임 일정은 SDK 호출 시험(`poc`)에만 쓴다.
 *
 * 일정 등록 화면이 생기기 전까지는 시험용 일정을 쓴다. 마감은 앱을 켠 시각에서 90분 뒤다.
 * 주차와 도보 시간은 설명용 가정이고 좌표는 대략값이다.
 */
object Platform {
    private val LONG_TERM = Destination("icn-t1-long", "장기주차장", 37.4452, 126.4490, "인천 중구 공항로 272")
    private val WAYPOINT = Destination("poc-waypoint", "시흥하늘휴게소", 37.3899, 126.8663, "경기 시흥시 조남동")
    private val SHORT_TERM = Destination("icn-t1-short", "단기주차장", 37.4478, 126.4525, "인천 중구 공항로 272")

    fun create(context: Context): PlatformBundle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val navi = PleosNavi(context, listOf(LONG_TERM, SHORT_TERM, ScenarioSongpa.HANSEONG), scope).also { it.start() }
        val vehicle = PleosVehicle(context, scope).also { it.start() }
        val speech = PleosSpeech(context).also { it.start() }
        val location = PleosLocation(context).also { it.start() }
        val player = TrackPlayer(ScenarioSongpa.track, start = Instant.now())
        val drive = ReplayBackedNavi(navi, player)
        return PlatformBundle(
            plan = TripPlan(
                title = "시험용 일정 · 인천공항 제1터미널",
                deadline = Instant.now().plus(90, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES),
                parkingChoices = listOf(
                    ParkingChoice(LONG_TERM, parking = 6.minutes, walk = 8.minutes),
                    ParkingChoice(SHORT_TERM, parking = 6.minutes, walk = 5.minutes),
                ),
            ),
            departureLabel = "SDK 연동 시험",
            time = SystemTimeSource(),
            navi = navi,
            vehicle = vehicle,
            speech = speech,
            play = null,
            keepAlive = true,
            // 도슨트에 쓸 SDK 호출 시험: 경로를 잡고, 경유지를 넣고, 회전 안내와 위치를 받는다.
            docent = DocentBundle(
                title = "송파 백제 드라이브",
                time = player,
                navi = drive,
                vehicle = player,
                // 에뮬레이터의 기기 내 음성 엔진이 오류를 내서 음성은 쓰지 않는다. 해설은 자막, 답은 버튼이다.
                speech = ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds),
                road = player,
                stories = LiveStories(player, AheadTracker(ScenarioSongpa.pois), player.nextTurn),
                play = drive::drive,
                photos = mapOf(
                    ScenarioSongpa.SEOKCHON.id to PlacePhoto(R.drawable.photo_seokchon, "Saigen Jiro, CC0"),
                    ScenarioSongpa.BANGI.id to PlacePhoto(R.drawable.photo_bangi, "Gary Todd, CC0"),
                    // 박물관 바로 옆의 몽촌토성 사진이다.
                    ScenarioSongpa.HANSEONG.id to PlacePhoto(R.drawable.photo_mongchon, "몽촌토성, Saigen Jiro, CC0"),
                ),
                voice = false,
                replayChip = "위치 재생",
                replayChipShort = "위치 재생",
                replayNote = "경로와 경유지는 실제 내비에 보내고, 위치와 남은 시간은 OpenStreetMap 경로(© OpenStreetMap 기여자)를 " +
                    "10배속으로 재생합니다. 해설과 들르는 데 드는 시간은 예시 값입니다.",
                driveLabel = "송파 코스 출발",
                straightLine = true,
                quietWithin = 600,
            ),
            poc = { name ->
                // route:위도,경도 — 그곳으로 경로를 잡는다. 시연 경로의 거리와 회전을 재는 데 쓴다.
                if (name.startsWith("route:")) {
                    val (lat, lon) = name.removePrefix("route:").split(",").map { it.trim().toDouble() }
                    scope.launch {
                        delay(5.seconds)
                        navi.changeDestination(Destination("poc-route", "시험 목적지", lat, lon))
                    }
                }
                if (name == "waypoint") {
                    scope.launch {
                        delay(8.seconds)
                        navi.changeDestination(LONG_TERM)
                        delay(40.seconds)
                        navi.addWaypoint(WAYPOINT)
                        delay(20.seconds)
                        location.logLastKnown()
                    }
                }
            },
        )
    }
}
