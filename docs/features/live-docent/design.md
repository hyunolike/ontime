# 도슨트를 실제 SDK에 연결하기 (live-docent) 설계

- 작성일: 2026-11-20
- 상태: 구현 완료 (1차 EGM 검토: [reviews/2026-11-20-review-1.md](reviews/2026-11-20-review-1.md))
- 명세: [spec.md](spec.md)

## 1. 구조

```
 pleos 빌드 Platform.create()
 ┌──────────────────────────────────────────────────────────────────────────┐
 │ TrackPlayer (platform-fake) ── LocationGateway ─▶ LiveStories ─▶ Story ──┤
 │   └ TimeSource, RoadGateway, VehicleGateway ─────────────────────────────┤
 │ ReplayBackedNavi (platform-fake) ─ NaviGateway ──────────────────────────┼─▶ DocentSession (변경 없음)
 │   ├ route: TrackPlayer의 남은 시간                                          │
 │   └ 경로, 경유지 요청: PleosNavi (platform-pleos)                            │
 │ ScriptedSpeech (platform-fake) ── SpeechGateway ─────────────────────────┤
 └──────────────────────────────────────────────────────────────────────────┘
```

- `DocentSession`은 두 곳만 바꾼다. `settled`인 곳만 이야기를 시작하고(R1), 이야기 도중 차가 서면 멈춘다(R3-10). 나머지는 입력 게이트웨이의 구현을 바꿔 끼운다.
- `TrackPlayer`, `ReplayBackedNavi`는 `platform-api` 타입만 쓴다. `platform-pleos` 타입을 참조하지 않는다.
- `app`의 `pleos` 빌드에 `pleosImplementation(project(":platform-fake"))`를 더한다. 경주 시나리오 코드도 APK에 함께 들어가지만 수십 KB이고, 경주 사진은 `fake` 소스셋 리소스라 들어가지 않는다.
- 실차에서는 `TrackPlayer` 자리에 실제 위치와 회전 목록이 들어가고, `ReplayBackedNavi` 없이 `PleosNavi`를 바로 쓴다. 이번 범위는 아니다.

## 2. 새로 만들거나 바꾸는 것

### platform-api

```kotlin
/** 위치 한 점. heading은 북쪽 0도, 시계 방향이고 모르면 null. speed는 m/s. */
data class Fix(val latitude: Double, val longitude: Double, val heading: Double?, val speedMetersPerSecond: Double)

interface LocationGateway { val fixes: Flow<Fix> }

interface NaviGateway {
    // 기존 멤버 유지
    /** 넣어 둔 첫 경유지를 지운다. 지원하지 않으면 false. */
    suspend fun removeWaypoint(): Boolean = false
}
```

`PoiAhead.distanceMeters`의 KDoc을 "경로를 따라 남은 거리. 위치로 계산하는 구현은 직선 거리"로 고친다. `StoryGateway`, `RoadGateway` 주석도 맞춘다.

### core-session: `docent.AheadTracker`, `docent.LiveStories` (R1)

`AheadTracker(catalog, cone = 70, behind = 100, radius = 5_000, near = 150, receding = 200, undecided = 15, undecidedBeyond = 1_500, minSpeed = 1.0)`, `update(fix, nextTurn: Maneuver?): List<PoiAhead>`.

- 거리는 하버사인 공식(반지름 6,371,000m). 방위 차 `Δ = ((bearing - heading + 540) % 360) - 180`, `Δ < 0`이면 `LEFT`.
- 믿을 수 있는 방향 `trusted`: 속도가 `minSpeed` 이상이고 heading이 있으면 갱신한다. 없으면(출발 전) 전부 `settled = false`로 거리순.
- 장소마다 가장 가까웠던 거리 `closest`를 기억한다.
- 순서대로 판정한다.
  1. `거리 <= near`면 넣고 `settled`.
  2. `passed`면 뺀다.
  3. 움직이는 중이고 `|Δ| > behind`이고 `거리 > closest + receding`이면 `passed`에 넣고 뺀다.
  4. 나머지는 넣는다. `settled = |Δ| <= cone && 거리 <= radius && !(|Δ| < undecided && 거리 > undecidedBeyond) && !flips`. `flips`는 `nextTurn.turn == side && 거리·cos(Δ) > nextTurn.distance`.
- `LiveStories(location, tracker, nextTurn: Flow<Maneuver?>)`는 두 흐름을 `combine`한다. tracker가 상태를 가지므로 수집자는 하나여야 한다.
- `PoiAhead`에 `settled: Boolean = true`, `Maneuver`에 `turn: Side? = null`, `RouteManeuver`에 `turn`을 더했다. 기존 경주 재생기는 기본값이라 동작이 같다.
- 실차는 `nextTurn`을 내비 회전 목록(`TBTInfo.type`의 `TURN_LEFT`, `TURN_RIGHT`)에서 얻는다. 이번 범위에서는 연결하지 않았다.

