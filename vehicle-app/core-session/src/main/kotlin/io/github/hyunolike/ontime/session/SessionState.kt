package io.github.hyunolike.ontime.session

import io.github.hyunolike.ontime.slack.ParkingOption
import io.github.hyunolike.ontime.slack.SlackLevel
import io.github.hyunolike.ontime.slack.SlackResult
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 등록된 일정 하나. */
data class TripPlan(
    val title: String,
    /** 놓치면 안 되는 시각. 출발 시각이 아니라 수속 마감이다. */
    val deadline: Instant,
    val buffer: Duration = 3.minutes,
    /** 갈 수 있는 주차장. id는 내비 목적지 id와 같다. */
    val parkingOptions: List<ParkingOption>,
)

enum class SuggestionStatus { PROPOSED, ACCEPTED, DECLINED, FAILED }

data class Suggestion(val text: String, val status: SuggestionStatus, val result: String? = null)

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
