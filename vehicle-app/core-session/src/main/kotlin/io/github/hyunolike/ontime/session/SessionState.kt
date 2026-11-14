package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.slack.ParkingOption
import io.github.hyunolike.ontime.slack.SlackLevel
import io.github.hyunolike.ontime.slack.SlackResult
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 갈 수 있는 주차장 하나. 내비 목적지와 거기서 드는 주차, 도보 시간을 묶는다.
 * [dailyFee]는 소형차 하루 최대 요금(원)이다. 모르면 null.
 */
data class ParkingChoice(
    val destination: Destination,
    val parking: Duration,
    val walk: Duration,
    val dailyFee: Int? = null,
) {
    val option: ParkingOption get() = ParkingOption(destination.id, destination.name, parking, walk)
}

/** 등록된 일정 하나. */
data class TripPlan(
    val title: String,
    /** 놓치면 안 되는 시각. 출발 시각이 아니라 수속 마감이다. */
    val deadline: Instant,
    val buffer: Duration = 3.minutes,
    val parkingChoices: List<ParkingChoice>,
)

enum class SuggestionStatus { PROPOSED, ACCEPTED, DECLINED, FAILED }

data class Suggestion(
    val text: String,
    val status: SuggestionStatus,
    val result: String? = null,
    /** 제안할 때의 여유(분)와 바꿨을 때 예상 여유(분). */
    val currentMinutes: Int? = null,
    val expectedMinutes: Int? = null,
)

/** 주차장 하나를 골랐을 때의 예상 여유. 정차 중 비교 화면에 쓴다. */
data class ParkingPreview(
    val destination: Destination,
    val slack: SlackResult,
    val level: SlackLevel,
    val drive: Duration,
    val walk: Duration,
    val current: Boolean,
    val dailyFee: Int? = null,
)

data class SessionState(
    val now: Instant,
    val driving: Boolean,
    val destinationName: String,
    val slack: SlackResult,
    val level: SlackLevel,
    val untilDeadline: Duration,
    val drive: Duration,
    val parking: Duration,
    val walk: Duration,
    val buffer: Duration,
    /** 주행 시간 입력의 출처. REPLAY면 화면에 표시한다. */
    val source: Source,
    /** 주행 시간 입력이 오래됐다. 숫자를 믿기 어렵고 제안을 멈춘다. */
    val stale: Boolean,
    val suggestion: Suggestion? = null,
)