### platform-fake: `track.Polyline`, `track.Track`, `track.TrackPlayer` (R2)

```kotlin
data class LatLon(val latitude: Double, val longitude: Double)          // 구현됨
object Polyline { fun decode(encoded: String): List<LatLon> }          // 구현됨, 정밀도 5
data class Track(val destination: Destination, val points: List<LatLon>, val maneuvers: List<RouteManeuver>)
class TrackPlayer(track: Track, start: Instant, cruise: Double = 10.0, playback: Double = 10.0, step: Duration = 2.seconds)
    : TimeSource, LocationGateway, RoadGateway, VehicleGateway
```

- `RouteManeuver`는 `DocentPlayer.kt`의 것을 쓴다. `cruise`는 m/s다(`RoadGateway.speedMetersPerSecond`와 이름이 겹치지 않게).
- 거리 계산은 출발점 기준 등장방형 투영(x = Δlon·cos(lat0)·R, y = Δlat·R)의 평면 미터로 한다. 14km 안에서 오차는 무시할 만하다. 누적 거리 배열 `cumulative`를 미리 만든다.
- 상태: `offset`(재생 경과 시간), `traveled`(경로 위 미터), `started`, `stop: Stop?`(`Stop(atMeters, place: Destination)`), `arrivedStop: Boolean`.
- `canStopAt(place)`: 경로 전체 선분에 장소를 투영해 가장 가까운 지점의 경로 위 미터 `m`을 구한다. `m > traveled`이면 `m`, 아니면 null.
- `stopAt(place): Boolean`: `canStopAt`이 null이 아니면 `stop`을 바꾸고 `arrivedStop = false`로 둔다.
- `run()`: `started = true`. `arrivedStop`이면 `stop = null`. 정차 지점(`stop?.atMeters ?: 경로 끝`)에 닿을 때까지 `delay(step / playback)`마다 `offset += step`, `traveled += cruise·step`을 하고 정차 지점에서 멈춘다. 경유지에 닿으면 `arrivedStop = true`. 기존 `DocentPlayer.run()`과 같은 계약이다.
- `fixes`: `arrivedStop`이면 `Fix(place.latitude, place.longitude, 마지막 heading, 0.0)`. 좌표는 `Poi.destination`과 정확히 같아야 한다(`DocentSession`의 도장 기준과 UI의 `arrivedAt()`이 거리 0을 본다). 아니면 `traveled`의 보간 위치, heading은 `traveled + 40m` 지점을 향한 방위, 속도는 달리는 중 `cruise` 아니면 0. 출발 전에도 첫 값을 바로 내보낸다.
- `remaining`: `(경로 길이 - traveled) / cruise` 초. `remainingFlow`로도 내보낸다.
- `nextManeuver`: `traveled` 뒤 600m 안의 가장 가까운 회전. `nextTurn`: 거리 제한 없이 다음 좌회전이나 우회전.
- `driving`: `started && !(정차 지점에 닿음)`.
- `atVisitedStop`: `arrivedStop`. `ReplayBackedNavi`가 지울 경유지를 아는 데 쓴다.
- `now`: `start + offset`.

### platform-fake: `ReplayBackedNavi` (R3)

```kotlin
class ReplayBackedNavi(
    private val primary: NaviGateway,
    private val player: TrackPlayer,
    private val routeConfirmTimeout: Duration = 20.seconds,
    private val confirmRadiusMeters: Int = 300,
) : NaviGateway
```

- `route`: `player`의 상태로 만든다. `RouteStatus(track.destination, Reading(player.remaining, player.now, REPLAY))`. `primary.route`는 쓰지 않는다(R3-3). 첫 값을 곧바로 내보낸다.
- 상태: `routed: Boolean`(requestRoute를 보냈는가), `visited: Boolean`(들른 경유지가 내비에 남아 있는가).
- `drive()`: `routed`가 아니면 `primary.changeDestination(track.destination)`을 부르고 `routed = true`(결과와 상관없이). `player.atVisitedStop && visited`이면 `primary.removeWaypoint()`, `visited = false`. 그다음 `player.run()`. 경유지에 닿아 돌아오면 `visited = player.atVisitedStop`.
- `addWaypoint(to)`:
  1. `player.canStopAt(to) == null`이면 false.
  2. `routed`가 아니면 `ensureRoute()`. 내비의 마지막 값이 `freshFor`(30초) 안에 받은 시연 목적지면 요청하지 않는다. 아니면 `changeDestination`을 보내고, 요청 시각 이후에 받은 시연 목적지 값을 `routeConfirmTimeout`까지 기다린다. 안 오면 false. 시각은 주입한 `clock`으로 잰다.
  3. `visited`면 `primary.removeWaypoint()`, `visited = false`.
  4. `primary.addWaypoint(to)`가 false면 false.
  5. `player.stopAt(to)`가 false면(1과 4 사이에 지나감) `primary.removeWaypoint()`로 되돌리고 false.
  6. true.
