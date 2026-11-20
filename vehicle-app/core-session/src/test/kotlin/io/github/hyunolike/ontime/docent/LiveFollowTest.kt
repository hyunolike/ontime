package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.TrackPlayer
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.MotionEstimator
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.Side
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 에뮬레이터 스크립트(tools/geo-drive.py)처럼 송파 경로 좌표를 0.5초마다 위치로 흘려 넣는다.
 * 앱은 재생기가 아니라 이 위치로 움직인다. 실제 위치 SDK 경로와 같은 조합이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveFollowTest {
    private class NoNavi : NaviGateway {
        override val route: Flow<RouteStatus> = emptyFlow()
        override suspend fun estimateRemainingDrive(to: Destination): Duration? = null
        override suspend fun changeDestination(to: Destination): Boolean = true
        override suspend fun addWaypoint(to: Destination): Boolean = true
    }

    @Test
    fun `흘려 넣은 위치만으로 네 곳을 지날 때의 쪽으로 소개하고 끝에서 선다`() = runTest {
        val clock = { testScheduler.currentTime }
        val player = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH, clock = clock)
        // 경로를 50m 간격으로 0.5초마다. 초속 100m(10배속)다.
        val points = player.samples(50.0)
        val estimator = MotionEstimator()
        val gps: Flow<Fix> = flow {
            // 에뮬레이터에는 지난번에 넣은 먼 좌표(경주)가 남아 있다가 판교 좌표로 바뀐다.
            repeat(3) {
                emit(estimator.next(35.839, 129.211, clock()))
                delay(1_000)
            }
            // 지난 시연이 멈춘 2.3km 지점에 있다가, 출발점으로 돌아가는 동안 FusedLocation이 그 사이를 이어 준다.
            // 차가 경로를 따라 뒤로 빠르게 달린 것처럼 보이는 위치들이다.
            val stale = points.indexOfFirst { true } + 46
            emit(estimator.next(points[stale].latitude, points[stale].longitude, clock())); delay(1_000)
            for (i in stale downTo 0 step 5) {
                emit(estimator.next(points[i].latitude, points[i].longitude, clock()))
                delay(500)
            }
            for (p in points) {
                emit(estimator.next(p.latitude, p.longitude, clock()))
                delay(500)
            }
            // 끝에서 서 있다.
            repeat(10) {
                emit(estimator.next(points.last().latitude, points.last().longitude, clock()))
                delay(500)
            }
        }
        val navi = ReplayBackedNavi(NoNavi(), player)
        val stories = LiveStories(player, AheadTracker(ScenarioSongpa.pois, passedOnRoute = { player.hasPassed(it.destination) }), player.nextTurn)
        val session = DocentSession(
            player, navi, player, ScriptedSpeech(answer = false, answerDelay = 2.seconds, sayDelay = 1.seconds), player, stories,
            quietWithin = 600,
        )
        val states = mutableListOf<DocentState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        // 위치 흐름은 앞쪽 작업으로 둔다. advanceUntilIdle은 backgroundScope의 작업을 기다리지 않는다.
        launch { player.follow(gps) }
        runCurrent()
        launch { navi.drive() }
        advanceUntilIdle()

        val sides = states.mapNotNull { it.narration?.ahead }.associate { it.poi.destination to it.side }
        assertEquals(Side.RIGHT, sides[ScenarioSongpa.SEOUL_AIRBASE])
        assertEquals(Side.LEFT, sides[ScenarioSongpa.SEOKCHON])
        assertEquals(Side.LEFT, sides[ScenarioSongpa.BANGI])
        val last = states.last()
        assertTrue(!last.driving, "끝에서 서 있어야 한다")
        assertTrue(last.remaining < 10.seconds, "남은 시간 ${last.remaining}")
    }

    @Test
    fun `끝까지 달린 뒤 처음부터 다시 하고 출발점으로 위치를 옮기면 네 곳이 모두 다시 보인다`() = runTest {
        val clock = { testScheduler.currentTime }
        val player = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH, clock = clock)
        val points = player.samples(50.0)
        val estimator = MotionEstimator()
        // PleosLocation처럼 마지막 위치를 하나 들고 있는 흐름이다.
        val gps = MutableSharedFlow<Fix>(replay = 1)
        val tracker = AheadTracker(ScenarioSongpa.pois, passedOnRoute = { player.hasPassed(it.destination) })
        val navi = ReplayBackedNavi(NoNavi(), player)
        val session = DocentSession(
            player, navi, player, ScriptedSpeech(answer = false, answerDelay = 2.seconds, sayDelay = 1.seconds), player,
            LiveStories(player, tracker, player.nextTurn), quietWithin = 600,
        )
        val states = mutableListOf<DocentState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        var follow = launch { player.follow(gps) }
        launch { navi.drive() }
        for (p in points) {
            gps.emit(estimator.next(p.latitude, p.longitude, clock())); advanceTimeBy(500); runCurrent()
        }
        repeat(10) { gps.emit(estimator.next(points.last().latitude, points.last().longitude, clock())); advanceTimeBy(500); runCurrent() }

        // 처음부터 다시(Platform.reset과 같은 순서).
        follow.cancel(); follow.join()
        navi.reset(); tracker.reset(); session.reset()
        follow = launch { player.follow(gps) }
        runCurrent()
        // 스크립트가 출발점 좌표를 보내기 시작한다.
        repeat(6) { gps.emit(estimator.next(points.first().latitude, points.first().longitude, clock())); advanceTimeBy(500); runCurrent() }
        val ready = states.last()
        assertEquals(
            listOf(ScenarioSongpa.SEOUL_AIRBASE, ScenarioSongpa.SEOKCHON, ScenarioSongpa.BANGI, ScenarioSongpa.MONGCHON),
            ready.ahead.map { it.poi.destination },
        )
        follow.cancel()
    }

    @Test
    fun `경유지에 들르느라 길을 벗어나도 앞의 장소는 지나친 것으로 치지 않는다`() = runTest {
        val clock = { testScheduler.currentTime }
        val player = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH, clock = clock)
        val points = player.samples(50.0)
        val estimator = MotionEstimator()
        val gps = MutableSharedFlow<Fix>(replay = 1)
        val tracker = AheadTracker(ScenarioSongpa.pois, passedOnRoute = { player.hasPassed(it.destination) })
        val stories = LiveStories(player, tracker, player.nextTurn)
        val latest = mutableListOf<List<io.github.hyunolike.ontime.platform.PoiAhead>>()
        backgroundScope.launch { stories.ahead.collect { latest += it } }
        val follow = launch { player.follow(gps) }
        // 석촌동 고분군 진입점(약 11.5km)까지 달린다.
        val exit = player.canStopAt(ScenarioSongpa.SEOKCHON)!!
        for (p in points.take((exit / 50).toInt())) { gps.emit(estimator.next(p.latitude, p.longitude, clock())); advanceTimeBy(500); runCurrent() }
        // 서쪽으로 1.2km 떨어진 석촌동 고분군으로 들어간다. 동쪽의 방이동 고분군에서는 멀어진다.
        val from = points[(exit / 50).toInt()]
        for (k in 1..12) {
            val lat = from.latitude + (ScenarioSongpa.SEOKCHON.latitude - from.latitude) * k / 12
            val lon = from.longitude + (ScenarioSongpa.SEOKCHON.longitude - from.longitude) * k / 12
            gps.emit(estimator.next(lat, lon, clock())); advanceTimeBy(500); runCurrent()
        }
        assertTrue(latest.last().any { it.poi.destination == ScenarioSongpa.BANGI }, "방이동 고분군이 남아 있어야 한다: ${latest.last().map { it.poi.destination.name }}")
        follow.cancel()
    }
}
