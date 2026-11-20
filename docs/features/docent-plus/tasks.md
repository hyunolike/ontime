# 도슨트 고도화 (docent-plus) 작업

- 명세: [spec.md](spec.md), 설계: [design.md](design.md), 검토: [reviews/](reviews/)
- 현재 단계: 구현 완료. 키가 필요한 두 항목(음성, TourAPI)은 키를 받은 뒤 에뮬레이터 확인

| ID | 작업 | 검증 | 상태 |
|---|---|---|---|
| T1 | E2 좌석 안내(음성 문장, 화면 "조수석에서 잘 보여요") | `DocentSessionTest`, 에뮬레이터 | 완료 |
| T2 | E1 이야기 길이(`pickStory`), 소수점 안전한 첫 문장 | `StoryLengthTest` 6개 | 완료 |
| T3 | 에셋(배터리, 조수석, 다시 시작, 음성, 사진 대체 그림) | Codex 제작, 빌드, 비교 시트 | 완료. 조수석 아이콘은 읽기 어려워 쓰지 않음 |
| T4 | E3 충전 대기: `ChargeState`, `ReplayVehicle`, `PleosVehicle`, `nearbyOnFoot`, 몽촌토성, 카드 | `OnFootTest`, `ReplayVehicleTest`, `SongpaDriveTest`, 에뮬레이터 | 완료 |
| T5 | 출처: 칩은 경로 출처, 관광공사 해설 출처 표시 | 단위 테스트 | 완료 |
| T6 | E6 이어 가기, 처음부터 다시 | `SongpaDriveTest`, 에뮬레이터 | 완료 |
| T7 | E5 음성: `listenYesNo`, 질문 제한 시간, 전용 서버 인스턴스, BuildConfig, `voice` StateFlow | `DocentSessionTest` | 구현 완료, 키 없어 실제 소리 미확인 |
| T8 | E4 TourAPI: `platform-tour`, 캐시, 목록 교체, 도장 칸 4칸 | `TourCatalogTest` 6개 | 구현 완료, 키 없어 실제 조회 미확인 |
| T9 | E7 재탐색 로그, 문서 | 문서 | 완료 |
| T10 | E8 사업성 초안 | `docs/business.md` | 완료 |
| T11 | 전체 테스트, 두 빌드, 에뮬레이터 확인, README | 112개 통과 | 완료 |

## 에뮬레이터 확인 (2026-11-20)

- 사진 없는 서울공항에 대체 그림이 나오고, 오른쪽 장소 이야기에 "조수석에서 잘 보여요"가 붙는다.
- 정차 화면 목록과 "다음 이야기"에 직선 거리가 나온다.
- 앱을 다시 띄우면 들은 곳이 복원되고 "처음부터 다시"가 나온다. 누르면 들은 곳이 지워지고 출발 버튼이 돌아온다.
- 목적지에서 `poc charge`로 충전을 켜면 "걸어서 둘러볼 곳" 카드에 몽촌토성(걸어서 14분), 방이동 고분군(16분), 배터리 100%(실제 SDK 값), "충전 시험" 칩이 나온다.
- `am start --es poc`가 분할 화면의 앱에 전달되지 않아 브로드캐스트로 바꿨다.

## 키를 받으면 할 일

- `vehicle-app/local.properties`에 `pleos.tts.clientId`, `pleos.tts.clientSecret`, `tourapi.key`를 넣고 `pleos` 빌드를 다시 설치한다.
- 음성: `OnTimePleos` 로그에 `tts.onConnected`, `stt.onConnected`가 오는지, 해설이 소리로 나오는지, "들를래"로 답이 되는지 확인한다.
- TourAPI: `<- TourCatalog n곳` 로그와 응답 필드 이름(`response.body.items.item`, `mapx`, `mapy`, `overview`)이 가정과 맞는지 확인한다. 다르면 `TourApiClient`와 `TourCatalogTest`의 고정 JSON을 함께 고친다.
- 키가 들어간 APK는 로컬 시연에만 쓴다. 제출하거나 공개하는 APK는 키 없이 빌드한다.
