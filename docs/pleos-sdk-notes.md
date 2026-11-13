# Pleos SDK 확인 기록

공식 문서(https://document.pleos.ai)를 2026-11-13에 읽고 정리했습니다. 각 항목의 상태를 세 가지로 구분합니다.

- **문서 확인**: 문서에 적혀 있음. 에뮬레이터에서 직접 돌려 보지는 않음
- **미확인**: 문서에서 찾지 못함. PoC로 확인해야 함
- **에뮬레이터 검증**: 직접 실행해 응답을 받음 (아직 없음)

API Reference 페이지는 스크립트로 그려져 본문을 읽지 못했습니다. 정확한 시그니처는 Android Studio에서 SDK를 받은 뒤 소스와 자동 완성으로 다시 확인합니다.

## 프로젝트 설정 (문서 확인)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
```

```kotlin
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:FusedLocation:1.1.3")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
implementation("ai.pleos.playground:TextToSpeech:3.3.1")
implementation("ai.pleos.playground:SpeechToText:3.3.1")
```

- minSdk 28 이상 (차량 사이버 보안 요건), Gradle 8.0 이상.
- compileSdk, targetSdk, AGP, Kotlin 버전은 문서에 없습니다. 에뮬레이터가 API 34이므로 34에 맞춥니다.

## 에뮬레이터 (문서 확인)

- Pleos Connect Emulator v2.1.0 (2026-10-19 릴리스), Android API 34, Form Factor는 Automotive.
- Android Studio의 SDK Manager에서 SDK Update Sites에 추가합니다.
  - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
- AVD 설정: Default boot는 `Cold`, RAM 4GB. Quick boot로 띄우면 차량 값(VHAL)을 쓸 수 없습니다.
- STT 테스트는 Extended Controls의 Microphone에서 호스트 오디오 입력을 켭니다.
- Virtual Console의 Emulation Controls로 차량 값을 바꿉니다.
  - Motion: Gear Selection (P, R, N, D)
  - Convenience: Door Position, Door Lock
  - Energy: Remaining Range (0~1,000,000m), Battery SOC, Battery Energy, Charge Port
- 화면 크기는 폼팩터 테스트 도구(Host Display Bridge, Go 1.22 이상 필요)로 바꿉니다. 예시 화면은 12.9인치 세로와 14.6인치 가로입니다. dp, DPI, px 수치는 문서 본문에 없어 직접 확인합니다.
- **미확인**: 위치와 내비 경로 상태를 주입하는 방법. GPU 권장 사양이 Windows/Linux 노트북 기준으로만 적혀 있어 Apple Silicon에서의 동작 여부.

## NaviHelper 3.0.5

문서 확인:

```kotlin
naviHelper.initialize()
naviHelper.addListener(listener)          // NaviHelperEventListener
naviHelper.requestRoute(RouteInfo(latitude = ..., longitude = ..., displayLongitude = null,
    displayLatitude = null, poiName = "...", poiId = "...", poiSubId = "0", address = "..."))
naviHelper.addWaypoint(RequestWaypointInfo(latitude, longitude, waypointIndex, poiId, poiName, poiSubId, address))
naviHelper.removeWaypoint(WaypointIndex.FIRST)
naviHelper.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination = ..., waypoints = listOf(...)))
naviHelper.getDestinationInfo()           // 결과는 onDestinationInfo()로 온다
naviHelper.cancelRoute()
```

- 목적지를 바꿀 때도 `requestRoute()`를 다시 부릅니다. 호출이 성공해도 경로 탐색이 끝났다는 뜻은 아니며 결과는 콜백으로 확인합니다.
- 경유지는 최대 두 개입니다 (`WaypointIndex.FIRST`, `SECOND`).
- Java 예시의 `RouteInfo` 생성자는 경도가 먼저 옵니다. Kotlin에서는 이름 있는 인자를 씁니다.
- 리스너 콜백: `onRouteStarted`, `onRoutingCompleted`, `onRouteCancelled`, `onReRouteEnded`, `onRouteOptionChanged`, `onDestinationChanged`, `onDestinationArrived`, `onWaypointChanged`, `onWayPointArrived`, `onDestinationInfo`, `onWayPointInfo`, `onRouteStateInfo`, `onTBTInfo`, `onTripInfo`, `onNavigationStatus`, `onDrivingInfo`, `onCurrentLocationInfo`, `onError` 등.
- 권한: `pleos.car.permission.NAVI_ROUTE` (조회), `pleos.car.permission.NAVI_ROUTE_SEARCH` (경로 요청과 경유지).

미확인:

- **남은 주행 시간이나 도착 예정 시각을 주는 콜백과 필드.** `onTripInfo`, `onRouteStateInfo`, `onDrivingInfo`가 후보지만 문서에 필드 설명이 없습니다. 여유 시간 계산의 중심 입력이므로 가장 먼저 확인합니다.
- 이벤트는 내비 앱 구현과 상태에 따라 달라질 수 있다고 적혀 있습니다.

## Vehicle 2.3.2

문서 확인:

```kotlin
vehicle.initialize()
val door = vehicle.getDoor()
door.checkDoorPositionCapabilities(onComplete = { config, areaMap -> /* config.accessType */ }, onFailed = { e -> })
door.getCurrentDoorPosition(area = DoorArea(DoorValue.ROW_1_LEFT), onComplete = { pos: Int? -> }, onFailed = { e -> })
door.registerDoorPosition(doorPositionListener = listener)   // onPositionUpdated(pos: Int?)
door.unregisterDoorPosition(doorPositionListener = listener)
vehicle.release()
```

- 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper.
- 지원 여부는 `checkXxxCapabilities`로 먼저 확인하고, `AccessType`이 `READ`나 `READ_WRITE`일 때만 씁니다.
- 권한은 `pleos.car.permission.CAR_DOORS`, `CAR_ENERGY`, `CAR_DRIVE` 형식입니다. 빠지면 `onFailed`로 `VehicleException`이 옵니다.
- 실제 마켓 입점 때는 사전 협의된 권한만 쓸 수 있습니다. 공모전 심사는 입점 심사가 아닙니다.

미확인:

- **기어 상태 getter.** 도메인 목록에 Gear가 없습니다. Virtual Console에는 Gear Selection이 있어 값은 존재하지만 어느 도메인의 어떤 메서드인지 찾지 못했습니다 (DrivingMode가 후보).
- EvBattery와 Fuel의 주행가능거리, SOC getter 이름.
- 도어 위치 값 `pos`의 의미.

## SpeechToText 3.3.1

문서 확인:

```kotlin
val stt = SpeechToText(applicationContext, Mode.HYBRID)   // HYBRID, SERVER, ON_DEVICE
stt.initialize()
stt.addListener(resultListener)
stt.request()      // 인식 시작, 인자 없음
stt.stop()
```

- `ResultListener`: `onReady()`, `onStartedRecognition()`, `onUpdated(stt: String, completed: Boolean)`, `onEndedRecognition()`, `onError(errorType: ErrorType)`.
- 발화를 텍스트로 돌려줄 뿐이고 명령 해석은 앱이 합니다.
- `SERVER`와 `HYBRID`의 서버 인식은 Playground My Project의 Client ID와 Client Secret으로 `registerApp()`을 해야 합니다. `ON_DEVICE`는 등록이 필요 없습니다.
- 권한: `pleos.car.permission.STT_SERVICE`.

미확인: 한국어와 `ON_DEVICE` 모드의 한국어 지원, 주행 중 사용 제한.

## TextToSpeech 3.3.1

문서 확인:

```kotlin
val tts = TextToSpeech(applicationContext, Mode.HYBRID)
tts.initialize()
tts.addEventListener(listener)
tts.speak("단기주차장으로 바꾸면 12분을 아낄 수 있습니다")   // isFinal 기본값 true
tts.stop()
```

- 서버를 쓰는 모드는 `OnServerConnectionListener.onConnected()` 이후에 `speak()`를 부릅니다.
- 권한: `pleos.car.permission.TTS_SERVICE`.

## FusedLocation 1.1.3

문서 확인: `isSupported()`, `initialize()`, `getLastKnownLocation()`, `registerFusedLocationCallback()`, `unregisterFusedLocationCallback()`, `release()`. 결과는 `LocationData`(위도, 경도, 고도, 진행 방향, 신뢰도, 타임스탬프). 권한은 `pleos.car.permission.FUSED_LOCATION`.

## Gleo Interface와 LLM SDK

- LLM SDK는 에뮬레이터 v2.1.0에서 제공이 끝났습니다.
- Gleo Interface는 `assets/schema.json`을 Pleos가 검토하고 승인해야 활성화됩니다. 공모전 참가팀은 이 검토를 신청할 수 없으므로 **이번 출품작에서는 쓰지 않습니다.**
