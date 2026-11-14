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
    // 마감을 넘기면 넘긴 만큼을 위험 색으로 그린다. 가장 중요한 상태가 가장 약하게 보이지 않게 한다.
    BudgetSegment("여유", kotlin.math.abs(slack.exact.minutesFloat()), OnTimeIcon.Clock, tone = level.tone()),
)

fun minutesText(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

/** 받침에 따라 "으로" 또는 "로"를 붙인다. */
fun String.withEuro(): String {
    val last = lastOrNull() ?: return this
    if (last !in '가'..'힣') return this + "으로"
    val jong = (last.code - 0xAC00) % 28
    return this + if (jong == 0 || jong == 8) "로" else "으로"
}

fun feeText(won: Int): String = "%,d원".format(won)
