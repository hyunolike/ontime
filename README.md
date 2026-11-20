<div align="center">

<img src="docs/images/banner.svg" width="100%" alt="경주 고분 사이 길을 달리는 차와 앞쪽의 장소 표시">

<br>
<br>

<img src="docs/images/icon.svg" width="112" alt="길벗 앱 아이콘">

# 길벗

곧 지나갈 곳의 이야기를 몇 분 뒤 어느 쪽 창밖인지와 함께 미리 들려주는 차량용 드라이브 도슨트

### [브라우저에서 바로 체험하기 → gilbeot.hyunolike.dev](https://gilbeot.hyunolike.dev)

에뮬레이터 없이 판교에서 송파까지 달리며 이야기를 듣고, 들를 곳을 고르고, 방문 도장을 찍어 볼 수 있습니다.<br>
어느 쪽 창밖인지, 언제 말하고 언제 쉴지는 실제 앱 코드로 만든 기록이라 앱과 같습니다.

<br>

[![길벗 소개 영상](brag-output/brag.gif)](brag-output/brag.mp4)

이미지를 누르면 소리가 있는 26초 소개 영상이 열립니다.

</div>

<br>

## 소개

패키지 버스에는 이동 시간을 채워 주는 가이드가 있지만, 자차와 렌터카로 다니는 여행에는 없습니다. 길벗은 그 빈자리를 차가 채우게 하는 Pleos Connect 앱입니다.

내비가 아는 앞길을 보고 5분 뒤에 나올 곳을 미리 이야기하고, 그곳의 사진은 실제로 보일 쪽 창에 맞춰 띄웁니다. 회전이 가까우면 새 이야기를 꺼내지 않습니다. 들러 보고 싶어지면 가서 무엇을 볼 수 있는지, 도착이 얼마나 늦어지는지 확인한 뒤 버튼 한 번으로 경유지에 넣습니다. 그곳에 차를 세우면 방문 도장이 찍힙니다.

현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 출품작입니다.

<br>

## 화면

화면의 왼쪽과 오른쪽은 창밖의 왼쪽과 오른쪽과 같습니다.

| 곧 지나갈 곳을 미리 | 들를지 정할 때 필요한 것만 |
| :---: | :---: |
| <img src="docs/screenshots/2-story.png" alt="이야기"> | <img src="docs/screenshots/3-ask.png" alt="묻기"> |
| 오른쪽 창밖에 나올 곳은 사진도 오른쪽에 | 볼거리와 늦어지는 시간을 보고 고릅니다 |

| 회전 앞에서는 쉬기 | 회전을 지나면 이어서 |
| :---: | :---: |
| <img src="docs/screenshots/5-quiet.png" alt="쉼"> | <img src="docs/screenshots/6-left.png" alt="왼쪽"> |
| 사진을 내리고 묻던 질문도 닫습니다 | 이번에는 왼쪽 창밖입니다 |

| 출발 전 | 들른 뒤 |
| :---: | :---: |
| <img src="docs/screenshots/1-parked.png" alt="출발 전"> | <img src="docs/screenshots/7-stamp.png" alt="방문 도장"> |
| 오늘 길에 나올 곳과 들르는 데 드는 시간 | 차를 세운 곳에만 도장이 찍힙니다 |

| 다음 곳으로 바로 안내 | 내비와 나란히 띄웠을 때 |
| :---: | :---: |
| <img src="docs/screenshots/9-guide.png" alt="바로 안내"> | <img src="docs/screenshots/10-split.png" width="260" alt="좁은 화면"> |
| 도착한 곳에서 다음 장소를 고르면 다시 출발합니다 | 폭이 좁으면 사진을 위로 올립니다 |

주행 중에 누를 수 있는 것은 들를지 묻는 순간의 버튼 두 개뿐입니다. 답하지 않으면 그대로 지나갑니다.

<br>

## 차가 알고 있는 것

| 차가 아는 것 | 길벗에서 쓰는 곳 | 쓸 SDK |
| --- | --- | --- |
| 앞으로 갈 길 | 몇 분 뒤에 나올 곳을 미리 이야기 | NaviHelper |
| 다음 회전까지의 거리 | 회전 400m 앞에서는 새 이야기를 시작하지 않고 질문을 닫음 | NaviHelper |
| 진행 방향 | 사진을 보일 쪽에 띄움 | FusedLocation |
| 기어 | P로 바뀐 곳에만 방문 도장 | Vehicle |
| 소리 | 해설을 읽어 주고 말로 답을 받음 | TextToSpeech, SpeechToText |

