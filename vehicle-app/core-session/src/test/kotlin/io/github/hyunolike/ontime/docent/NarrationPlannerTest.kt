package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.track.TrackPlayer
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.RouteShape
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NarrationPlannerTest {
    /** 계획만 시험하는 경로. 위치만 쓴다. */
    private class Shape(override var progress: Double = 0.0) : RouteShape {
        override val length = 20_000.0
        override fun fixAt(meters: Double) = Fix(0.0, 0.0, 0.0, 10.0)
        override fun turnAfter(meters: Double): Maneuver? = null
        override val maneuverMeters = emptyList<Double>()
        override fun meterOf(place: Destination) = 0.0
    }

    // 말하는 시간: 긴 해설 10초, 짧은 해설 4초, 질문 2초(+ 기다림 10초). 초당 100m.
    private fun poi(id: String, canStop: Boolean = false) = Poi(
        Destination(id, id, 0.0, 0.0), story = "LONG", canStop = canStop, source = Source.REPLAY, shortStory = "SHORT",
    )
    private val speak: (String) -> Double = { t -> if (t.endsWith("LONG")) 10.0 else if (t.endsWith("SHORT")) 4.0 else 2.0 }

    private fun planner(shape: Shape, timeline: RouteTimeline) = NarrationPlanner(shape, timeline, { 100.0 }, speak)

    @Test
    fun `긴 해설로는 둘 다 못 들려주면 앞 장소를 짧게 말해 둘 다 들려준다`() {
        val a = poi("a"); val b = poi("b")
        // a는 0~1,000m, b는 600~900m에서 시작해야 한다. a를 길게(1,000m) 말하면 끝나는 지점이 b의 구간을 넘는다.
        val timeline = RouteTimeline(mapOf("a" to listOf(Span(0.0, 1_000.0)), "b" to listOf(Span(600.0, 900.0))), emptyList())
        val plan = planner(Shape(), timeline).plan(0.0, emptySet(), canAsk = false, listOf(a, b))
        assertEquals(listOf("a" to Telling.SHORT, "b" to Telling.LONG), plan.map { it.poi.destination.id to it.telling })
    }

    @Test
    fun `긴 해설로는 질문이 회전에 잘리면 짧게 말해 질문을 지킨다`() {
        val a = poi("a", canStop = true)
        // 길게 + 질문 = 1,000 + 1,200 = 2,200m라 2,000m부터의 조용 구간에 걸린다. 짧게 + 질문 = 1,600m.
        val timeline = RouteTimeline(mapOf("a" to listOf(Span(0.0, 500.0))), listOf(Span(2_000.0, 2_600.0)))
        val plan = planner(Shape(), timeline).plan(0.0, emptySet(), canAsk = true, listOf(a))
        assertEquals(Telling.SHORT, plan.single().telling)
        assertFalse(plan.single().askCut)
    }

    @Test
    fun `조용 구간 안에서는 시작하지 않고 끝난 뒤에 시작한다`() {
        val a = poi("a")
        val timeline = RouteTimeline(mapOf("a" to listOf(Span(1_000.0, 3_000.0))), listOf(Span(900.0, 1_500.0)))
        val shape = Shape(progress = 1_000.0)
        val planner = planner(shape, timeline)
        assertEquals(null, planner.next(emptySet(), false, listOf(a)))
        assertTrue(planner.plan(1_000.0, emptySet(), false, listOf(a)).single().start > 1_500.0)
    }

    @Test
    fun `들은 곳은 계획에 넣지 않는다`() {
        val timeline = RouteTimeline(mapOf("a" to listOf(Span(0.0, 1_000.0))), emptyList())
        assertTrue(planner(Shape(), timeline).plan(0.0, setOf("a"), false, listOf(poi("a"))).isEmpty())
    }

    @Test
    fun `송파 경로의 시간표는 지날 때의 쪽이 정해진 구간만 소개 구간으로 둔다`() {
        val player = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH)
        val timeline = RouteTimeline.build(player, ScenarioSongpa.pois)
        val ids = ScenarioSongpa.pois.map { it.destination.id }
        assertTrue(ids.all { timeline.windows[it]?.isNotEmpty() == true }, "${timeline.windows.keys}")
        // 방이동 고분군은 양재대로 우회전(11,526m)을 지나야 쪽이 정해진다.
        assertTrue(timeline.windows.getValue(ScenarioSongpa.BANGI.id).first().from > 11_526)
        // 석촌동 고분군은 우회전 앞에서 소개할 수 있다.
        assertTrue(timeline.windows.getValue(ScenarioSongpa.SEOKCHON.id).first().from < 11_526)
    }
}
