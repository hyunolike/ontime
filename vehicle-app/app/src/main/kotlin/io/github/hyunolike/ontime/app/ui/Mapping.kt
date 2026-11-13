package io.github.hyunolike.ontime.app.ui

import io.github.hyunolike.ontime.design.component.BudgetSegment
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.session.SessionState
import io.github.hyunolike.ontime.slack.SlackLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

fun SlackLevel.tone(): Tone = when (this) {
    SlackLevel.COMFORT -> Tone.Positive
    SlackLevel.CAUTION -> Tone.Caution
    SlackLevel.DANGER -> Tone.Critical
}

fun SlackLevel.label(): String = when (this) {
    SlackLevel.COMFORT -> "여유 있음"
    SlackLevel.CAUTION -> "주의"
    SlackLevel.DANGER -> "위험"
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))

fun Instant.clock(): String = clockFormat.format(this)

private fun Duration.minutesFloat(): Float = inWholeSeconds / 60f

/** 마감까지 남은 시간을 쓰는 순서대로 나눈다. 마지막이 남는 여유다. */
fun SessionState.budgetSegments(): List<BudgetSegment> = listOf(
    BudgetSegment("주행", drive.minutesFloat(), OnTimeIcon.Car),
    BudgetSegment("주차", parking.minutesFloat(), OnTimeIcon.Parking),
    BudgetSegment("도보", walk.minutesFloat(), OnTimeIcon.Walk),
    BudgetSegment("안전 버퍼", buffer.minutesFloat(), OnTimeIcon.Buffer),
    BudgetSegment("여유", slack.exact.minutesFloat().coerceAtLeast(0f), OnTimeIcon.Clock, tone = level.tone()),
)

fun minutesText(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}
