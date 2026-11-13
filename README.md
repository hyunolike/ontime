# 온타임 (OnTime)

늦으면 안 되는 시각에서 거꾸로 계산해, 주행 중에는 "여유 +9분"이라는 숫자 하나로 제시간 도착 여부를 알려 주는 차량용 정시 도착 도우미입니다. 늦어질 것 같으면 더 가까운 주차장이나 충전 경유지를 말로 제안하고, 운전자가 "그렇게 해 줘"라고 답하면 내비 경로를 바꿉니다.

현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 출품작입니다.

> 현재 상태: 설계 단계입니다. 아래 항목 중 "작성 예정"은 구현이 진행되면서 채웁니다.

## 저장소 구성

| 경로 | 내용 |
|---|---|
| `vehicle-app/` | Pleos Connect 차량 앱 (Kotlin, Jetpack Compose) |
| `server/` | 운항 정보와 주차장 현황을 모아 차량 앱에 전달하는 서버 (Spring Boot) |
| `mobile-web/` | 하차 후 스마트폰에서 이어 보는 화면 |
| `apk/` | 제출용 release APK |
| `docs/` | 설계 문서, SDK 확인 기록, 공모전 규정 요약 |

## 실행 방법

작성 예정. 차량 앱 빌드, 서버 실행, 에뮬레이터 설치 순서로 정리합니다.

## 테스트 환경 및 필요한 설정

- Android Studio와 Pleos Connect 시스템 이미지 (Android API 34)
- JDK 17 (차량 앱), JDK 21 (서버)
- 공공데이터포털 서비스 키: 저장소에 넣지 않습니다. `.env.example`을 복사해 직접 채웁니다. 키가 없어도 저장된 응답으로 재생하는 모드로 실행할 수 있게 만듭니다.

## Pleos Connect 에뮬레이터 버전과 화면 규격

- 에뮬레이터: Pleos Connect Emulator v2.1.0 (Android API 34)
- 화면 규격: 작성 예정. 에뮬레이터에서 실제 dp, DPI, px 값을 확인한 뒤 적습니다.

## APK 위치

`apk/장현호_온타임_release.apk` (작성 예정)

## 사용하는 Pleos Connect SDK

| SDK | 버전 | 용도 |
|---|---|---|
| NaviHelper | 3.0.5 | 목적지 재설정, 경유지 추가, 경로 이벤트 수신 |
| Vehicle | 2.3.2 | 주행가능거리, 도어와 주행 상태 |
| SpeechToText | 3.3.1 | 일정 등록과 대안 수락 |
| TextToSpeech | 3.3.1 | 상태가 바뀔 때 음성 안내 |
| FusedLocation | 1.1.3 | 현재 위치 |

## 문서

- [설계 문서](docs/superpowers/specs/2026-11-13-ontime-design.md)
- [Pleos SDK 확인 기록](docs/pleos-sdk-notes.md)
- [공모전 규정 요약](docs/contest-rules.md)
