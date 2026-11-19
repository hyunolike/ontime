package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AheadTrackerTest {
    // 위도 1도는 약 111km, 이 위도에서 경도 1도는 약 88km다.
    private val lat0 = 37.45
    private val lon0 = 127.10
    private fun north(m: Double) = m / 111_195.0
    private fun east(m: Double) = m / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0)))

    private fun poi(id: String, dNorth: Double, dEast: Double) =
        Poi(Destination(id, id, lat0 + north(dNorth), lon0 + east(dEast)), "", canStop = true, source = Source.REPLAY)

    private fun fix(dNorth: Double = 0.0, dEast: Double = 0.0, heading: Double? = 0.0, speed: Double = 10.0) =
        Fix(lat0 + north(dNorth), lon0 + east(dEast), heading, speed)

    private fun List<PoiAhead>.ids() = map { it.poi.destination.id }
    private fun List<PoiAhead>.settledIds() = filter { it.settled }.ids()

    private val ne = poi("ne", 700.0, 700.0)
    private val nw = poi("nw", 700.0, -700.0)
    private val south = poi("south", -1_000.0, 0.0)
    private val far = poi("far", 6_000.0, 2_000.0)

    @Test
    fun `북쪽으로 가면 북동쪽은 오른쪽, 북서쪽은 왼쪽으로 소개할 수 있고 뒤쪽과 5km 밖은 아직 소개하지 않는다`() {
        val ahead = AheadTracker(listOf(ne, nw, south, far)).update(fix(heading = 0.0))
        assertEquals(listOf("ne", "nw"), ahead.settledIds().sorted())
        assertEquals(Side.RIGHT, ahead.single { it.poi == ne }.side)
        assertEquals(Side.LEFT, ahead.single { it.poi == nw }.side)
        val distance = ahead.single { it.poi == ne }.distanceMeters
        assertTrue(distance in 985..995, "distance=$distance")
        // 아직 지나지 않았으므로 목록에는 남는다.
        assertTrue(ahead.ids().containsAll(listOf("south", "far")))
    }

    @Test
    fun `서쪽으로 가면 북쪽 장소가 오른쪽이다`() {
        val ahead = AheadTracker(listOf(poi("n", 1_000.0, -200.0))).update(fix(heading = 270.0, dEast = 1_000.0))
        assertEquals(Side.RIGHT, ahead.single().side)
        assertTrue(ahead.single().settled)
    }

    @Test
    fun `멀리 거의 정면에 있는 곳은 어느 쪽으로 지날지 몰라 아직 소개하지 않는다`() {
        val tracker = AheadTracker(listOf(poi("a", 3_000.0, 300.0)))
        assertFalse(tracker.update(fix()).single().settled)
        assertTrue(tracker.update(fix(1_800.0)).single().settled)
    }

    @Test
    fun `우회전 앞에서 회전 지점보다 먼 오른쪽 장소는 꺾은 뒤 왼쪽이 될 수 있어 아직 소개하지 않는다`() {
        val beyondRight = poi("br", 2_000.0, 600.0)
        val beyondLeft = poi("bl", 2_000.0, -600.0)
        val beforeRight = poi("nr", 500.0, 300.0)
        val turn = Maneuver(1_000, "우회전", Side.RIGHT)
        val ahead = AheadTracker(listOf(beyondRight, beyondLeft, beforeRight)).update(fix(), turn)
        assertEquals(listOf("bl", "nr"), ahead.settledIds().sorted())
    }

    @Test
    fun `가까운 순서다`() {
        val ahead = AheadTracker(listOf(poi("b", 1_400.0, 300.0), poi("a", 500.0, 100.0))).update(fix())
        assertEquals(listOf("a", "b"), ahead.ids())
    }

    @Test
    fun `지나쳐 멀어진 곳은 빠지고 다시 앞에 나타나도 넣지 않는다`() {
        val tracker = AheadTracker(listOf(poi("t", 1_000.0, 0.0)))
        assertEquals(1, tracker.update(fix(0.0)).size)
        tracker.update(fix(1_000.0))
        assertEquals(0, tracker.update(fix(1_300.0)).size)
        assertEquals(0, tracker.update(fix(1_600.0, heading = 180.0)).size)
    }

    @Test
    fun `골목에서 방향이 잠깐 뒤로 바뀌어도 멀어지지 않았으면 지나친 것으로 치지 않는다`() {
        val tracker = AheadTracker(listOf(poi("t", 3_000.0, 1_000.0)))
        tracker.update(fix(0.0))
        tracker.update(fix(-50.0, 50.0, heading = 135.0))
        assertEquals(listOf("t"), tracker.update(fix(100.0, 50.0, heading = 0.0)).settledIds())
    }

    @Test
    fun `옆으로 지나다 그 장소 좌표에 서면 거리 0으로 목록에 있다`() {
        val side = poi("s", 1_000.0, 480.0)
        val tracker = AheadTracker(listOf(side))
        tracker.update(fix(0.0))
        tracker.update(fix(1_000.0))
        tracker.update(fix(1_600.0))
        val stopped = tracker.update(Fix(side.destination.latitude, side.destination.longitude, 0.0, 0.0))
        assertEquals(0, stopped.single().distanceMeters)
    }

    @Test
    fun `속도가 낮으면 마지막으로 믿을 수 있었던 방향을 쓴다`() {
        val tracker = AheadTracker(listOf(ne))
        tracker.update(fix(heading = 0.0))
        assertEquals(Side.RIGHT, tracker.update(fix(heading = 180.0, speed = 0.0)).single().side)
    }

    @Test
    fun `출발 전에는 모든 장소가 가까운 순서로 나오고 아직 소개하지 않는다`() {
        val ahead = AheadTracker(listOf(far, south, ne)).update(fix(heading = 0.0, speed = 0.0))
        assertEquals(listOf("ne", "south", "far"), ahead.ids())
        assertTrue(ahead.none { it.settled })
    }

    @Test
    fun `출발 직후 방향이 잠깐 반대여도 멀어지지 않았으면 지나친 것으로 치지 않는다`() {
        val tracker = AheadTracker(listOf(poi("t", 2_000.0, 700.0)))
        tracker.update(fix(speed = 0.0))
        tracker.update(fix(heading = 180.0))
        assertEquals(listOf("t"), tracker.update(fix(heading = 0.0)).settledIds())
    }
}
