package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.RouteShape
import kotlin.math.max

/** 경로 위 [from]~[to] 미터 구간. */
data class Span(val from: Double, val to: Double) {
    operator fun contains(m: Double) = m in from..to
}

/**
 * 앞길 시간표. 장소마다 소개를 시작할 수 있는 구간과 회전 앞 조용 구간을 경로 위 미터로 적는다.
 *
 * 소개 가능 여부는 주행 중 판정과 같다: [AheadTracker]가 그 장소를 settled로 보고, 직선 거리가 [tooLate]~[announceWithin]이다.
 * 그래서 경로를 [step]m 간격으로 따라가며 새 추적기에 위치를 넣어 본다.
 */
class RouteTimeline(
    val windows: Map<String, List<Span>>,
    val quiet: List<Span>,
    /** 목적지에 닿는 지점. 여기서 이야기와 질문이 끝난다. */
    val arrival: Double = Double.MAX_VALUE,
) {
    fun isQuiet(m: Double) = quiet.any { m in it }

    /** [m] 다음에 시작하는 첫 조용 구간의 시작. 없으면 null. */
    fun nextQuietStart(m: Double): Double? = quiet.filter { it.from > m }.minOfOrNull { it.from }

    companion object {
        fun build(
            shape: RouteShape,
            catalog: List<Poi>,
            announceWithin: Int = 3_000,
            tooLate: Int = 500,
            quietWithin: Int = 600,
            step: Double = 20.0,
        ): RouteTimeline {
            var at = 0.0
            val tracker = AheadTracker(catalog, passedOnRoute = { at > shape.meterOf(it.destination) + 200 })
            val open = mutableMapOf<String, MutableList<Span>>()
            while (at <= shape.length) {
                val ahead = tracker.update(shape.fixAt(at), shape.turnAfter(at))
                ahead.filter { it.settled && it.distanceMeters in tooLate..announceWithin }.forEach { a ->
                    val spans = open.getOrPut(a.poi.destination.id) { mutableListOf() }
                    val last = spans.lastOrNull()
                    if (last != null && at - last.to <= step * 1.5) spans[spans.lastIndex] = last.copy(to = at) else spans += Span(at, at)
                }
                at += step
            }
            val quiet = shape.maneuverMeters.map { Span(max(0.0, it - quietWithin), it) }
            return RouteTimeline(open, quiet, shape.length)
        }
    }
}

/** 장소 하나를 어떻게 들려줄지. */
enum class Telling { SHORT, LONG }

/**
 * 계획의 한 항목. [start]~[end]는 경로 위 미터.
 * [askDone]은 질문을 끝까지 할 수 있으면 참이다. 질문이 회전 조용 구간에 닿으면 거기서 닫히고([askCut]),
 * 해설이 끝났을 때 이미 조용 구간이면 묻지 않는다. 세션의 동작과 같다.
 */
data class PlannedNarration(
    val poi: Poi,
    val telling: Telling,
    val start: Double,
    val end: Double,
    val ask: Boolean,
    val askCut: Boolean,
    val askDone: Boolean = ask && !askCut,
    /** 질문이 닫히기 전에 답할 틈이 있었는지. 끝까지 물었으면 참이다. */
    val askChance: Boolean = askDone,
)

/**
 * 말할 순서를 앞길 전체를 보고 정한다.
 *
 * 앞의 장소마다 건너뛰기, 짧게, 길게 가운데 하나와 순서를 골라 점수가 가장 높은 계획을 찾는다. 각 항목은 소개 가능 구간 안,
 * 조용 구간 밖의 가장 이른 지점에서 시작하고 서로 겹치지 않는다. 점수는 길게 3, 짧게 2, 끝까지 물은 질문 3,
 * 회전이나 도착에 닫히지만 답할 틈([answerSeconds])은 있던 질문 2. 들를 기회를 주는 것이 서비스의 목적이라
 * 질문 하나가 해설 길이 차이(1점)보다 크다.
 * 장소가 많지 않아(앞 [horizon]m 안 보통 2~4곳) 모든 경우를 따져 본다.
 *
 * @param metersPerSecond 경로를 따라 실제 1초에 가는 거리. 말하는 시간을 미터로 바꾸는 데 쓴다(10배속 재생이면 100).
 * @param speakSeconds 문장을 말하는 데 걸리는 시간(초).
 */