<br>

## 지금 되는 것과 남은 것

빌드는 두 가지입니다.

- `fake` 빌드(제출한 APK): 경주 시내를 지나 불국사로 가는 길을 10배속으로 재생합니다. Pleos 이미지가 없는 에뮬레이터에서도 돕니다. 거리와 방향, 회전 위치, 들르는 데 드는 시간은 예시 값이고, 화면 위쪽에 예시 데이터라고 표시해 두었습니다.
- `pleos` 빌드: 판교에서 송파의 한성백제박물관으로 가는 길입니다. 경로 요청과 경유지 추가, 해제는 실제 내비에 보냅니다. 차의 위치와 남은 시간, 회전은 OpenStreetMap 경로를 따라 재생합니다. 에뮬레이터의 내비 차는 판교에 서 있고 위치 주입도 받지 않기 때문입니다. 화면 위쪽에 위치 재생이라고 표시합니다.

<img src="docs/screenshots/11-songpa-split.png" width="640" alt="길벗과 내비를 나란히 띄운 화면. 방이동 고분군을 왼쪽 창밖으로 소개하며 들를지 묻는다">

지금 되는 것

- 이야기를 꺼낼 때와 쉴 때를 고르는 판단, 버튼으로 답해 경유지 넣기, 정차 화면에서 고른 곳으로 바로 안내, 방문 도장
- 위치와 진행 방향으로 앞에 있는 곳과 왼쪽, 오른쪽을 가리는 계산. 회전을 앞두고 쪽이 바뀔 수 있는 곳은 회전을 지난 뒤 소개합니다.
- 남은 시간에 맞춘 해설 길이. 그곳이 1분 안이거나 다음 이야기가 곧 이어지면 한 문장으로 줄입니다.
- 오른쪽 창밖 장소는 "조수석 쪽"이라고 알려 동승자가 보게 합니다. 운전자에게는 고개를 돌리라고 하지 않습니다.
- 충전 대기 도슨트. 차를 세우고 충전하는 동안 걸어서 갈 만한 곳을 걷는 시간과 함께 보여 줍니다. 충전구와 배터리 잔량은 차량 SDK에서 읽습니다.
- 앱이 다시 시작돼도 들은 곳과 방문 도장을 이어 가고, "처음부터 다시"로 시연을 되돌립니다.
- `pleos` 빌드에서 경유지를 고르면 내비 화면에도 경유지가 들어가고(`addWaypoint`), 들른 뒤 다시 출발하면 지워집니다(`removeWaypoint`). 에뮬레이터에서 확인했습니다.
- 실제 위치 모드. 에뮬레이터에 넣은 GPS 좌표를 FusedLocation SDK로 받아 도슨트가 움직입니다. 경유지에 들르느라 길을 벗어나도 앞의 장소를 놓치지 않고, 꺾는 순간의 흔들리는 방향으로 쪽을 정하지 않습니다.
- 단위 테스트와 실제 송파 경로를 처음부터 끝까지 돌리는 통합 테스트 129개

키를 넣으면 켜지는 것(코드는 들어 있고, 키가 없어 실제 동작은 아직 확인하지 못했습니다)

- 음성 안내와 음성 답. Pleos Playground에 등록한 정보로 TTS와 STT를 서버 모드로 씁니다. 키가 없으면 화면 자막과 버튼으로 동작합니다.
- 한국관광공사 TourAPI. 경로를 따라 주변 관광지를 최대 여섯 곳 더하고, 응답을 기기에 저장해 통신이 끊겨도 다시 씁니다.

남은 것

- 실차 시험. 실제 위치 모드는 에뮬레이터에서 확인했고, 실차에서는 회전 방향을 내비 회전 목록에서 받도록 연결해야 합니다.
- 들르는 데 드는 시간. 지금은 예시 값입니다. SDK가 "그곳에서 목적지까지"의 시간을 주지 않아 우회 시간을 정확히 구할 수 없습니다.
- 다국어 해설. 화면 문구부터 여러 언어로 옮겨야 합니다.

