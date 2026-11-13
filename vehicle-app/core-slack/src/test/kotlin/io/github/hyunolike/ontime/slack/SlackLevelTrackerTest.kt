package io.github.hyunolike.ontime.slack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SlackLevelTrackerTest {
    @Test
    fun `기준값으로 상태를 나눈다`() {
        assertEquals(SlackLevel.COMFORT, SlackThresholds().levelFor(15.minutes))
        assertEquals(SlackLevel.CAUTION, SlackThresholds().levelFor(15.minutes - 1.seconds))
        assertEquals(SlackLevel.CAUTION, SlackThresholds().levelFor(5.minutes))
        assertEquals(SlackLevel.DANGER, SlackThresholds().levelFor(5.minutes - 1.seconds))
        assertEquals(SlackLevel.DANGER, SlackThresholds().levelFor((-3).minutes))
    }

    @Test
    fun `첫 값은 이전 상태 없이 변화로 알린다`() {
        val tracker = SlackLevelTracker()
        assertEquals(LevelChange(null, SlackLevel.COMFORT), tracker.update(28.minutes))
        assertNull(tracker.update(27.minutes))
    }

    @Test
    fun `나빠질 때는 바로 바뀐다`() {
        val tracker = SlackLevelTracker()
        tracker.update(28.minutes)
        assertEquals(LevelChange(SlackLevel.COMFORT, SlackLevel.CAUTION), tracker.update(9.minutes))
        assertEquals(LevelChange(SlackLevel.CAUTION, SlackLevel.DANGER), tracker.update(4.minutes))
    }

    @Test
    fun `좋아질 때는 기준보다 2분 더 있어야 바뀐다`() {
        val tracker = SlackLevelTracker()
        tracker.update(14.minutes)
        assertNull(tracker.update(15.minutes))
        assertNull(tracker.update(16.minutes + 59.seconds))
        assertEquals(LevelChange(SlackLevel.CAUTION, SlackLevel.COMFORT), tracker.update(17.minutes))
    }

    @Test
    fun `기준 근처에서 오르내려도 안내가 반복되지 않는다`() {
        val tracker = SlackLevelTracker()
        tracker.update(16.minutes)
        val changes = listOf(14, 15, 14, 16, 14, 15).mapNotNull { tracker.update(it.minutes) }
        assertEquals(listOf(LevelChange(SlackLevel.COMFORT, SlackLevel.CAUTION)), changes)
    }

    @Test
    fun `위험에서 여유로 한 번에 넘어갈 수 있다`() {
        val tracker = SlackLevelTracker()
        tracker.update(2.minutes)
        assertNull(tracker.update(6.minutes))
        assertEquals(LevelChange(SlackLevel.DANGER, SlackLevel.COMFORT), tracker.update(21.minutes))
        assertEquals(SlackLevel.COMFORT, tracker.level)
    }
}
