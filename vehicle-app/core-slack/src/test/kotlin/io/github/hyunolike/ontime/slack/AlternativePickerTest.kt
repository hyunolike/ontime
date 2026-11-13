package io.github.hyunolike.ontime.slack

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class AlternativePickerTest {
    private val long = ParkingOption("long", "장기주차장", parking = 6.minutes, walk = 8.minutes)
    private val short = ParkingOption("short", "단기주차장", parking = 6.minutes, walk = 5.minutes)
    private val far = ParkingOption("far", "외곽주차장", parking = 4.minutes, walk = 20.minutes)

    private val current = SlackInput(
        deadline = Instant.parse("2026-12-03T06:55:00Z"),
        now = Instant.parse("2026-12-03T05:51:00Z"),
        drive = 38.minutes,
        parking = long.parking,
        walk = long.walk,
        buffer = 3.minutes,
    )

    @Test
    fun `여유를 가장 많이 늘리는 주차장 하나를 고른다`() {
        val picked = AlternativePicker.pick(
            current,
            candidates = listOf(Candidate(short, drive = 29.minutes), Candidate(far, drive = 30.minutes)),
        )
        assertEquals(short, picked?.option)
        assertEquals(12.minutes, picked?.gain)
        assertEquals(21, picked?.newSlack?.displayMinutes)
    }

    @Test
    fun `이득이 3분 미만이면 제안하지 않는다`() {
        val picked = AlternativePicker.pick(current, listOf(Candidate(short, drive = 39.minutes)))
        assertNull(picked) // 도보 3분 이득, 주행 1분 손해
    }

    @Test
    fun `이득이 정확히 3분이면 제안한다`() {
        val picked = AlternativePicker.pick(current, listOf(Candidate(short, drive = 38.minutes)))
        assertEquals(3.minutes, picked?.gain)
    }

    @Test
    fun `후보가 없으면 제안하지 않는다`() {
        assertNull(AlternativePicker.pick(current, emptyList()))
    }
}
