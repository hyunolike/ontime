# 도슨트 고도화 (docent-plus) 설계

- 작성일: 2026-11-20
- 상태: 1차 EGM 검토 반영. 아래 "검토 반영 결정"이 앞 절과 다르면 그쪽을 따른다
- 명세: [spec.md](spec.md)

## E1. 이야기 길이

- `Poi`에 `shortStory: String = ""`를 더한다. 비어 있으면 `story`의 첫 문장(마침표, 물음표, 느낌표, "요." 기준으로 처음 끊기는 곳까지)을 쓴다.
- `DocentSession.narrate()`가 이야기를 고를 때 `pickStory(ahead, speed, nextManeuver, others)`를 부른다. 순수 함수로 `docent/StoryLength.kt`에 둔다.
  - `readSeconds(text) = text.length / 6.0`
  - 짧게: `distance / speed < 60` 또는 `nextManeuver != null && nextManeuver.distance / speed < readSeconds(story)` 또는 `others`(아직 소개하지 않은 `settled` 장소들) 중 `(distance - announceWithin) / speed < readSeconds(story)`인 곳이 있음
- 다음 회전은 `road.nextManeuver.first()`(600m 안)만 알 수 있으므로, 600m 밖 회전은 두 번째 조건에 걸리지 않는다. 회전 600m 앞부터는 어차피 새 이야기를 꺼내지 않으므로(quietWithin) 충분하다.

## E2. 좌석 안내

- `DocentSession`에 `seatHint: Boolean = false`를 둔다. 켜면 해설 문장이 `"5분 뒤 오른쪽, 조수석 쪽에 대릉원."`이 된다. 왼쪽은 `"4분 뒤 왼쪽에 첨성대."` 그대로다.
- 기본값이 꺼짐이라 기존 테스트 기대값은 그대로다. 앱은 `OnTimeRuntime`에서 두 빌드 모두 켠다. 시험은 켠 세션을 따로 만들어 확인한다.

## E3. 충전 대기

- `platform-api`: `VehicleGateway`에 `val charging: Flow<Boolean> get() = flowOf(false)`, `val batteryPercent: Flow<Int?> get() = flowOf(null)`를 기본 구현으로 더한다.
- `core-session`: `DocentState`에 `charging: Boolean`, `battery: Int?`, `walkable: List<Walkable>`를 더한다. `Walkable(poi, walkMinutes)`.
  - `walkable`은 `!driving && charging`일 때만 채운다. 지나온 곳도 걸어서 갈 수 있으므로 `stories.ahead`(지나치지 않은 곳만)로는 부족하다. 그래서 `DocentSession`이 선택 입력 `walk: WalkSource?`(`location: LocationGateway`, `catalog: () -> List<Poi>`)를 받는다. 없으면 `walkable`은 늘 비어 있다(`fake` 빌드).
  - 계산은 순수 함수 `nearbyOnFoot(fix, catalog)`(`docent/OnFoot.kt`): 직선 거리 `d <= 1_200`, 걷는 시간 `ceil(d * 1.3 / 1.2 / 60)`분, 가까운 순서 세 곳.
- `platform-pleos`: `PleosVehicle`에 충전구 열림 리스너와 배터리 잔량 리스너를 더한다. 실패하면 `charging = false`.
- `platform-fake`: `TrackPlayer`에 `charging` 상태와 `setCharging(Boolean)`을 둔다. `pleos` 빌드는 `PleosVehicle.charging`과 `poc charge`가 바꾸는 재생 값 가운데 하나라도 참이면 충전 중으로 본다(`combine { a, b -> a || b }`). `poc`로 켠 경우 칩을 "충전 시험"으로 바꾼다.
- `app`: 정차 화면 `TripPane`에서 `state.walkable`이 있으면 도장 카드 아래에 "충전하는 동안 걸어서 둘러볼 곳" 카드를 그린다. 장소 이름, "걸어서 13분", 하이라이트 한 줄. 잔량이 있으면 제목 옆에 "배터리 64%".
- `ScenarioSongpa`에 몽촌토성(`songpa-mongchon`, 37.5175329, 127.1126700, 들르기 없음)을 더한다. 해설: "흙을 쌓아 만든 백제의 성이에요. 지금은 올림픽공원 안에서 성벽 위 산책로로 걸어 볼 수 있어요." 짧은 해설: "흙으로 쌓은 백제의 성, 몽촌토성이에요." 하이라이트 "성벽 위 산책로 걷기". 사진은 기존 `photo_mongchon`.

## E4. TourAPI

