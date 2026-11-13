# 0단계 최소 동작본 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 시나리오 A의 ③(사고 정체로 +28분이 +9분이 되고, 주차장을 바꿔 +21분이 되는 장면)을 재생 데이터로 보여 주는 서명된 release APK를 `apk/`에 올린다.

**Architecture:** 여유 계산과 상태 판정은 순수 Kotlin 모듈에 두고 테스트를 먼저 쓴다. 차량 입력은 인터페이스 뒤에 두고, 0단계에서는 시나리오를 재생하는 구현만 만든다. Pleos SDK는 쓰지 않는다.

**Tech Stack:** Kotlin 2.0.21, Gradle 8.14.3 wrapper, AGP 8.7.3, Jetpack Compose (BOM 2024.09.03), kotlinx-coroutines 1.9.0, JUnit 5

**Spec:** `docs/superpowers/specs/2026-11-13-ontime-design.md`

## Global Constraints

- minSdk 28, compileSdk 34, targetSdk 34. JDK 17로 빌드한다.
- 패키지와 applicationId는 `io.github.hyunolike.ontime`.
- 표시 값은 내림하고 상태 판정은 내림 전 값으로 한다.
- 상태 기준: 여유 +15분 이상, 주의 +5분 이상 +15분 미만, 위험 +5분 미만. 좋아지는 방향은 기준보다 2분 더 있어야 바뀐다.
- 안전 버퍼 3분. 대안은 여유를 3분 이상 늘릴 때만 제안하고, 같은 대안은 한 번만 제안한다.
- 재생 데이터를 쓰는 동안 화면에 "시연 재생"을 항상 표시한다.
- 주행 화면에는 버튼을 두지 않는다.
- 비밀값, 키스토어, 개인정보는 커밋하지 않는다.

## 설계와 달라지는 점

설계 5.1절은 대안 제안을 위험 상태에서 한다고 적었지만, 시나리오 ③은 +9분(주의)에서 제안한다. **주의나 위험으로 나빠질 때 제안**하는 것으로 맞추고 설계 문서도 고친다.

## 시나리오 수치 (수속 마감 15:55, 버퍼 3분)

| 주차장 | 주차 | 도보 | 남은 주행 |
|---|---|---|---|
| 장기주차장 | 6분 | 8분 | 14:10에 60분, 14:50에 20분, 14:51에 38분(사고 정체), 이후 1분에 1분씩 감소 |
| 단기주차장 | 6분 | 5분 | 장기주차장보다 항상 9분 짧다 |

14:50 여유 +28분, 14:51 여유 +9분, 단기주차장으로 바꾸면 +21분.

## Review Focus

- 이미 마감을 넘긴 경우: 음수로 표시하고 내림한다 (−0.5분은 −1분).
- 임계값 근처에서 오르내리는 입력: 안내가 반복되지 않는다.
- 대안이 없는 경우와 대안의 이득이 3분 미만인 경우: 제안하지 않는다.
- 제안을 거절한 경우: 경로를 유지하고 다시 제안하지 않는다.
- 주행 시간 입력이 2분 넘게 갱신되지 않은 경우: 오래됨으로 표시하고 제안을 멈춘다.

---

### Task 1: `core-slack` 여유 계산과 상태 판정

**Files:** `vehicle-app/` Gradle 뼈대, `vehicle-app/core-slack/src/{main,test}/kotlin/io/github/hyunolike/ontime/slack/`

**Produces:**
- `enum class Source { LIVE, CACHED, REPLAY }`, `data class Reading<T>(value, receivedAt: Instant, source)` + `isStale(now, maxAge)`
- `SlackInput(deadline, now, drive, parking, walk, stopover, buffer)`, `SlackResult(exact: Duration, displayMinutes: Int)`, `SlackCalculator.calculate(input)`
- `SlackLevel { COMFORT, CAUTION, DANGER }`, `SlackLevelTracker.update(slack): LevelChange?`
- `ParkingOption(id, name, parking, walk)`, `Alternative(option, gain, newSlack)`, `AlternativePicker.pick(...)`

- [ ] 실패하는 테스트 작성: 세 장면(+28, +9, +21), 내림, 음수, 상태 기준, 2분 완충, 대안 선택과 3분 미만 제외
- [ ] 구현 후 `./gradlew :core-slack:test` 통과
- [ ] 커밋

### Task 2: `platform-api`와 `platform-fake` 시나리오 재생

**Produces:**
- `Destination(id, name)`, `RouteStatus(destination, remainingDrive: Reading<Duration>)`
- `TimeSource.now: Flow<Instant>`, `NaviGateway { route; estimateRemainingDrive(to); changeDestination(to) }`, `VehicleGateway.driving`, `SpeechGateway { say(text); askYesNo(prompt): Boolean }`
- `Scenario`, `ScenarioA`, `ScenarioPlayer(scenario, speed)`: 위 인터페이스 넷을 구현

- [ ] 실패하는 테스트 작성: 보간, 시나리오 A의 세 장면 수치, 목적지 변경 후 남은 주행 시간, 도착하면 주행 종료
- [ ] 구현 후 `./gradlew :platform-fake:test` 통과
- [ ] 커밋

### Task 3: `core-session` 입력을 모아 화면 상태를 만든다

**Produces:** `TripPlan(deadline, departure, buffer, parkingOptions)`, `SessionState`, `OnTimeSession(scope, plan, time, navi, vehicle, speech)` + `state: StateFlow<SessionState>`

- [ ] 실패하는 테스트 작성: 시나리오 A를 끝까지 돌리면 제안이 한 번 나오고 수락 후 목적지가 단기주차장이 되며 여유가 +21분이다. 거절하면 경로 유지. 입력이 오래되면 제안하지 않는다
- [ ] 구현 후 `./gradlew :core-session:test` 통과
- [ ] 커밋

### Task 4: `app` 주행 화면과 정차 화면

- [ ] Android SDK 준비 (platform 34, build-tools)
- [ ] `MainActivity`, `OnTimeViewModel`, `DrivingScreen`, `StoppedScreen`, 테마
- [ ] `./gradlew :app:assembleFakeDebug` 통과, 에뮬레이터에서 실행해 화면 확인
- [ ] 커밋

### Task 5: release APK와 README

- [ ] 서명 설정(키스토어는 커밋하지 않음), `./gradlew :app:assembleFakeRelease`
- [ ] `apk/장현호_온타임_release.apk`로 복사, README의 실행 방법과 현재 범위 갱신
- [ ] 커밋과 푸시, 커밋 해시 보고