- 예외: `primary` 호출의 예외는 false로 바꾼다(`PleosNavi`는 이미 `runCatching`으로 감싼다).
- `estimateRemainingDrive`, `changeDestination`은 `primary`에 넘긴다.
- 시험에서는 `primary`에 가짜 `NaviGateway`를, 시간은 `runTest`의 가상 시간을 쓴다.

### platform-pleos

- `PleosNavi.removeWaypoint()`: `helper.removeWaypoint(WaypointIndex.FIRST)`를 `runCatching`으로 부르고 로그를 남긴다. 호출 스레드는 다른 호출처럼 `Platform`이 만든 `Dispatchers.Main` scope다.
- `PleosNavi`의 `known`에 시연 목적지를 넣는다. 로그와 경로 확인(R3-5)에서 목적지가 `track.destination`의 id로 보인다.

### app

- `DocentBundle`에 문구 필드를 더한다. 기본값은 지금 문구라서 `fake` 빌드는 바뀌지 않는다.
  - `replayChip = "예시 데이터 재생"` (좁은 화면 "예시 데이터"는 앞 네 글자로 자르지 않고 `replayChipShort = "예시 데이터"`로 따로 둔다)
  - `replayNote = 지금 설명 문구에서 사진 문장을 뺀 것`
  - `driveLabel = "경주 코스 시연 시작"`
  - `straightLine = false`: true면 출발 전 정차 화면의 장소 거리를 "직선 10.2km"로 적고 쪽을 뺀다(R5-3).
- `DocentDrivingScreen`, `DocentStoppedScreen`에 `DocentBundle`을 통째로 넘기도록 바꾼다(지금은 title, photos, voice를 따로 넘긴다). `DocentHeader`, `TripPane`, `PlacesPane`, `PlaceRow`가 필요한 필드를 읽는다.
- 사진 저작자: `Photo` 위에 `PlacePhoto.credit`을 작은 글씨로 오른쪽 아래에 적는다(R5-2). `fake` 사진도 같은 방식이다.
- `pleos` `Platform.create()`의 `docent`:
  - `title = "송파 백제 드라이브"`, `time/vehicle/road = player`, `stories = LiveStories(player, AheadTracker(ScenarioSongpa.pois))`, `navi = ReplayBackedNavi(pleosNavi, player)`, `play = navi::drive`
  - `speech = ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDelay = 3.5.seconds)`, `voice = false`
  - `photos`: 석촌동 고분군, 방이동 고분군, 목적지(몽촌토성 사진)
  - `TrackPlayer(start = Instant.now())`
  - 문구: `replayChip = "위치 재생"`, `replayChipShort = "위치 재생"`, `driveLabel = "송파 코스 출발"`, `straightLine = true`, `replayNote = "경로와 경유지는 실제 내비에 보내고, 차의 위치와 남은 시간, 회전은 OpenStreetMap 경로를 10배속으로 재생합니다. 해설과 들르는 데 드는 시간은 예시 값이고 화면 자막으로 보여 드립니다."`
- `pleos` 빌드의 `OnTimeSession`은 그대로 둔다. 경로 목적지가 주차장 목록에 없으므로 상태가 null로 남고 미리보기 조회(`requestTrip`)도 하지 않는다.

## 3. 시연 경로 데이터 (R4)

`platform-fake`의 `ScenarioSongpa.kt`에 둔다.

- 경로: OSRM(`router.project-osrm.org`, 2026-11-20 조회) 판교 출발점(127.094681, 37.4127212) → 한성백제박물관(127.1210850, 37.5154492), 14,293m. polyline 문자열 952자. © OpenStreetMap 기여자, ODbL.
- 목적지: `Destination("songpa-hanseong", "한성백제박물관", 37.5154492, 127.1210850, "서울 송파구 위례성대로 71")`.
- 회전(OSRM 단계, 경로 위 미터): 112 좌회전 창업로, 500 좌회전 대왕판교로, 6,437 우회전 헌릉로, 6,885 오른쪽 길 헌릉로, 8,228 좌회전 송파대로, 11,219 오른쪽 길 송파대로, 11,526 우회전 양재대로, 13,540 좌회전 위례성대로.
- 장소(좌표는 OpenStreetMap Nominatim, 경로에서 떨어진 거리는 경로선에 수선을 내려 잼):

