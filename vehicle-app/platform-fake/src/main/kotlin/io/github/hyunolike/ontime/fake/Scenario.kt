package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.Destination
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 시나리오 시작 후 [offset] 시점에 남은 주행 시간이 [remaining]이다. */
data class Keyframe(val offset: Duration, val remaining: Duration)

data class ScenarioRoute(val destination: Destination, val keyframes: List<Keyframe>) {
    init {
        require(keyframes.isNotEmpty()) { "keyframes가 비어 있다" }
        require(keyframes.zipWithNext().all { (a, b) -> a.offset < b.offset }) { "offset은 증가해야 한다" }
    }

    /** 키프레임 사이는 초 단위로 직선 보간한다. 범위를 벗어나면 양 끝 값을 쓴다. */
    fun remainingAt(offset: Duration): Duration {
        if (offset <= keyframes.first().offset) return keyframes.first().remaining
        if (offset >= keyframes.last().offset) return keyframes.last().remaining
        val index = keyframes.indexOfLast { it.offset <= offset }
        val a = keyframes[index]
        val b = keyframes[index + 1]
        val span = (b.offset - a.offset).inWholeSeconds
        val passed = (offset - a.offset).inWholeSeconds
        val delta = (b.remaining - a.remaining).inWholeSeconds
        return a.remaining + (delta * passed / span).seconds
    }
}

data class Scenario(
    /** offset 0의 실제 시각. */
    val start: Instant,
    /** 재생을 시작하는 지점. */
    val playFrom: Duration,
    val routes: List<ScenarioRoute>,
    val initialDestinationId: String,
) {
    fun route(destinationId: String): ScenarioRoute? = routes.find { it.destination.id == destinationId }
}

/**
 * 시나리오 A의 ③: 공항 가는 고속도로에서 사고 정체를 만난다.
 * 수속 마감 15:55, 버퍼 3분 기준으로 14:50에 여유 +28분, 14:51에 +9분,
 * 단기주차장으로 바꾸면 +21분이 나오도록 맞춘 설명용 수치다. 실측값이 아니다.
 */
object ScenarioA {
    // 좌표는 지도에서 읽은 대략값이다. 실제 내비 목적지로 쓰기 전에 주차장 입구 좌표로 확인한다.
    val LONG_TERM = Destination("icn-t1-long", "장기주차장", 37.4452, 126.4490, "인천 중구 공항로 272")
    val SHORT_TERM = Destination("icn-t1-short", "단기주차장", 37.4478, 126.4525, "인천 중구 공항로 272")

    val scenario = Scenario(
        start = Instant.parse("2026-12-03T05:10:00Z"), // 14:10 KST 출발
        playFrom = 38.minutes, // 14:48부터 재생
        routes = listOf(
            ScenarioRoute(
                LONG_TERM,
                listOf(
                    Keyframe(0.minutes, 60.minutes),
                    Keyframe(40.minutes, 20.minutes),
                    // 사고 정체로 도착 예정이 한 번에 19분 밀린다. 내비의 재탐색처럼 값이 뛰어야
                    // 제안이 나오는 순간의 여유와 화면의 숫자가 같다.
                    Keyframe(40.minutes + 5.seconds, 38.minutes + 55.seconds),
                    Keyframe(79.minutes, 0.minutes),
                ),
            ),
            ScenarioRoute(
                SHORT_TERM,
                listOf(
                    Keyframe(0.minutes, 51.minutes),
                    Keyframe(40.minutes, 11.minutes),
                    Keyframe(40.minutes + 5.seconds, 29.minutes + 55.seconds),
                    Keyframe(70.minutes, 0.minutes),
                ),
            ),
        ),
        initialDestinationId = LONG_TERM.id,
    )
}
