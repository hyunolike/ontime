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
import io.github.hyunolike.ontime.docent.NarrationPlanner
import io.github.hyunolike.ontime.docent.RouteTimeline
import io.github.hyunolike.ontime.docent.speakingSeconds
import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.ReplayVehicle
import io.github.hyunolike.ontime.tour.FileCache
import io.github.hyunolike.ontime.tour.TourApiClient
import io.github.hyunolike.ontime.tour.TourCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
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
        // 주행은 재생기, 충전은 실제 차량 값. 에뮬레이터에서 충전구를 열 수 없으면 `poc charge`로 시험 값을 켠다.
        val docentVehicle = ReplayVehicle(player, vehicle)
        val tracker = AheadTracker(ScenarioSongpa.pois, passedOnRoute = { player.hasPassed(it.destination) })
        // 앞길 전체를 보고 말할 순서와 길이를 정한다. 10배속 재생(초속 10m)이라 실제 1초에 경로 100m를 간다.
        val planner = NarrationPlanner(player, RouteTimeline.build(player, ScenarioSongpa.pois), metersPerSecond = { 100.0 })
        // 위치 출처. 재생(기본) 또는 실제 위치(FusedLocation, 에뮬레이터에서는 tools/geo-drive.py가 좌표를 넣음).
        val modeChip = MutableStateFlow<String?>(null)
        val modeNote = MutableStateFlow<String?>(null)
        var live = false
        var followJob: Job? = null
        fun startFollowing() {
            followJob = scope.launch { player.follow(location.fixes) }
        }
        // local.properties에 TourAPI 키가 있으면 경로를 따라 관광공사 관광지를 더한다. 실패하면 내장 목록만 쓴다.
        BuildConfig.TOURAPI_KEY.takeIf { it.isNotEmpty() }?.let { key ->
            scope.launch {
                val extra = withContext(Dispatchers.IO) {
                    val client = TourApiClient(key, cache = FileCache(java.io.File(context.filesDir, "tour")))
                    val points = player.samples(2_000.0).map { it.latitude to it.longitude }
                    TourCatalog(client).load(points, existing = ScenarioSongpa.pois)
                }
                planner.timeline = RouteTimeline.build(player, ScenarioSongpa.pois + extra)
                android.util.Log.i("OnTimePleos", "<- TourCatalog ${extra.size}곳: ${extra.joinToString { it.destination.name }}")
                // 수집과 같은 메인 스레드에서 바꾼다.
                tracker.setCatalog(ScenarioSongpa.pois + extra)
            }
        }
        // local.properties에 Playground 등록 정보가 있으면 도슨트 전용 서버 음성을 쓴다. 없으면 자막과 버튼이다.
        val voiceSpeech = BuildConfig.PLEOS_TTS_CLIENT_ID.takeIf { it.isNotEmpty() }?.let { id ->
            PleosSpeech(context, PleosSpeech.ServerCredentials(id, BuildConfig.PLEOS_TTS_CLIENT_SECRET)).also { it.start() }
        }
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
                vehicle = docentVehicle,
                // 서버 음성이 없으면 에뮬레이터의 기기 내 엔진이 오류를 내므로 자막과 버튼만 쓴다.
                // 자막은 실제 음성처럼 문장 길이만큼 보여 준다. 스케줄러가 짧은 해설을 고르는 의미가 생긴다.
                speech = voiceSpeech ?: ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDuration = { speakingSeconds(it).seconds }),
                voice = voiceSpeech?.ready ?: MutableStateFlow(false),
                askWindow = if (voiceSpeech != null) 12.seconds else kotlin.time.Duration.INFINITE,
                road = player,
                stories = LiveStories(player, tracker, player.nextTurn),
                play = drive::drive,
                photos = mapOf(
                    ScenarioSongpa.SEOKCHON.id to PlacePhoto(R.drawable.photo_seokchon, "Saigen Jiro, CC0"),
                    ScenarioSongpa.BANGI.id to PlacePhoto(R.drawable.photo_bangi, "Gary Todd, CC0"),
                    // 박물관 바로 옆의 몽촌토성 사진이다.
                    ScenarioSongpa.HANSEONG.id to PlacePhoto(R.drawable.photo_mongchon, "몽촌토성, Saigen Jiro, CC0"),
                    ScenarioSongpa.MONGCHON.id to PlacePhoto(R.drawable.photo_mongchon, "Saigen Jiro, CC0"),
                ),
                replayChip = "위치 재생",
                replayChipShort = "위치 재생",
                replayNote = "경로와 경유지는 실제 내비에 보내고, 위치와 남은 시간은 OpenStreetMap 경로(© OpenStreetMap 기여자)를 " +
                    "10배속으로 재생합니다. 해설과 들르는 데 드는 시간은 예시 값입니다.",
                driveLabel = "송파 코스 출발",
                straightLine = true,
                quietWithin = 600,
                walkFrom = player,
                catalog = { tracker.places },
                remember = true,
                reset = {
                    followJob?.cancel()
                    followJob?.join()
                    followJob = null
                    drive.reset()
                    tracker.reset()
                    docentVehicle.simulateCharging(false)
                    if (live) startFollowing()
                },
                planner = planner,
                modeChip = modeChip,
                modeNote = modeNote,
            ),
            poc = { name ->
                // charge — 충전 시험 값을 켜고 끈다. 충전 대기 화면을 에뮬레이터에서 보여 주기 위해서다.
                if (name == "charge") docentVehicle.simulateCharging(!docentVehicle.simulatingCharge)
                // location-live, location-replay — 위치 출처를 바꾼다. 바꾼 뒤 앱이 처음부터 다시 시작한다(OnTimeRuntime.poc).
                if (name == "location-live" || name == "location-replay") {
                    live = name == "location-live"
                    modeChip.value = if (live) "에뮬레이터 GPS" else null
                    modeNote.value = if (live) {
                        "위치는 FusedLocation SDK에서 받습니다. 에뮬레이터에 송파 경로 좌표를 넣어 움직이고, 경로와 경유지는 실제 내비에 보냅니다."
                    } else {
                        null
                    }
                    android.util.Log.i("OnTimePleos", "-> location ${if (live) "live" else "replay"}")
                }
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