| 장소 | id | 좌표 | 경로에서 | 들르기 |
|---|---|---|---|---|
| 서울공항 | `songpa-seoul-airbase` | 37.4445568, 127.1104831 | 4.3km 지점, 593m | 안 됨 |
| 석촌동 고분군 | `songpa-seokchon` | 37.5024456, 127.1022937 | 11.5km 지점, 1,179m | 30분 둘러보기, 늦어짐 40분 |
| 방이동 고분군 | `songpa-bangi` | 37.5074183, 127.1192072 | 12.8km 지점, 481m | 20분 둘러보기, 늦어짐 25분 |

해설(쪽은 앞에 자동으로 붙으므로 문장에 넣지 않는다. 석촌동은 길에서 멀어 "보인다"고 하지 않는다):

- 서울공항: "이름은 서울공항이지만 성남에 있는 공군 비행장이에요. 대통령 전용기가 뜨고 내리는 곳이죠."
- 석촌동 고분군: "가까이에 백제 초기의 돌무지무덤이 모여 있어요. 돌을 계단처럼 쌓아 올린 모양이 고구려 무덤과 닮았죠. 가장 큰 3호분은 한 변이 50m 가까이 돼요." 하이라이트 "계단처럼 쌓은 3호분 둘러보기".
- 방이동 고분군: "잔디로 덮인 둥근 무덤들이 공원 안에 남아 있어요. 돌로 방을 만들고 흙을 덮은 무덤이에요. 백제 무덤으로 알려졌지만 신라 무덤이라는 견해도 있어요." 하이라이트 "잔디 언덕 사이 산책".

사진(위키미디어 공용, 1280px로 줄임):

| 리소스 | 파일 | 저작자, 조건 |
|---|---|---|
| `photo_seokchon` | Stone Mound Tomb of Seoul Seokchon-dong Gobungun.JPG | Saigen Jiro, CC0 |
| `photo_bangi` | Baekje Stone Chamber Tombs in Bangi-dong 1.jpg | Gary Todd, CC0 |
| `photo_mongchon` | Seoul Mongchon Toseong-1.JPG | Saigen Jiro, CC0. 목적지 옆 몽촌토성이라 저작자 표시에 "몽촌토성"을 붙인다 |

## 4. 시험

- `AheadTracker`: R1 수용 기준(core-session).
- `TrackPlayer`, `ReplayBackedNavi`: R2와 R3-4~R3-7(platform-fake).
- 통합(core-session, `testImplementation(":platform-fake")` 있음): `ScenarioSongpa` 실제 경로로 `TrackPlayer`, `LiveStories`, `ReplayBackedNavi`(가짜 primary), `ScriptedSpeech`, `DocentSession`을 가상 시간으로 돌린다.
  1. 첫 상태가 곧바로 나오고 출발 전 `ahead`에 세 곳이 있다.
  2. 아무것도 들르지 않으면 세 곳이 순서대로 `told`에 들어가고 끝에서 `remaining == 0`.
  3. 석촌동 고분군에 들르면 도장이 찍히고, 다시 출발하면 가짜 primary에 `removeWaypoint`가 불리고 목적지까지 간다. 방이동 고분군도 같다.

## 5. 대안과 고르지 않은 이유

- **내비의 실제 위치와 남은 시간을 그대로 쓰기**: 에뮬레이터에서 차가 움직이지 않아 이야기가 나오지 않고 도착도 표시되지 않는다.
- **`geo fix`나 NMEA 주입으로 내비를 움직이기**: 에뮬레이터가 만드는 NMEA 체크섬이 틀려 내비가 버린다. 직접 넣은 문장도 같은 경로로 다시 만들어져 같은 결과였다.
- **기존 `DocentPlayer`(경로 위 거리만 재생)에 실제 내비만 붙이기**: 위치와 방향으로 쪽을 가리는 계산이 시연에서 돌지 않는다.
- **경유지 정차 지점을 도로 위에 두고 도장 기준을 늘리기**: 실차 로직을 시연에 맞춰 바꾸지 않는다.
- **재생 부품을 새 모듈(`platform-replay`)로 나누기**: 시연까지 2주라 모듈 추가 비용을 쓰지 않는다. `platform-fake`가 이미 재생 부품의 자리다.

## 5-1. 재탐색과 경유지 한도 (docent-plus E7)

- 길을 벗어나 내비가 경로를 다시 잡아도(`onReRouteEnded`) 앞에 있는 장소는 위치와 진행 방향으로 계산하므로 다시 계산할 것이 없다. 실차에서 회전 정보는 내비 회전 목록을 다시 조회해 받는다.
- 경유지는 한 번에 하나만 둔다. Pleos 내비의 경유지 최대 두 개 한도 안이다.

## 6. 미정 사항

- 실차에서 진행 방향을 `FusedLocation.heading`과 `onCurrentLocationInfo.heading` 가운데 무엇으로 받을지. 둘 다 에뮬레이터에서 0이었다.
- 내비가 `addWaypoint`를 `onError`로 거절했을 때 되돌리는 방법.
