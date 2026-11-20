package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class TrackFollowTest {
    private val lat0 = 37.45
    private val lon0 = 127.10
    private fun north(m: Double) = m / 111_195.0
    private fun east(m: Double) = m / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0)))
    private fun fix(n: Double, e: Double, speed: Double = 10.0, heading: Double = 0.0) = Fix(lat0 + north(n), lon0 + east(e), heading, speed)

    // 북쪽으로 2km 갔다가 남쪽으로 돌아오는 U자 길. 굽은 길에서 먼 구간으로 튀지 않는지 보려고 둘을 100m 띄운다.
    private val end = LatLon(lat0, lon0 + east(100.0))
    private val track = Track(
        Destination("end", "끝", end.latitude, end.longitude),
        listOf(LatLon(lat0, lon0), LatLon(lat0 + north(2_000.0), lon0), LatLon(lat0 + north(2_000.0), lon0 + east(100.0)), end),
        listOf(RouteManeuver(2_000, "우회전")),
    )
    private val roadside = Destination("side", "길가", lat0 + north(1_000.0), lon0 - east(300.0))

    private var nowMillis = 0L
    private fun player() = TrackPlayer(track, Instant.EPOCH, clock = { nowMillis })

    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected±$tolerance but was $actual")

    @Test
    fun `받은 위치를 경로에 투영해 달린 거리를 갱신하고 그 위치를 그대로 내보낸다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(500.0, 5.0))
        runCurrent()
        near(500.0, player.traveled, 1.0)
        val out = player.fixes.first()
        assertEquals(lat0 + north(500.0), out.latitude)
        assertTrue(player.driving.first())
        job.cancel()
    }

    @Test
    fun `뒤로 가지 않고 굽은 길의 먼 구간으로 튀지 않는다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(300.0, 0.0)); runCurrent()
        // 돌아오는 구간(동쪽 100m)의 같은 높이에 가까운 점이 와도 1km 앞까지만 찾으므로 그쪽으로 튀지 않는다.
        fixes.emit(fix(300.0, 60.0)); runCurrent()
        near(300.0, player.traveled, 1.0)
        fixes.emit(fix(200.0, 0.0)); runCurrent()
        near(300.0, player.traveled, 1.0)
        job.cancel()
    }

    @Test
    fun `경로를 따라 뒤로 가는 위치는 정차로 내보내고, 300m 넘게 뒤로 가면 그 자리로 다시 맞춘다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(1_500.0, 0.0)); runCurrent()
        near(1_500.0, player.traveled, 1.0)
        // 남쪽(뒤)으로 미끄러진다. 내보내는 위치는 정차(속도 0)다.
        fixes.emit(fix(1_450.0, 0.0, speed = 50.0, heading = 180.0)); runCurrent()
        assertEquals(0.0, player.fixes.first().speedMetersPerSecond)
        near(1_500.0, player.traveled, 1.0)
        // 출발점 가까이까지 돌아오면 그 자리로 다시 맞춘다.
        fixes.emit(fix(20.0, 0.0, speed = 50.0, heading = 180.0)); runCurrent()
        near(20.0, player.traveled, 1.0)
        // 다시 앞으로 가면 평소대로다.
        fixes.emit(fix(70.0, 0.0)); runCurrent()
        near(70.0, player.traveled, 1.0)
        assertEquals(10.0, player.fixes.first().speedMetersPerSecond)
        job.cancel()
    }

    @Test
    fun `따라가기를 시작했지만 아직 위치가 없으면 재생 속도가 아니라 정차로 내보낸다`() = runTest {
        val player = player()
        val job = launch { player.follow(MutableSharedFlow()) }
        runCurrent()
        assertEquals(0.0, player.fixes.first().speedMetersPerSecond)
        job.cancel()
    }

    @Test
    fun `처음부터 서 있으면 곧바로 정차다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(0.0, 0.0, speed = 0.0)); runCurrent()
        assertFalse(player.driving.first())
        job.cancel()
    }

    @Test
    fun `3초 넘게 서 있으면 정차다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(100.0, 0.0)); runCurrent()
        nowMillis = 1_000; fixes.emit(fix(100.0, 0.0, speed = 0.0)); runCurrent()
        assertTrue(player.driving.first())
        nowMillis = 4_500; fixes.emit(fix(100.0, 0.0, speed = 0.0)); runCurrent()
        assertFalse(player.driving.first())
        job.cancel()
    }

    @Test
    fun `경유지 근처에 서면 들른 것이고 다시 출발하면 다음 정차까지 기다린다`() = runTest {
        val player = player()
        val fixes = MutableSharedFlow<Fix>(replay = 1)
        val job = launch { player.follow(fixes) }
        fixes.emit(fix(10.0, 0.0)); runCurrent()
        assertTrue(player.stopAt(roadside))
        val firstLeg = async { player.run() }
        // 길에서 벗어나 그 장소로 들어가 선다. 길에서 먼 위치라 달린 거리는 바뀌지 않는다.
        fixes.emit(fix(1_000.0, 0.0)); runCurrent()
        nowMillis = 1_000; fixes.emit(Fix(roadside.latitude, roadside.longitude, 0.0, 0.0)); runCurrent()
        nowMillis = 5_000; fixes.emit(Fix(roadside.latitude, roadside.longitude, 0.0, 0.0)); runCurrent()
        assertTrue(firstLeg.isCompleted)
        assertTrue(player.atVisitedStop)
        near(1_000.0, player.traveled, 1.0)

        val secondLeg = async { player.run() }
        runCurrent()
        assertFalse(player.atVisitedStop)
        // 실제 스크립트처럼 1km보다 촘촘하게 위치가 온다.
        for ((i, p) in listOf(1_500.0 to 0.0, 2_000.0 to 50.0, 1_200.0 to 100.0, 400.0 to 100.0).withIndex()) {
            nowMillis = 6_000L + i * 500; fixes.emit(fix(p.first, p.second)); runCurrent()
        }
        assertFalse(secondLeg.isCompleted)
        // 끝에 닿아 선다.
        nowMillis = 8_000; fixes.emit(Fix(end.latitude, end.longitude, 180.0, 0.0)); runCurrent()
        nowMillis = 12_000; fixes.emit(Fix(end.latitude, end.longitude, 180.0, 0.0)); runCurrent()
        assertTrue(secondLeg.isCompleted)
        job.cancel()
    }
}
