package io.github.hyunolike.ontime.app.ui

import androidx.compose.ui.graphics.Color
import io.github.hyunolike.ontime.slack.SlackLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OnTimeColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF161C24)
    val Text = Color(0xFFF2F5F8)
    val Muted = Color(0xFF8A96A3)
    val Comfort = Color(0xFF3DDC97)
    val Caution = Color(0xFFFFB020)
    val Danger = Color(0xFFFF5A5F)
}

fun SlackLevel.color(): Color = when (this) {
    SlackLevel.COMFORT -> OnTimeColors.Comfort
    SlackLevel.CAUTION -> OnTimeColors.Caution
    SlackLevel.DANGER -> OnTimeColors.Danger
}

fun SlackLevel.label(): String = when (this) {
    SlackLevel.COMFORT -> "여유 있음"
    SlackLevel.CAUTION -> "주의"
    SlackLevel.DANGER -> "위험"
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))

fun Instant.clock(): String = clockFormat.format(this)

fun slackText(minutes: Int): String = if (minutes >= 0) "+${minutes}분" else "${minutes}분"
