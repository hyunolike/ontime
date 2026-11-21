package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.ScriptedSpeech
import io.github.hyunolike.ontime.fake.track.LatLon
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.Track
import io.github.hyunolike.ontime.fake.track.TrackPlayer
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 같은 길을 탐욕 규칙(가장 가까운 곳부터 바로)과 스케줄러로 달려 비교한다. 들르지 않는다고 답한다.
 * 결과 표는 build/planner-evaluation.md에 남는다(docs/features/narration-planner).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlannerEvaluationTest {
    private class NoNavi : NaviGateway {
        override val route: Flow<RouteStatus> = emptyFlow()
        override suspend fun estimateRemainingDrive(to: Destination): Duration? = null
        override suspend fun changeDestination(to: Destination): Boolean = true
        override suspend fun addWaypoint(to: Destination): Boolean = true
    }

    data class Metrics(val places: Int, val told: Int, val long: Int, val asked: Int, val askCut: Int, val notAsked: Int) {
        // 스케줄러의 목표와 같다. 회전에 닫힌 질문은 답할 틈이 있었다고 보고 2점이다.
        val score: Int get() = long * 3 + (told - long) * 2 + (asked - askCut) * 3 + askCut * 2

        override fun toString() = "들려줌 $told/$places, 길게 $long, 질문 $asked, 회전에 닫힌 질문 $askCut, 회전 앞이라 못 물음 $notAsked"
    }

    private fun TestScope.drive(track: Track, pois: List<Poi>, withPlanner: Boolean, playback: Double): Metrics {
        val player = TrackPlayer(track, Instant.EPOCH, playback = playback)
        val navi = ReplayBackedNavi(NoNavi(), player)
        val tracker = AheadTracker(pois, passedOnRoute = { player.hasPassed(it.destination) })
        val speech = ScriptedSpeech(answer = false, answerDelay = 10.seconds, sayDuration = { speakingSeconds(it).seconds })
        // 초속 10m로 달리고 재생 배속만큼 빨라진다. 실제 1초에 가는 거리다.
        val planner = if (withPlanner) NarrationPlanner(player, RouteTimeline.build(player, pois), { 10.0 * playback }) else null
        val session = DocentSession(
            player, navi, player, speech, player, LiveStories(player, tracker, player.nextTurn),
            quietWithin = 600, catalog = { pois }, planner = planner,
        )
        val states = mutableListOf<DocentState>()
        backgroundScope.launch { session.run() }
        backgroundScope.launch { session.state.collect { it?.let(states::add) } }
        runCurrent()
        launch { navi.drive() }
        advanceUntilIdle()
        val told = states.last().told
        val spoken = speech.spoken.value
        // 질문 결과는 세션이 직접 기록한 것을 쓴다. 곧바로 지워지는 상태는 상태 흐름에서 관찰되지 않는다.
        val asks = session.asks
        return Metrics(
            places = pois.size,
            told = told.size,
            long = told.count { poi -> spoken.any { it.endsWith(poi.story) } },
            asked = asks.count { it.outcome != AskOutcome.NOT_ASKED_TURN },
            askCut = asks.count { it.outcome == AskOutcome.CLOSED_BY_TURN },
            notAsked = asks.count { it.outcome == AskOutcome.NOT_ASKED_TURN },
        )
    }

    /** 12km 북쪽으로 가다 동쪽으로 꺾는 길. 장소 8곳이 약 1km 간격으로 양쪽에 번갈아 있다. */
    private val denseTrack: Pair<Track, List<Poi>> = run {
        val lat0 = 37.45
        val lon0 = 127.10
        fun at(n: Double, e: Double) = LatLon(lat0 + n / 111_195.0, lon0 + e / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0))))
        val end = at(9_000.0, 3_000.0)
        val track = Track(
            Destination("dense-end", "끝", end.latitude, end.longitude),
            listOf(at(0.0, 0.0), at(5_000.0, 0.0), at(5_000.0, 1_500.0), at(9_000.0, 1_500.0), end),
            listOf(
                RouteManeuver(5_000, "우회전", Side.RIGHT),
                RouteManeuver(6_500, "좌회전", Side.LEFT),
                RouteManeuver(10_500, "우회전", Side.RIGHT),
            ),
        )
        val story = "오래된 성곽길이 산을 따라 이어져요. 봄에는 꽃길이 되고 가을에는 단풍이 들어요. 성문 앞에서는 옛 모습을 재현한 행사가 열리기도 해요."
        val spots = listOf(
            1_200.0 to 400.0, 2_100.0 to -400.0, 3_000.0 to 450.0, 3_900.0 to -350.0,
            5_400.0 to 4_000.0, 6_900.0 to 5_800.0, 7_800.0 to 6_900.0, 8_700.0 to 7_600.0,
        )
        val pois = spots.mapIndexed { i, (n, e) ->
            // 앞 네 곳은 북쪽 길 양옆, 뒤 네 곳은 꺾은 뒤의 길 옆이다.
            val p = if (i < 4) at(n, e) else at(if (i % 2 == 0) e - 300 else e + 300, if (i < 6) 1_500.0 + (if (i % 2 == 0) 350.0 else -350.0) else 1_500.0 + (if (i % 2 == 0) 350.0 else -350.0))
            Poi(Destination("dense-$i", "장소${i + 1}", p.latitude, p.longitude), story, canStop = i % 2 == 0, source = Source.REPLAY)
        }
        track to pois
    }

    @Test
    fun `스케줄러는 어느 지표에서도 탐욕 규칙보다 나빠지지 않는다`() = runTest {
        val rows = mutableListOf<String>()
        val routes = listOf("송파 경로" to (ScenarioSongpa.track to ScenarioSongpa.pois), "장소가 몰린 시험 경로" to denseTrack)
        // 실제 속도(1배)와 시연 속도(10배). 10배속에서는 10초 질문이 도로 1km라 회전에 걸리기 쉽다.
        for ((speedName, playback) in listOf("실제 속도" to 1.0, "시연 10배속" to 10.0)) for ((routeName, scenario) in routes) {
            val name = "$routeName, $speedName"
            val (track, pois) = scenario
            val greedy = drive(track, pois, withPlanner = false, playback = playback)
            val planned = drive(track, pois, withPlanner = true, playback = playback)
            rows += "| $name | 탐욕 | ${greedy.told}/${greedy.places} | ${greedy.long} | ${greedy.asked - greedy.askCut} | ${greedy.askCut} | ${greedy.notAsked} | ${greedy.score} |"
            rows += "| $name | 스케줄러 | ${planned.told}/${planned.places} | ${planned.long} | ${planned.asked - planned.askCut} | ${planned.askCut} | ${planned.notAsked} | ${planned.score} |"
            assertTrue(planned.told >= greedy.told, "$name 들려준 곳: 탐욕 $greedy, 스케줄러 $planned")
            assertTrue(planned.score >= greedy.score, "$name 종합: 탐욕 $greedy(${greedy.score}), 스케줄러 $planned(${planned.score})")
        }
        val table = (listOf("| 경로 | 방식 | 들려준 곳 | 길게 | 끝까지 물은 질문 | 회전에 닫힌 질문 | 회전 앞이라 못 물음 | 점수 |", "|---|---|---|---|---|---|---|---|") + rows).joinToString("\n")
        File("build/planner-evaluation.md").writeText(table + "\n")
        println(table)
    }
}
