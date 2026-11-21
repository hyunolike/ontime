package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.LocationGateway
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.RoadGateway
import io.github.hyunolike.ontime.platform.RouteShape
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** 한 길의 모양과 그 위의 회전. [points]는 출발점부터 목적지까지 차례로. */
data class Track(val destination: Destination, val points: List<LatLon>, val maneuvers: List<RouteManeuver>)

/**
 * 실제 도로 모양([Track])을 따라 차의 위치를 재생한다. 에뮬레이터의 내비 차가 움직이지 않아서 쓴다.
 *
 * 경유지를 정하면 경로에서 그곳과 가장 가까운 지점까지 달린 뒤, 그 장소 좌표에 선 것으로 위치를 내보낸다.
 * 길가 장소는 도로에서 수백 m 떨어져 있어, 도로 위에 서면 실차와 같은 도장 기준(80m 안 정차)을 만족하지 못한다.
 *
 * @param cruise 달리는 속도(m/s)
 * @param playback 재생 배속
 */
class TrackPlayer(
    val track: Track,
    private val start: Instant,
    private val cruise: Double = 10.0,
    private val playback: Double = 10.0,
    private val step: Duration = 2.seconds,
    /** 실제 위치를 따라갈 때 쓰는 기기 시계(밀리초). */
    private val clock: () -> Long = System::currentTimeMillis,
) : TimeSource, LocationGateway, RoadGateway, VehicleGateway, RouteShape {
    private data class Stop(val atMeters: Double, val place: Destination)

    private data class State(
        val offset: Duration = Duration.ZERO,
        val traveled: Double = 0.0,
        val started: Boolean = false,
        val stop: Stop? = null,
        val arrivedStop: Boolean = false,
        /** 실제 위치를 따라가는 중이면 참. 시계로 재생하지 않는다. */
        val following: Boolean = false,
        /** 따라가는 중에 마지막으로 받은 실제 위치. */
        val liveFix: Fix? = null,
        /** 따라가는 중에 움직이고 있으면 참. 3초 넘게 서 있으면 거짓. */
        val liveMoving: Boolean = false,
        val stillSince: Long? = null,
        val nowMillis: Long = 0,
    )

    // 출발점 기준 평면 좌표(미터). 14km 안에서는 등장방형 투영의 오차를 무시할 만하다.
    private val lat0 = track.points.first().latitude
    private val lon0 = track.points.first().longitude
    private val metersPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS
    private val metersPerDegLon = metersPerDegLat * cos(Math.toRadians(lat0))
    private val xs = track.points.map { (it.longitude - lon0) * metersPerDegLon }
    private val ys = track.points.map { (it.latitude - lat0) * metersPerDegLat }
    private val cumulative: List<Double> = buildList {
        add(0.0)
        for (i in 1 until xs.size) add(last() + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]))
    }
    override val length: Double = cumulative.last()

    private val state = MutableStateFlow(State())

    /** 경로 위에서 달린 거리(미터). */
    val traveled: Double get() = state.value.traveled

    override val progress: Double get() = state.value.traveled

    override fun fixAt(meters: Double): Fix {
        val (x, y) = pointAt(meters)
        return Fix(lat0 + y / metersPerDegLat, lon0 + x / metersPerDegLon, headingAt(meters), cruise)
    }

    override fun turnAfter(meters: Double): Maneuver? = track.maneuvers
        .filter { it.turn != null && it.atMeters > meters - TURN_SETTLE }
        .minByOrNull { it.atMeters }
        ?.let { Maneuver((it.atMeters - meters).toInt().coerceAtLeast(0), it.instruction, it.turn) }

    override val maneuverMeters: List<Double> = track.maneuvers.map { it.atMeters.toDouble() }

    override fun meterOf(place: Destination): Double =
        placeAt.getOrPut(place.id) { project(place.latitude, place.longitude, 0.0, length)?.first ?: length }

    /** 경유지를 정해 두었으면(닿았든 아니든) true. */
    val hasStop: Boolean get() = state.value.stop != null

    /** 들른 경유지에 서 있으면 true. */
    val atVisitedStop: Boolean get() = state.value.arrivedStop

    /** 목적지까지 남은 주행 시간. 경유지에서 둘러보는 시간은 넣지 않는다. */
    val remaining: Duration get() = remainingOf(state.value)

    /** [remaining]과 그 값을 계산한 재생 시각. */
    val remainingFlow: Flow<Pair<Duration, Instant>> = state.map { remainingOf(it) to timeOf(it) }.distinctUntilChanged()

    override val now: Flow<Instant> = state.map(::timeOf).distinctUntilChanged()

    override val fixes: Flow<Fix> = state.map(::fixOf).distinctUntilChanged()

    override val driving: Flow<Boolean> = state.map {
        if (it.following) it.liveMoving && !it.arrivedStop else it.started && it.traveled < targetOf(it)
    }.distinctUntilChanged()

    override val nextManeuver: Flow<Maneuver?> = state.map { s ->
        track.maneuvers
            .filter { it.atMeters > s.traveled && it.atMeters - s.traveled <= MANEUVER_HORIZON }
            .minByOrNull { it.atMeters }
            ?.let { Maneuver((it.atMeters - s.traveled).toInt(), it.instruction, it.turn) }
    }.distinctUntilChanged()

    /**
     * 거리와 상관없이 앞에 있는 다음 회전(좌회전, 우회전). 쪽이 뒤집힐 장소를 가리는 데 쓴다.
     * 회전 지점을 지나고 [TURN_SETTLE]m까지는 그 회전을 아직 앞에 있는 것으로 둔다(거리 0). 위치 두 점으로 구한
     * 진행 방향은 꺾은 직후에도 한동안 꺾기 전 방향이라, 그 사이에 쪽을 정하면 틀린다.
     */
    val nextTurn: Flow<Maneuver?> = state.map { s ->
        track.maneuvers
            .filter { it.turn != null && it.atMeters > s.traveled - TURN_SETTLE }
            .minByOrNull { it.atMeters }
            ?.let { Maneuver((it.atMeters - s.traveled).toInt().coerceAtLeast(0), it.instruction, it.turn) }
    }.distinctUntilChanged()

    override val speedMetersPerSecond: Flow<Double> = flowOf(cruise)

    private val placeAt = mutableMapOf<String, Double>()

    /**
     * 경로를 따라 [place]의 지점을 [margin]m 넘게 지났으면 true. 길가 장소의 지점은 경로에서 그곳과 가장 가까운 곳이다.
     * 경유지에 들르느라 길을 벗어나도 달린 거리는 그대로라 앞의 장소를 지나친 것으로 보지 않는다.
     */
    fun hasPassed(place: Destination, margin: Double = 200.0): Boolean = state.value.traveled > meterOf(place) + margin

    /** 경로에서 [place]와 가장 가까운 지점의 경로 위 미터. 이미 지난 곳이면 null. */
    fun canStopAt(place: Destination): Double? =
        project(place.latitude, place.longitude, 0.0, length)?.first?.takeIf { it > state.value.traveled }

    /** 경로의 [from]~[to] 미터 구간에서 그 점과 가장 가까운 지점의 (경로 위 미터, 떨어진 거리). */
    private fun project(latitude: Double, longitude: Double, from: Double, to: Double): Pair<Double, Double>? {
        val px = (longitude - lon0) * metersPerDegLon
        val py = (latitude - lat0) * metersPerDegLat
        var best = Double.MAX_VALUE
        var bestAt: Double? = null
        for (i in 0 until xs.size - 1) {
            if (cumulative[i + 1] < from || cumulative[i] > to) continue
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val len2 = dx * dx + dy * dy
            var t = if (len2 == 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / len2).coerceIn(0.0, 1.0)
            // 구간 경계에 걸친 선분은 범위 안쪽으로 자른다.
            val segment = cumulative[i + 1] - cumulative[i]
            if (segment > 0) t = t.coerceIn(((from - cumulative[i]) / segment).coerceIn(0.0, 1.0), ((to - cumulative[i]) / segment).coerceIn(0.0, 1.0))
            val d = hypot(xs[i] + t * dx - px, ys[i] + t * dy - py)
            if (d < best) {
                best = d
                bestAt = cumulative[i] + t * segment
            }
        }
        return bestAt?.let { it to best }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        hypot((lon2 - lon1) * metersPerDegLon, (lat2 - lat1) * metersPerDegLat)

    /** [place]를 경유지로 정한다. 이미 지난 곳이면 false. 경유지에 서 있는 중이면 다시 출발할 때 그곳까지 간다. */
    fun stopAt(place: Destination): Boolean {
        val at = canStopAt(place) ?: return false
        state.value = state.value.copy(stop = Stop(at, place), arrivedStop = false)
        return true
    }

    /**
     * 정차 지점(경유지나 목적지)에 닿을 때까지 달린다. 들른 경유지에 서 있다가 부르면 그곳을 떠나 이어서 달린다.
     * 기존 `DocentPlayer.run()`과 같은 계약이다.
     */
    suspend fun run() {
        state.value = state.value.let { s ->
            if (s.arrivedStop) s.copy(started = true, stop = null, arrivedStop = false) else s.copy(started = true)
        }
        if (state.value.following) {
            // 실제 위치가 차를 움직인다. 경유지에 들르거나 끝에 닿아 설 때까지 기다린다.
            state.first { it.arrivedStop || (it.stop == null && it.traveled >= length - END_MARGIN && !it.liveMoving) }
            return
        }
        while (state.value.traveled < targetOf(state.value)) {
            delay(step / playback)
            val s = state.value
            val next = (s.traveled + cruise * step.inWholeMilliseconds / 1000.0).coerceAtMost(targetOf(s))
            state.value = s.copy(offset = s.offset + step, traveled = next)
        }
        val s = state.value
        if (s.stop != null && s.traveled >= s.stop.atMeters) state.value = s.copy(arrivedStop = true)
    }

    /** 경로 위 [everyMeters] 간격의 지점(출발점과 끝 포함). 경로 주변 장소를 찾을 때 쓴다. */
    fun samples(everyMeters: Double): List<LatLon> {
        val marks = generateSequence(0.0) { it + everyMeters }.takeWhile { it < length }.toList() + length
        return marks.map { m -> pointAt(m).let { (x, y) -> LatLon(lat0 + y / metersPerDegLat, lon0 + x / metersPerDegLon) } }
    }

    /** 출발 전으로 되돌린다. 시연을 처음부터 다시 할 때 쓴다. 달리는 중이면 먼저 [run]을 멈춰야 한다. */
    fun reset() {
        state.value = State()
    }

    /**
     * 시계 대신 실제 위치([fixes])를 따라간다. 받은 위치를 경로에 투영해 달린 거리를 갱신하고, 그 위치를 그대로 내보낸다.
     * 투영은 지금 위치에서 앞쪽 [FOLLOW_WINDOW] 안에서만 찾고 뒤로 가지 않는다. 길에서 [OFF_ROUTE]보다 멀면(들른 곳 안)
     * 달린 거리를 바꾸지 않는다. 취소되면 재생 모드로 돌아간다.
     */
    suspend fun follow(fixes: Flow<Fix>) {
        state.value = state.value.copy(following = true, started = true, nowMillis = clock())
        try {
            fixes.collect { fix ->
                val s = state.value
                val now = clock()
                val moving = fix.speedMetersPerSecond >= MOVING_SPEED
                val stillSince = if (moving) null else s.stillSince ?: now
                // 움직이면 주행, 움직이던 차가 3초 넘게 서 있으면 정차. 처음부터 서 있으면 곧바로 정차다.
                val stopped = !moving && (!s.liveMoving || now - stillSince!! >= STILL_MILLIS)
                // 평소에는 앞쪽 창 안에서 찾는다. 창 밖이면 경로 전체에서 찾아, 경로 위면 그 자리로 다시 맞춘다
                // (지난 시연의 위치가 남아 있다가 출발점으로 돌아오는 경우, 위치를 새로 넣은 경우).
                val inWindow = project(fix.latitude, fix.longitude, s.traveled - FOLLOW_WINDOW, s.traveled + FOLLOW_WINDOW)
                    ?.takeIf { (at, offset) -> offset <= OFF_ROUTE && at >= s.traveled - REANCHOR }?.first
                val anywhere = if (inWindow == null) {
                    project(fix.latitude, fix.longitude, 0.0, length)?.takeIf { (_, offset) -> offset <= OFF_ROUTE }?.first
                } else {
                    null
                }
                val traveled = when {
                    anywhere != null -> anywhere
                    inWindow != null -> maxOf(s.traveled, inWindow)
                    else -> s.traveled
                }
                // 뒤로 가거나 다시 맞춘 위치는 정차로 내보낸다. 뒤로 미끄러지는 위치로 앞의 장소를 "지나쳤다"고 보지 않기 위해서다.
                val backward = anywhere != null || (inWindow != null && inWindow < s.traveled - BACKWARD)
                val emitted = if (backward) fix.copy(speedMetersPerSecond = 0.0) else fix
                val stop = s.stop
                val arrived = stop != null && stopped && AHEAD_NEAR >= distanceMeters(fix.latitude, fix.longitude, stop.place.latitude, stop.place.longitude)
                state.value = s.copy(
                    traveled = traveled,
                    liveFix = emitted,
                    liveMoving = !stopped,
                    stillSince = stillSince,
                    arrivedStop = s.arrivedStop || arrived,
                    nowMillis = now,
                )
            }
        } finally {
            state.value = state.value.copy(following = false, liveFix = null, liveMoving = false, stillSince = null)
        }
    }

    /** 시험용: 경로 위 [meters] 지점으로 옮긴다. */
    internal fun seek(meters: Double) {
        state.value = state.value.copy(traveled = meters.coerceIn(0.0, length))
    }

    private fun targetOf(s: State): Double = s.stop?.atMeters ?: length

    private fun timeOf(s: State): Instant =
        if (s.following) Instant.ofEpochMilli(s.nowMillis) else start.plus(s.offset.toJavaDuration())

    private fun remainingOf(s: State): Duration = ((length - s.traveled) / cruise).seconds

    private fun fixOf(s: State): Fix {
        if (s.following) s.liveFix?.let { return it }
        val heading = headingAt(s.traveled)
        if (s.arrivedStop && s.stop != null) {
            return Fix(s.stop.place.latitude, s.stop.place.longitude, heading, 0.0)
        }
        val (x, y) = pointAt(s.traveled)
        // 실제 위치를 따라가는데 아직 위치가 없으면 서 있는 것으로 둔다. 재생 속도를 내보내면 엉뚱한 방향을 믿게 된다.
        val moving = !s.following && s.started && s.traveled < targetOf(s)
        return Fix(lat0 + y / metersPerDegLat, lon0 + x / metersPerDegLon, heading, if (moving) cruise else 0.0)
    }

    /** 지금 위치에서 경로를 따라 [LOOK_AHEAD]m 앞 지점을 향한 방위. 짧은 구간에서 방향이 튀지 않게 한다. */
    private fun headingAt(meters: Double): Double {
        val from = pointAt(meters)
        val ahead = (meters + LOOK_AHEAD).coerceAtMost(length)
        val to = if (ahead - meters < 1.0) {
            // 끝에서는 마지막 구간의 방향을 쓴다.
            val last = xs.lastIndex
            return bearing(xs[last] - xs[last - 1], ys[last] - ys[last - 1])
        } else {
            pointAt(ahead)
        }
        return bearing(to.first - from.first, to.second - from.second)
    }

    private fun bearing(dx: Double, dy: Double): Double = (Math.toDegrees(atan2(dx, dy)) + 360) % 360

    private fun pointAt(meters: Double): Pair<Double, Double> {
        val m = meters.coerceIn(0.0, length)
        var i = cumulative.binarySearch(m).let { if (it >= 0) it else -it - 2 }.coerceIn(0, xs.size - 2)
        while (i < xs.size - 2 && cumulative[i + 1] - cumulative[i] == 0.0) i++
        val segment = cumulative[i + 1] - cumulative[i]
        val t = if (segment == 0.0) 0.0 else (m - cumulative[i]) / segment
        return xs[i] + t * (xs[i + 1] - xs[i]) to ys[i] + t * (ys[i + 1] - ys[i])
    }

    private companion object {
        const val EARTH_RADIUS = 6_371_000.0
        const val MANEUVER_HORIZON = 600
        const val LOOK_AHEAD = 40.0
        const val FOLLOW_WINDOW = 1_000.0
        const val OFF_ROUTE = 150.0
        const val AHEAD_NEAR = 150.0
        const val MOVING_SPEED = 1.0
        const val STILL_MILLIS = 3_000L
        const val END_MARGIN = 30.0
        const val TURN_SETTLE = 60.0
        const val REANCHOR = 300.0
        const val BACKWARD = 30.0
    }
}
