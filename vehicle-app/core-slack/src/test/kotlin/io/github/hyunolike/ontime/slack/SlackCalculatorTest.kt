package io.github.hyunolike.ontime.slack

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SlackCalculatorTest {
    private val deadline = Instant.parse("2026-12-03T06:55:00Z") // 15:55 KST

    private fun input(
        now: String,
        drive: Duration,
        parking: Duration = 6.minutes,
        walk: Duration = 8.minutes,
        stopover: Duration = Duration.ZERO,
    ) = SlackInput(
        deadline = deadline,
        now = Instant.parse(now),
        drive = drive,
        parking = parking,
        walk = walk,
        stopover = stopover,
        buffer = 3.minutes,
    )

    @Test
    fun `정체 전에는 여유가 28분이다`() {
        val result = SlackCalculator.calculate(input(now = "2026-12-03T05:50:00Z", drive = 20.minutes))
        assertEquals(28.minutes, result.exact)
        assertEquals(28, result.displayMinutes)
    }

    @Test
    fun `사고 정체로 주행이 38분 남으면 여유는 9분이다`() {
        val result = SlackCalculator.calculate(input(now = "2026-12-03T05:51:00Z", drive = 38.minutes))
        assertEquals(9, result.displayMinutes)
    }

    @Test
    fun `단기주차장으로 바꾸면 여유는 21분이다`() {
        val result = SlackCalculator.calculate(
            input(now = "2026-12-03T05:51:00Z", drive = 29.minutes, walk = 5.minutes),
        )
        assertEquals(21, result.displayMinutes)
    }

    @Test
    fun `탑승 마감 기준이면 공항 안에서 걸리는 시간도 뺀다`() {
        // 탑승 마감 16:25, 공항 안 30분(수속 8, 출국장 14, 탑승구 이동 8)이면 수속 마감 15:55 기준과 같은 여유가 나온다.
        val result = SlackCalculator.calculate(
            SlackInput(
                deadline = Instant.parse("2026-12-03T07:25:00Z"),
                now = Instant.parse("2026-12-03T05:51:00Z"),
                drive = 38.minutes,
                parking = 6.minutes,
                walk = 8.minutes,
                airport = 30.minutes,
                buffer = 3.minutes,
            ),
        )
        assertEquals(9, result.displayMinutes)
    }

    @Test
    fun `경유지 정차 시간도 뺀다`() {
        val result = SlackCalculator.calculate(
            input(now = "2026-12-03T05:51:00Z", drive = 29.minutes, walk = 5.minutes, stopover = 10.minutes),
        )
        assertEquals(11, result.displayMinutes)
    }

    @Test
    fun `표시 값은 낙관적으로 올리지 않고 내린다`() {
        val result = SlackCalculator.calculate(input(now = "2026-12-03T05:51:10Z", drive = 38.minutes))
        assertEquals(8.minutes + 50.seconds, result.exact)
        assertEquals(8, result.displayMinutes)
    }

    @Test
    fun `마감을 넘기면 음수이고 음수도 내린다`() {
        val result = SlackCalculator.calculate(input(now = "2026-12-03T06:00:30Z", drive = 38.minutes))
        assertEquals(-(30.seconds), result.exact)
        assertEquals(-1, result.displayMinutes)
    }
}