설계와 확인 기록은 [live-docent](docs/features/live-docent/spec.md)와 [docent-plus](docs/features/docent-plus/spec.md)에, 사업성 초안은 [business](docs/business.md)에, SDK 호출 결과는 [SDK 확인 기록](docs/pleos-sdk-notes.md)에 있습니다. 송파 경로 데이터는 © OpenStreetMap 기여자(ODbL)입니다.

<br>

## 실행 방법

에뮬레이터를 띄운 뒤 APK를 설치하고 실행합니다.

```bash
adb install -r apk/장현호_길벗_release.apk
adb shell am start -n io.github.hyunolike.ontime/io.github.hyunolike.ontime.app.MainActivity
```

앱이 열리면 경주 코스 시연 시작을 누릅니다.

1. 5초쯤 오른쪽 창밖에 나올 대릉원 이야기가 사진과 함께 나옵니다.
2. 9초쯤 들러 볼지 묻습니다. 여기서 들르기를 눌러 주세요. 10초쯤 기다립니다.
3. 19초쯤 우회전 앞에서 잠시 쉽니다. 그때까지 누르지 않았다면 질문이 닫히고 대릉원은 지나갑니다.
4. 23초쯤 회전을 지나 왼쪽 창밖의 첨성대 이야기가 이어집니다.
5. 30초쯤 대릉원에 도착해 방문 도장이 찍힙니다.

도착 화면에서 첨성대의 여기로 안내를 누르면 첨성대로, 불국사로 계속 안내를 누르면 목적지로 다시 출발합니다. 아무것도 누르지 않으면 두 곳을 지나 불국사까지 3분쯤 재생됩니다.

패키지 이름과 저장소 이름에 남은 `ontime`은 초기 프로토타입의 이름입니다.

### 소스에서 빌드

JDK 17과 Android SDK 플랫폼 34가 필요합니다.

```bash
cd vehicle-app
./gradlew test                     # 단위 테스트
./gradlew :app:assembleFakeDebug   # 예시 데이터 재생 빌드
./gradlew :app:assemblePleosDebug  # SDK 확인 빌드, Pleos Connect 에뮬레이터에서만 동작
```

release 빌드는 `vehicle-app/keystore.properties`가 있을 때만 서명됩니다. 키스토어는 저장소에 넣지 않았습니다.

### Pleos Connect 에뮬레이터를 띄울 때

명령줄로 띄우면 DNS를 잡지 못해 내비가 응답하지 않을 때가 있고, 기본 시간대가 미국 서부입니다.

```bash
~/Library/Android/sdk/emulator/emulator -avd <Pleos 기기 이름> -no-snapshot \
  -dns-server 8.8.8.8,1.1.1.1 -timezone Asia/Seoul
```

앱 영역 오른쪽 끝의 손잡이를 왼쪽으로 끌면 화면이 반으로 나뉘어 내비와 나란히 볼 수 있습니다.

`pleos` 빌드(송파 경로)는 `./gradlew :app:installPleosDebug`로 설치합니다. 화면을 먼저 나눠 두면 출발할 때 내비가 앱을 가리지 않습니다. "송파 코스 출발"을 누르면 내비에 경로를 요청하고, 정차 화면에서 "여기로 안내"를 누르거나 주행 중 "들르기"를 누르면 내비에 경유지가 들어갑니다. SDK 호출은 `adb logcat -s OnTimePleos`로 볼 수 있습니다.

위치를 재생하지 않고 실제 위치 SDK(FusedLocation)로 움직이려면 실제 위치 모드로 바꾸고 좌표를 흘려 넣는 스크립트를 띄웁니다. 앱은 실차와 같은 코드 경로로 움직이고, 경유지를 넣으면 스크립트가 그 장소에 들어가 섭니다. 내비 화면의 차는 에뮬레이터 GPS를 받지 않아 그대로 서 있습니다.

```bash
adb shell am broadcast -a io.github.hyunolike.ontime.POC --es poc location-live   # 되돌리기: location-replay
python3 tools/geo-drive.py      # 출발점에 3초 선 뒤 달린다. 그다음 앱에서 "송파 코스 출발"
```

