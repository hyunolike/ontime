# 도슨트를 실제 SDK에 연결하기 (live-docent) 작업

- 명세: [spec.md](spec.md), 설계: [design.md](design.md), 검토: [reviews/](reviews/)
- 현재 단계: 완료
- 다음 작업: 2차 PT 시연 연습. 남은 과제는 README의 "남은 것" 참고

| ID | 작업 | 요구사항 | 검증 | 상태 |
|---|---|---|---|---|
| T1 | `Fix`, `LocationGateway`, `NaviGateway.removeWaypoint()`, `PoiAhead.settled`, `Maneuver.turn`, KDoc | R1, R3-6 | 컴파일 | 완료 |
| T2 | `AheadTracker`, `LiveStories` | R1 | `AheadTrackerTest` 11개 | 완료 |
| T3 | `Polyline`, `Track`, `TrackPlayer` | R2 | `PolylineTest` 2개, `TrackPlayerTest` 7개 | 완료 |
| T4 | `ReplayBackedNavi` | R3-2~R3-7 | `ReplayBackedNaviTest` 10개 | 완료 |
| T5 | `PleosNavi.removeWaypoint`, `known`에 시연 목적지 | R3-6 | `:app:assemblePleosDebug`, 에뮬레이터 로그 | 완료 |
| T6 | `ScenarioSongpa`, 사진 리소스, 통합 테스트 | R4 | `ScenarioSongpaTest` 1개, `SongpaDriveTest` 7개 | 완료 |
| T7 | `pleos` `Platform`을 도슨트로 구성 | R3-1, R3-9, R3-11 | `:app:assemblePleosDebug` | 완료 |
| T8 | 문구 필드, 사진 저작자, 직선 거리 표시 | R5-1~R5-4 | 에뮬레이터 화면 | 완료 |
| T9 | 에뮬레이터 시연 확인 | R3 수용 기준 | 아래 기록 | 완료 |
| T10 | README, SDK 확인 기록 | R5-5 | 문서 확인 | 완료 |
| T11 | 전체 테스트 | R6 | `./gradlew test` 88개 통과, `assembleFakeDebug`, `assemblePleosDebug` | 완료 |

## 에뮬레이터 확인 (2026-11-20)

Pleos Connect Emulator v2.1.0, 화면을 1:1로 나눠 왼쪽에 길벗, 오른쪽에 내비를 띄웠다.

- 출발 전 정차 화면에 세 곳이 직선 거리로 나온다.
- 출발하면 `requestRoute`가 가고 내비가 한성백제박물관 28분, 13km로 안내한다.
- 서울공항은 "5분 뒤 오른쪽 창밖", 석촌동 고분군은 "5분 뒤 왼쪽 창밖", 방이동 고분군은 "2분 뒤 왼쪽 창밖"으로 소개된다. 경로선에 수선을 내려 잰 쪽과 같다.
- 회전 앞에서는 "잠시 쉽니다"를 보여 주고, 묻던 질문을 닫는다.
- 출발 전 "여기로 안내"로 석촌동 고분군을 넣으면 `addWaypoint` 6초 뒤 `onWaypointChanged(FIRST, ADD)`가 오고 내비 화면에 경유지가 나타난다. 그곳에 서면 방문 도장 1/2가 찍힌다.
- "계속 안내"를 누르면 `removeWaypoint` 6초 뒤 `onWaypointChanged(FIRST, REMOVE)`가 오고 내비가 원래 경로로 돌아간다.
- 목적지에 닿으면 "목적지에 도착했습니다"가 나온다.

- `fake` 빌드는 정차 화면과 주행 화면이 이전과 같고, 사진 오른쪽 위에 저작자가 붙는다(에뮬레이터의 release 앱을 지우고 debug 빌드로 확인).

확인하지 못한 것: 주행 중 질문에 "들르기"를 눌러 경유지를 넣는 흐름. 자동화로 누를 시점을 맞추기 어려웠다. 같은 흐름은 `SongpaDriveTest`가 확인한다.

## 진행 기록

- 2026-11-20: 에뮬레이터에서 위치 주입(`geo fix`, NMEA)과 기어 주입이 막혀 있음을 확인. 사용자 결정으로 혼합 방식과 판교 출발 경로로 정함. 내비 경로 시험용 `poc route:위도,경도`를 `pleos` 빌드에 추가함.
- 2026-11-20: 1차 EGM 검토 두 건을 반영함.
- 2026-11-20: 구현 중 실제 경로로 돌려 보며 찾은 문제와 조치.
  - 출발 직후 골목에서 서울공항이 지나친 것으로 빠짐 → 멀어질 때만 지나친 것으로 봄.
  - 방이동 고분군이 우회전 전에 "오른쪽"으로 소개됨 → 정면의 먼 곳과 꺾는 쪽의 먼 곳은 소개를 미룸(`settled`).
  - 화면에 "오늘 이야기는 모두 들었습니다"가 일찍 뜸 → 지나치지 않은 곳은 모두 목록에 둠.
  - 출발 전 경유지가 내비에서 사라짐 → 이미 안내 중이면 경로를 다시 요청하지 않음, 요청 뒤의 값만 확인으로 침.
  - 석촌동 정차 직전에 시작한 방이동 이야기가 정차 화면 뒤로 사라짐 → 정차하면 이야기를 멈추고 다시 출발할 때 들려줌.
