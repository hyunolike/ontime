package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.slack.ParkingOption
import io.github.hyunolike.ontime.slack.SlackLevel
import io.github.hyunolike.ontime.slack.SlackResult
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 갈 수 있는 주차장 하나. 내비 목적지와 거기서 드는 주차, 도보 시간을 묶는다. */
data class ParkingChoice(val destination: Destination, val parking: Duration, val walk: Duration) {
    val option: ParkingOption get() = ParkingOption(destination.id, destination.name, parking, walk)
}

/**
 * 공항 안에서 거치는 단계 하나. 예: 수속 카운터, 출국장(보안검색), 탑승구까지 이동.
 * [estimated]가 true면 실제 데이터가 아니라 추정이나 예시 값이다. 화면에 그렇게 표시한다.
 */
data class AirportStep(val label: String, val duration: Duration, val estimated: Boolean = true)

/** 등록된 일정 하나. */
data class TripPlan(
    val title: String,
    /** 놓치면 안 되는 시각. 출발 시각이 아니라 탑승 마감이나 수속 마감이다. */
    val deadline: Instant,
    val buffer: Duration = 3.minutes,
    val parkingChoices: List<ParkingChoice>,
    /** 화면에 보여 줄 마감의 이름. */
    val deadlineLabel: String = "수속 마감",
    /** 터미널에 들어선 뒤 마감 지점까지 거치는 단계. 비어 있으면 주차와 도보까지만 계산한다. */
    val airportSteps: List<AirportStep> = emptyList(),
) {
    val airport: Duration get() = airportSteps.fold(Duration.ZERO) { sum, step -> sum + step.duration }
}

enum class SuggestionStatus { PROPOSED, ACCEPTED, DECLINED, FAILED }

data class Suggestion(
    val text: String,
    val status: SuggestionStatus,
    val result: String? = null,
    /** 제안할 때의 여유(분)와 바꿨을 때 예상 여유(분). */
    val currentMinutes: Int? = null,
    val expectedMinutes: Int? = null,
    /** 분이 아닌 상태를 비교할 때 쓴다. 예: "붐빔"에서 "여유"로. */
    val currentLabel: String? = null,
    val expectedLabel: String? = null,
)

/** 주차장 하나를 골랐을 때의 예상 여유. 정차 중 비교 화면에 쓴다. */
data class ParkingPreview(
    val destination: Destination,
    val slack: SlackResult,
    val level: SlackLevel,
    val drive: Duration,
    val walk: Duration,
    val current: Boolean,
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
    /** 공항 안에서 걸리는 시간. */
    val airport: Duration,
    val buffer: Duration,
    /** 주행 시간 입력의 출처. REPLAY면 화면에 표시한다. */
    val source: Source,
    /** 주행 시간 입력이 오래됐다. 숫자를 믿기 어렵고 제안을 멈춘다. */
    val stale: Boolean,
    val suggestion: Suggestion? = null,
)
