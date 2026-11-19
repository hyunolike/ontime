package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.platform.Destination
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class TrackPlayerTest {
    private val lat0 = 37.45
    private val lon0 = 127.10
    private fun north(m: Double) = m / 111_195.0
    private fun east(m: Double) = m / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0)))
    private fun at(n: Double, e: Double) = LatLon(lat0 + north(n), lon0 + east(e))

    // 북쪽으로 2km, 동쪽으로 꺾어 1km.
    private val end = at(2_000.0, 1_000.0)
    private val track = Track(
        destination = Destination("end", "끝", end.latitude, end.longitude),
        points = listOf(at(0.0, 0.0), at(2_000.0, 0.0), end),
        maneuvers = listOf(RouteManeuver(2_000, "우회전")),
    )
    private val start = Instant.parse("2026-12-05T01:00:00Z")

    // 동쪽 길가에서 300m 떨어진 곳. 경로 1km 지점과 가장 가깝다.
    private val roadside = Destination("side", "길가", lat0 + north(1_000.0), lon0 + east(300.0))

    private fun player() = TrackPlayer(track, start, cruise = 10.0, playback = 10.0)

    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected±$tolerance but was $actual")

    @Test
    fun `출발 전에도 첫 위치와 북쪽 방향을 속도 0으로 내보낸다`() = runTest {
        val fix = player().fixes.first()
        near(lat0, fix.latitude, 1e-6)
        near(0.0, fix.heading!!, 0.5)
        assertEquals(0.0, fix.speedMetersPerSecond)
    }

    @Test
    fun `꺾이기 전 40m 안에서는 동쪽을 향하기 시작한다`() = runTest {
        val player = player()
        player.seek(1_990.0)
        val heading = player.fixes.first().heading!!
        assertTrue(heading in 10.0..80.0, "heading=$heading")
        player.seek(2_100.0)
        near(90.0, player.fixes.first().heading!!, 0.5)
    }

    @Test
    fun `600m 안의 다음 회전만 내보낸다`() = runTest {
        val player = player()
        assertNull(player.nextManeuver.first())
        player.seek(1_500.0)
        assertEquals(500, player.nextManeuver.first()!!.distanceMeters)
        player.seek(2_010.0)
        assertNull(player.nextManeuver.first())
    }

    @Test
    fun `경유지에 닿으면 그 장소 좌표에 서고 다시 출발하면 이어서 목적지까지 간다`() = runTest {
        val player = player()
        assertNotNull(player.canStopAt(roadside))
        assertTrue(player.stopAt(roadside))
        player.run()
        val parked = player.fixes.first()
        assertEquals(roadside.latitude, parked.latitude)
        assertEquals(roadside.longitude, parked.longitude)
        assertEquals(0.0, parked.speedMetersPerSecond)
        assertFalse(player.driving.first())
        assertTrue(player.atVisitedStop)
        near(1_000.0, player.traveled, 20.0)

        player.run()
        assertFalse(player.atVisitedStop)
        assertFalse(player.driving.first())
        assertEquals(Duration.ZERO, player.remaining)
        near(end.latitude, player.fixes.first().latitude, 1e-6)
    }

    @Test
    fun `달리는 중에는 driving이고 남은 시간이 준다`() = runTest {
        val player = player()
        val job = launch { player.run() }
        advanceUntilIdle()
        job.join()
        assertEquals(Duration.ZERO, player.remaining)
        // 출발 시각에서 300초(3km / 10m/s) 지났다.
        assertEquals(start.plusSeconds(300), player.now.first())
    }

    @Test
    fun `이미 지난 곳에는 설 수 없다`() = runTest {
        val player = player()
        player.seek(1_500.0)
        assertNull(player.canStopAt(roadside))
        assertFalse(player.stopAt(roadside))
    }

    @Test
    fun `경유지에 선 채 다른 곳을 고르면 다시 출발했을 때 그곳까지 간다`() = runTest {
        val player = player()
        player.stopAt(roadside)
        player.run()
        val later = Destination("later", "뒤쪽", lat0 + north(2_200.0), lon0 + east(500.0))
        assertTrue(player.stopAt(later))
        player.run()
        assertTrue(player.atVisitedStop)
        assertEquals(later.latitude, player.fixes.first().latitude)
        near(2_500.0, player.traveled, 20.0)
    }
}