- 새 모듈 없이 `platform-fake`가 아닌 순수 JVM 모듈이 필요하다. `core-session`에 `tour` 패키지를 두면 HTTP가 들어가 성격이 섞이므로, **새 모듈 `platform-tour`**(kotlin-jvm, 의존: `platform-api`, `kotlinx-serialization-json`)를 만든다.
  - `TourApiClient(serviceKey, http: (String) -> String)`: `http`를 주입해 시험에서는 고정 JSON을 넣는다. 실제는 `HttpURLConnection`.
  - 요청: `https://apis.data.go.kr/B551011/KorService2/locationBasedList2?serviceKey=...&MobileOS=AND&MobileApp=Gilbeot&_type=json&mapX=<경도>&mapY=<위도>&radius=1000&contentTypeId=12&numOfRows=10&arrange=E`, 개요는 `detailCommon2?contentId=...`.
  - 응답 형식은 KorService1과 같다고 가정한다: `response.body.items.item[]`의 `title`, `mapx`, `mapy`, `contentid`, `addr1`, 결과가 없으면 `items`가 빈 문자열. 개요는 `overview`. **키를 받으면 실제 응답으로 이 가정을 확인한다.**
  - `TourCatalog.load(track points, existing pois, cache)`: 2km 간격 지점마다 조회, 중복 제거(이름 같음 또는 150m 안), 개요 앞 두 문장을 `story`로, `canStop = true`, `stayMinutes = 30`, `extraMinutes = null`(모름), `source = Source.LIVE`.
  - `Cache`: 요청 URL의 해시를 파일 이름으로 응답 본문을 저장한다. 파일이 있으면 통신 없이 쓴다.
- `AheadTracker`는 목록을 바꿀 수 있어야 한다: `setCatalog(list)`. 바뀌어도 `passed`, `closest`는 id 기준이라 유지된다.
- `pleos` `Platform`: `BuildConfig.TOURAPI_KEY`가 있으면 앱 시작 때 백그라운드에서 `TourCatalog.load`를 하고 끝나면 `tracker.setCatalog(내장 + 관광공사)`. 캐시 폴더는 `context.filesDir/tour`.
- 출처: TourAPI 장소의 `source = LIVE`. 칩 문구는 그대로 "위치 재생". 설명 문구에 "관광공사 데이터 n곳 포함"을 더한다(`DocentBundle.replayNote`를 함수로 바꾸지 않고, 정차 화면에서 `state.ahead`의 LIVE 개수를 세어 덧붙인다).
- 해설 끝에 "(한국관광공사)" 출처를 자막에만 붙인다.

## E5. 음성

- `app/build.gradle.kts`가 `local.properties`의 `pleos.tts.clientId`, `pleos.tts.clientSecret`, `tourapi.key`를 `BuildConfig` 필드로 넣는다. 없으면 빈 문자열.
- `PleosSpeech(context, credentials: Credentials?)`: 있으면 TTS와 STT를 `Mode.SERVER`로 만들고 `registerApp(clientId, clientSecret, listener)`를 부른다. 연결되면 `ready = true`. `say`와 `askYesNo`는 `ready`가 아니면 곧바로 돌아간다(`askYesNo`는 영원히 기다리는 `awaitCancellation()`로 두어 버튼만 받는다).
- `pleos` `DocentBundle`: 정보가 있으면 `speech = PleosSpeech`, `voice = true`. 없으면 지금의 `ScriptedSpeech`.
- 시험: 정보가 없을 때의 경로는 기존 테스트가 덮는다. 정보가 있을 때는 에뮬레이터에서 확인하고, 그 전까지 "미확인"으로 기록한다.

## E6. 이어 가기

- `DocentSession`에 `snapshot(): DocentMemory`와 생성자 인자 `memory: DocentMemory?`를 둔다. `DocentMemory(destinationId, toldIds, waypointId, stampIds)`.
- 복원: 첫 입력에서 목적지 id가 같으면 `toldIds`, `told`(장소 목록에서 찾음), `waypoint`, `stamps`를 채운다.
- `app`: `OnTimeRuntime`이 `docentState`가 바뀔 때마다(목적지, told, waypoint, stamps가 바뀐 경우만) `SharedPreferences`에 JSON 문자열로 저장한다. 시작할 때 읽어 넘긴다. `fake` 빌드는 시연 반복을 위해 저장하지 않는다.
- 재생기 위치는 저장하지 않는다. 앱이 다시 시작되면 재생은 처음부터지만 들은 곳은 다시 말하지 않는다. 시연에서는 문제가 되므로 정차 화면에 "처음부터 다시" 버튼을 두어 저장을 지운다.

## E7

- `PleosNavi.onReRouteEnded`를 로그로 남긴다.
- live-docent 설계에 재탐색과 경유지 한도에 대한 문장을 더한다.

## E8

- `docs/business.md` 초안. 수치는 출처가 있는 것만 쓴다.

## 순서

E2 → E1 → E3 → E6 → E5 → E4 → E7 → E8. 키가 필요 없는 것부터, 화면에 보이는 것부터 한다.

