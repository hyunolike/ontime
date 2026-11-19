package io.github.hyunolike.ontime.fake.track

import io.github.hyunolike.ontime.fake.RouteManeuver
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.LocationGateway
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.RoadGateway
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
) : TimeSource, LocationGateway, RoadGateway, VehicleGateway {
    private data class Stop(val atMeters: Double, val place: Destination)

    private data class State(
        val offset: Duration = Duration.ZERO,
        val traveled: Double = 0.0,
        val started: Boolean = false,
        val stop: Stop? = null,
        val arrivedStop: Boolean = false,
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
    val length: Double = cumulative.last()

    private val state = MutableStateFlow(State())

    /** 경로 위에서 달린 거리(미터). */
    val traveled: Double get() = state.value.traveled

    /** 들른 경유지에 서 있으면 true. */
    val atVisitedStop: Boolean get() = state.value.arrivedStop

    /** 목적지까지 남은 주행 시간. 경유지에서 둘러보는 시간은 넣지 않는다. */
    val remaining: Duration get() = remainingOf(state.value)

    /** [remaining]과 그 값을 계산한 재생 시각. */
    val remainingFlow: Flow<Pair<Duration, Instant>> = state.map { remainingOf(it) to timeOf(it) }.distinctUntilChanged()

    override val now: Flow<Instant> = state.map(::timeOf).distinctUntilChanged()

    override val fixes: Flow<Fix> = state.map(::fixOf).distinctUntilChanged()

    override val driving: Flow<Boolean> = state.map { it.started && it.traveled < targetOf(it) }.distinctUntilChanged()

    override val nextManeuver: Flow<Maneuver?> = state.map { s ->
        track.maneuvers
            .filter { it.atMeters > s.traveled && it.atMeters - s.traveled <= MANEUVER_HORIZON }
            .minByOrNull { it.atMeters }
            ?.let { Maneuver((it.atMeters - s.traveled).toInt(), it.instruction, it.turn) }
    }.distinctUntilChanged()

    /** 거리와 상관없이 앞에 있는 다음 회전(좌회전, 우회전). 쪽이 뒤집힐 장소를 가리는 데 쓴다. */
    val nextTurn: Flow<Maneuver?> = state.map { s ->
        track.maneuvers
            .filter { it.turn != null && it.atMeters > s.traveled }
            .minByOrNull { it.atMeters }
            ?.let { Maneuver((it.atMeters - s.traveled).toInt(), it.instruction, it.turn) }
    }.distinctUntilChanged()

    override val speedMetersPerSecond: Flow<Double> = flowOf(cruise)

    /** 경로에서 [place]와 가장 가까운 지점의 경로 위 미터. 이미 지난 곳이면 null. */
    fun canStopAt(place: Destination): Double? {
        val px = (place.longitude - lon0) * metersPerDegLon
        val py = (place.latitude - lat0) * metersPerDegLat
        var best = Double.MAX_VALUE
        var bestAt = 0.0
        for (i in 0 until xs.size - 1) {
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(xs[i] + t * dx - px, ys[i] + t * dy - py)
            if (d < best) {
                best = d
                bestAt = cumulative[i] + t * (cumulative[i + 1] - cumulative[i])
            }
        }
        return bestAt.takeIf { it > state.value.traveled }
    }

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
        while (state.value.traveled < targetOf(state.value)) {
            delay(step / playback)
            val s = state.value
            val next = (s.traveled + cruise * step.inWholeMilliseconds / 1000.0).coerceAtMost(targetOf(s))
            state.value = s.copy(offset = s.offset + step, traveled = next)
        }
        val s = state.value
        if (s.stop != null && s.traveled >= s.stop.atMeters) state.value = s.copy(arrivedStop = true)
    }

    /** 시험용: 경로 위 [meters] 지점으로 옮긴다. */
    internal fun seek(meters: Double) {
        state.value = state.value.copy(traveled = meters.coerceIn(0.0, length))
    }

    private fun targetOf(s: State): Double = s.stop?.atMeters ?: length

    private fun timeOf(s: State): Instant = start.plus(s.offset.toJavaDuration())

    private fun remainingOf(s: State): Duration = ((length - s.traveled) / cruise).seconds

    private fun fixOf(s: State): Fix {
        val heading = headingAt(s.traveled)
        if (s.arrivedStop && s.stop != null) {
            return Fix(s.stop.place.latitude, s.stop.place.longitude, heading, 0.0)
        }
        val (x, y) = pointAt(s.traveled)
        val moving = s.started && s.traveled < targetOf(s)
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
    }
}