충전 대기 화면은 충전구를 열면 나옵니다. 에뮬레이터에서는 앱이 떠 있는 동안 아래 명령으로 충전 시험 값을 켜고 끌 수 있습니다.

```bash
adb shell am broadcast -a io.github.hyunolike.ontime.POC --es poc charge
```

음성과 TourAPI는 `vehicle-app/local.properties`에 키를 넣고 다시 빌드하면 켜집니다. 키가 들어간 APK는 로컬 시연에만 쓰고 공개하지 않습니다.

```properties
pleos.tts.clientId=...
pleos.tts.clientSecret=...
tourapi.key=...
```

<br>

## 테스트 환경 및 필요한 설정

- minSdk 28, targetSdk 34
- 제출 APK는 따로 설정할 것이 없고 API 키나 네트워크 연결도 필요하지 않습니다.

## 에뮬레이터 버전과 화면 규격

- Pleos Connect Emulator v2.1.0 (Android API 34), 기기 프로필 `Pleos_Connect_v2_0`
- 화면은 2560x1440 px, 160 dpi입니다. 앱 영역은 1682x1210 px이고, 내비와 나란히 띄우면 841x1210 px입니다.
- 제출 APK를 이 에뮬레이터에 설치해 위 순서가 끝까지 도는 것을 확인했습니다.

## APK 위치

[apk/장현호_길벗_release.apk](apk/장현호_길벗_release.apk)

<br>

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어와 화면 | Kotlin 2.0.21, Jetpack Compose (BOM 2024.09.03) |
| 비동기 | Kotlin Coroutines 1.9.0 |
| 빌드 | Gradle 8.14.3, Android Gradle Plugin 8.13.2, JDK 17 |
| 테스트 | kotlin.test, kotlinx-coroutines-test |
| 차량 | Pleos Connect SDK |

| Pleos Connect SDK | 버전 | 쓰는 곳 | 확인한 범위 |
| --- | --- | --- | --- |
| NaviHelper | 3.0.5 | 남은 거리와 시간, 경유지 추가, 회전 안내 | 호출과 응답 확인, 도슨트 화면 연결 전 |
| Vehicle | 2.3.2 | 기어 P로 방문 확인, 주행 중 화면 제한 | 기어 조회 확인 |
| TextToSpeech | 3.3.1 | 해설 읽기 | 기기 내 엔진 오류, 서버 모드 연결 필요 |
| SpeechToText | 3.3.1 | 들를지 묻는 말에 대한 답 듣기 | 아직 확인하지 않음 |
| FusedLocation | 1.1.3 | 진행 방향으로 좌우 판단 | 서비스 연결 확인, 위치 갱신은 아직 |

<br>

## 구조

화면과 판단 로직은 차량 입력을 인터페이스로만 받습니다. 그래서 같은 화면을 예시 주행으로도, 실제 SDK로도 돌릴 수 있습니다.

```mermaid
flowchart LR
  app[app<br>화면] --> session[core-session<br>언제 말하고 언제 쉴지]
  app --> design[design-system<br>색, 글자, 구성 요소]
  session --> api[platform-api<br>차량, 내비, 음성 인터페이스]
  fake[platform-fake<br>예시 주행 재생] --> api
  pleos[platform-pleos<br>Pleos Connect SDK] --> api
```

| 경로 | 내용 |
| --- | --- |
| `vehicle-app/` | 차량 앱 |
| `apk/` | 제출용 release APK |
| `brag-output/` | 소개 영상과 썸네일, 효과음 출처 |
| `docs/` | SDK 확인 기록, 디자인 시스템, 공모전 규정 요약 |

`core-slack` 모듈과 `core-session`의 `session` 패키지는 초기 프로토타입의 코드이고, `pleos` 빌드의 SDK 확인 화면이 씁니다.

<br>

## 문서

- [SDK 확인 기록](docs/pleos-sdk-notes.md)
- [디자인 시스템](docs/design-system.md)
- [공모전 규정 요약](docs/contest-rules.md)

<br>

## 만든 사람

| <img src="https://github.com/hyunolike.png?size=120" width="100" alt="hyunolike"> |
| :---: |
| [장현호](https://github.com/hyunolike) |