## 검토 반영 결정 (2026-11-20, [1차 검토](reviews/2026-11-20-review-1.md))

### 음성 (E5)
- `SpeechGateway`에 `suspend fun listenYesNo(prompt: String): Boolean? = askYesNo(prompt)`를 더한다. `null`은 "답 없음"이다. `PleosSpeech`는 무응답이나 애매한 답이면 `null`.
- `DocentSession`의 질문은 `select { 음성 답(null이 아닐 때만), 버튼 답, 10초 제한 }`이다. `ScriptedSpeech`는 10초 뒤 `false`를 주므로 지금 동작과 같다.
- 도슨트는 전용 `PleosSpeech(SERVER)` 인스턴스를 쓴다. `OnTimeSession`(시험용)은 지금의 ON_DEVICE 인스턴스를 그대로 쓴다.
- `DocentBundle.voice`는 `StateFlow<Boolean>`으로 바꾼다. 서버 등록이 끝나야 참이 되고, 화면의 "말해도 됩니다" 안내는 이 값을 따른다.
- `app/build.gradle.kts`에 `buildFeatures { buildConfig = true }`를 켠다. 키는 `local.properties`에서만 읽는다. 키가 들어간 APK는 로컬 시연용이고, 제출하거나 공개하는 APK는 키 없이 빌드한다(키가 APK 안에서 읽힐 수 있기 때문).

### 충전 (E3)
- `platform-api`: `data class ChargeState(val charging: Boolean, val batteryPercent: Int?, val simulated: Boolean)`, `VehicleGateway.charge: Flow<ChargeState>`(기본 `flowOf(ChargeState(false, null, false))`).
- `PleosVehicle`: `EvBattery.isEvChargePortOpen`, `registerEvChargePortOpen(EvChargePortOpenListener.onEvChargePortOpenUpdated)`, `getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge`(javap로 확인한 이름). 매니페스트에 `pleos.car.permission.CAR_ENERGY`를 더한다.
- `ReplayVehicle(player, real: VehicleGateway?)`(platform-fake): `driving`은 재생기, `charge`는 실제 값과 `poc charge`로 켠 시험 값을 합친다. 시험 값이 켜져 있으면 `simulated = true`.
- 화면: `simulated`면 카드 제목 옆에 "충전 시험" 칩.
- 출발 전에도 충전 중이면 카드를 보여 준다(충전하고 출발하는 경우).
- 몽촌토성을 더하므로 `SongpaDriveTest`의 "세 곳" 기대를 네 곳으로 고친다. 몽촌토성 사진은 목적지와 같은 사진을 쓴다(바로 옆이라 같은 장소의 사진이다).

### 출처 (E4)
- `DocentState.source`는 장소가 아니라 경로의 출처를 따른다. 두 빌드 모두 경로가 재생이라 칩이 늘 보인다.
- 관광공사 장소는 `source = Source.LIVE`. 이야기 화면의 해설 아래에 "해설 출처 한국관광공사"를 작게 적는다. 음성에는 넣지 않는다.

### TourAPI (E4)
- 새 모듈 `platform-tour`(kotlin-jvm). 의존: `platform-api`, `org.jetbrains.kotlinx:kotlinx-serialization-json`(라이브러리만, 플러그인 없이 `Json.parseToJsonElement`). 입력은 `List<Pair<Double, Double>>`(위도, 경도).
- 조회 수 상한: 경로 지점 최대 7회, 개요는 거리순 최대 6곳(합 13회). 요청마다 연결과 읽기 제한 5초. `Dispatchers.IO`.
- 관광공사 장소는 최대 6곳만 더한다.
- `AheadTracker.setCatalog`는 메인 스레드에서만 부른다(`withContext(Dispatchers.Main)`). 수집도 메인이라 경합하지 않는다.
- `app`에 `INTERNET` 권한을 더한다(`pleos` 소스셋 매니페스트).
- 방문 도장 칸은 최대 4칸. 도장 찍힌 곳, 들르러 가는 곳, 들은 곳, 가까운 곳 순서.

### 이어 가기 (E6)
- `DocentSession`은 `catalog: () -> List<Poi>`를 선택 입력으로 받는다(충전 대기와 같이 씀). 복원은 들은 곳과 도장만 한다. 경유지는 복원하지 않는다(재생기와 내비에 다시 넣는 일이 생기므로).
- 저장 여부는 `DocentBundle.remember: Boolean`(`pleos`만 참).

### 그 밖
- 첫 문장은 `(?<=[.?!])\s` 기준으로 자른다. "1.2km" 같은 소수점에서 잘리지 않는다.
- E1의 "다음 회전" 조건은 `nextManeuver`가 600m 안만 알려 주고 `quietWithin`도 600m라 실제로는 거의 걸리지 않는다. 효과는 앞의 두 조건에서 나온다.