class NarrationPlanner(
    private val shape: RouteShape,
    /** 장소 목록이 바뀌면(관광공사 데이터) 새로 만들어 바꾼다. */
    var timeline: RouteTimeline,
    private val metersPerSecond: () -> Double,
    private val speakSeconds: (String) -> Double = ::speakingSeconds,
    private val askSeconds: Double = 10.0,
    /** 회전에 닫히는 질문이라도 이만큼 열려 있으면 답할 기회가 있었다고 본다(2점). */
    private val answerSeconds: Double = 4.0,
    private val horizon: Double = 5_000.0,
    private val maxPlaces: Int = 6,
    /** 계획의 첫 항목 시작 지점이 이만큼 안이면 지금 시작한다. */
    private val startSlack: Double = 60.0,
) {
    /** 지금 시작할 항목. 기다려야 하면 null. */
    fun next(told: Set<String>, canAsk: Boolean, catalog: List<Poi>): PlannedNarration? {
        val from = shape.progress
        return plan(from, told, canAsk, catalog).firstOrNull()?.takeIf { it.start <= from + startSlack }
    }

    /** [from]부터의 가장 좋은 계획. */
    fun plan(from: Double, told: Set<String>, canAsk: Boolean, catalog: List<Poi>): List<PlannedNarration> {
        val candidates = catalog
            .filter { it.destination.id !in told }
            .mapNotNull { poi -> timeline.windows[poi.destination.id]?.let { spans -> poi to spans.filter { it.to >= from } } }
            .filter { (_, spans) -> spans.isNotEmpty() && spans.first().from <= from + horizon }
            .sortedBy { (_, spans) -> spans.first().from }
            .take(maxPlaces)
        var best: Result = Result(emptyList(), 0, Double.MAX_VALUE)
        fun search(remaining: List<Pair<Poi, List<Span>>>, freeAt: Double, chosen: List<PlannedNarration>, score: Int) {
            val end = chosen.lastOrNull()?.end ?: from
            if (score > best.score || (score == best.score && end < best.end)) best = Result(chosen, score, end)
            for ((index, candidate) in remaining.withIndex()) {
                val rest = remaining.toMutableList().also { it.removeAt(index) }
                val (poi, spans) = candidate
                for (telling in Telling.entries) {
                    val item = place(poi, spans, telling, freeAt, canAsk && poi.canStop && chosen.none { it.ask }) ?: continue
                    search(rest, item.end, chosen + item, score + scoreOf(item))
                }
            }
        }
        search(candidates, from, emptyList(), 0)
        return best.items
    }

    private data class Result(val items: List<PlannedNarration>, val score: Int, val end: Double)

    private fun place(poi: Poi, spans: List<Span>, telling: Telling, freeAt: Double, ask: Boolean): PlannedNarration? {
        val text = if (telling == Telling.LONG) poi.story else poi.short()
        val pace = metersPerSecond().coerceAtLeast(1.0)
        // 앞머리("2분 뒤 왼쪽에 ○○.")도 말한다. 오른쪽이면 "조수석 쪽" 안내가 붙어 조금 길어진다.
        val tell = (speakSeconds(LEAD_SAMPLE + poi.destination.name + ". " + text)) * pace
        val askLength = if (ask) (speakSeconds(QUESTION) + askSeconds) * pace else 0.0
        for (span in spans) {
            var start = max(freeAt, span.from)
            // 조용 구간 안이면 그 구간이 끝난 뒤로 민다.
            while (timeline.isQuiet(start)) start = timeline.quiet.first { start in it }.to + 1
            if (start > span.to) continue
            val tellEnd = start + tell
            if (!ask) return PlannedNarration(poi, telling, start, tellEnd, ask = false, askCut = false)
            // 해설이 끝났을 때 이미 회전 앞이거나 도착했으면 묻지 않는다.
            if (timeline.isQuiet(tellEnd) || tellEnd >= timeline.arrival) {
                return PlannedNarration(poi, telling, start, tellEnd, ask = false, askCut = false)
            }
            // 묻는 중에 회전 앞이 되거나 도착하면 그때 닫힌다.
            val closeAt = minOf(timeline.nextQuietStart(tellEnd) ?: Double.MAX_VALUE, timeline.arrival)
            val full = tellEnd + askLength
            return if (closeAt < full) {
                // 질문을 읽고 답할 틈이 [answerSeconds] 이상 있었으면 답할 기회는 준 것이다.
                val open = (closeAt - tellEnd) / pace - speakSeconds(QUESTION)
                PlannedNarration(poi, telling, start, closeAt, ask = true, askCut = true, askChance = open >= answerSeconds)
            } else {
                PlannedNarration(poi, telling, start, full, ask = true, askCut = false)
            }
        }
        return null
    }

    private fun scoreOf(item: PlannedNarration): Int =
        (if (item.telling == Telling.LONG) 3 else 2) + (if (item.askDone) 3 else if (item.askChance) 2 else 0)

    private companion object {
        const val LEAD_SAMPLE = "2분 뒤 왼쪽에 "
        const val QUESTION = "들러 볼까요?"
    }
}

/** 문장을 말하는 데 걸리는 시간(초). 한국어 기준 초당 6자, 최소 2초. 시연 빌드의 자막 표시 시간도 이 값을 쓴다. */
fun speakingSeconds(text: String): Double = maxOf(2.0, readSeconds(text))
