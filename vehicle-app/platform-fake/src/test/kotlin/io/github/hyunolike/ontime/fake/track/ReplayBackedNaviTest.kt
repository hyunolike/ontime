package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ReplayBackedNaviTest {
    private val lat0 = 37.45
    private val lon0 = 127.10
    private fun at(n: Double, e: Double) = LatLon(lat0 + n / 111_195.0, lon0 + e / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0))))
    private fun place(id: String, n: Double, e: Double) = at(n, e).let { Destination(id, id, it.latitude, it.longitude) }

    private val end = place("end", 3_000.0, 0.0)
    private val track = Track(end, listOf(at(0.0, 0.0), at(3_000.0, 0.0)), listOf(RouteManeuver(1_000, "우회전")))
    private val first = place("first", 1_000.0, 300.0)
    private val second = place("second", 2_000.0, 300.0)

    /** 호출을 기록하는 가짜 내비. */
    private class FakeNavi(var accept: Boolean = true, val clock: () -> Instant = { Instant.EPOCH }) : NaviGateway {
        val calls = mutableListOf<String>()
        val routes = MutableSharedFlow<RouteStatus>(replay = 1)
        override val route: Flow<RouteStatus> = routes
        override suspend fun estimateRemainingDrive(to: Destination): Duration? = null
        override suspend fun changeDestination(to: Destination): Boolean { calls += "route:${to.id}"; return true }
        override suspend fun addWaypoint(to: Destination): Boolean { calls += "add:${to.id}"; return accept }
        override suspend fun removeWaypoint(): Boolean { calls += "remove"; return true }

        fun confirm(destination: Destination, at: Instant = clock()) {
            routes.tryEmit(RouteStatus(destination, Reading(27.minutes, at, Source.LIVE)))
        }
    }

    /** 가상 시간을 따르는 벽시계. */
    private fun TestScope.clock(): () -> Instant = { Instant.ofEpochMilli(testScheduler.currentTime) }

    private fun TestScope.setup(accept: Boolean = true): Triple<FakeNavi, TrackPlayer, ReplayBackedNavi> {
        val primary = FakeNavi(accept, clock())
        val player = TrackPlayer(track, Instant.parse("2026-12-05T01:00:00Z"))
        return Triple(primary, player, ReplayBackedNavi(primary, player, clock = clock()))
    }

    @Test
    fun `남은 시간은 내비 값이 없어도 재생 값으로 곧바로 나온다`() = runTest {
        val (_, _, navi) = setup()
        val route = navi.route.first()
        assertEquals("end", route.destination.id)
        assertTrue((route.remainingDrive.value - 300.seconds).absoluteValue < 1.seconds)
        assertEquals(Source.REPLAY, route.remainingDrive.source)
    }

    @Test
    fun `처음 출발하면 경로를 요청하고 목적지에서 남은 시간이 0이 된다`() = runTest {
        val (primary, _, navi) = setup()
        navi.drive()
        assertEquals(listOf("route:end"), primary.calls)
        assertEquals(Duration.ZERO, navi.route.first().remainingDrive.value)
    }

    @Test
    fun `달리는 중에 경유지를 넣으면 내비에 넣고 재생기도 그곳에서 선다`() = runTest {
        val (primary, player, navi) = setup()
        primary.confirm(end)
        player.seek(10.0)
        navi.markRouted()
        assertTrue(navi.addWaypoint(first))
        navi.drive()
        assertTrue(player.atVisitedStop)
        assertEquals(listOf("add:first"), primary.calls)
    }

    @Test
    fun `들른 경유지에서 다시 출발하면 내비의 경유지를 지운다`() = runTest {
        val (primary, player, navi) = setup()
        navi.markRouted()
        navi.addWaypoint(first)
        navi.drive()
        navi.drive()
        assertEquals(listOf("add:first", "remove"), primary.calls)
        assertFalse(player.atVisitedStop)
        assertEquals(Duration.ZERO, player.remaining)
    }

    @Test
    fun `경유지에 선 채 다른 곳을 고르면 들른 곳을 먼저 지우고 새 곳을 넣는다`() = runTest {
        val (primary, player, navi) = setup()
        navi.markRouted()
        navi.addWaypoint(first)
        navi.drive()
        assertTrue(navi.addWaypoint(second))
        navi.drive()
        assertEquals(listOf("add:first", "remove", "add:second"), primary.calls)
        assertTrue(player.atVisitedStop)
    }

    @Test
    fun `재생기가 이미 지난 곳이면 내비에 보내지 않는다`() = runTest {
        val (primary, player, navi) = setup()
        navi.markRouted()
        player.seek(1_500.0)
        assertFalse(navi.addWaypoint(first))
        assertTrue(primary.calls.isEmpty())
    }

    @Test
    fun `내비가 받지 않으면 재생기도 서지 않는다`() = runTest {
        val (primary, player, navi) = setup(accept = false)
        navi.markRouted()
        assertFalse(navi.addWaypoint(first))
        navi.drive()
        assertFalse(player.atVisitedStop)
        assertEquals(Duration.ZERO, player.remaining)
        assertEquals(listOf("add:first"), primary.calls)
    }

    @Test
    fun `출발 전에 경유지를 고르면 경로를 요청하고 내비가 목적지를 알려 온 뒤 넣는다`() = runTest {
        val (primary, _, navi) = setup()
        // 요청 전에 받아 둔 오래된 값은 확인으로 치지 않는다.
        primary.confirm(end)
        advanceTimeBy(60_000)
        val added = async { navi.addWaypoint(first) }
        advanceTimeBy(5_000)
        assertEquals(listOf("route:end"), primary.calls)
        // 다른 목적지는 확인으로 치지 않는다.
        primary.confirm(place("other", 9_000.0, 9_000.0))
        advanceTimeBy(1_000)
        assertEquals(1, primary.calls.size)
        primary.confirm(end)
        assertTrue(added.await())
        assertEquals(listOf("route:end", "add:first"), primary.calls)
        // 출발할 때 경로를 다시 요청하지 않는다.
        navi.drive()
        assertEquals(listOf("route:end", "add:first"), primary.calls)
    }

    @Test
    fun `내비가 이미 같은 목적지로 안내 중이면 경로를 다시 요청하지 않고 경유지를 넣는다`() = runTest {
        val (primary, _, navi) = setup()
        primary.confirm(end)
        advanceTimeBy(5_000)
        assertTrue(navi.addWaypoint(first))
        navi.drive()
        assertEquals(listOf("add:first"), primary.calls)
    }

    @Test
    fun `출발 전 경로 확인이 20초 안에 오지 않으면 경유지를 넣지 않는다`() = runTest {
        val (primary, player, navi) = setup()
        val added = async { navi.addWaypoint(first) }
        advanceTimeBy(21_000)
        assertFalse(added.await())
        assertEquals(listOf("route:end"), primary.calls)
        navi.drive()
        assertFalse(player.atVisitedStop)
    }
}
