package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.LocationGateway
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.platform.StoryGateway
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * 위치와 진행 방향으로 앞에 있는 장소를 고른다. 경로를 모르므로 거리는 직선 거리다.
 *
 * - 아직 지나지 않은 곳은 모두 목록에 넣는다. 그중 쪽을 믿고 소개해도 되는 곳만 `settled`로 둔다.
 *   진행 방향 앞쪽 ±[cone]도, [radius] 안이고, [undecidedBeyond]m보다 멀면서 정면 ±[undecided]도 안에 있지 않은 곳이다.
 *   거의 정면에 있는 먼 곳은 길이 어느 쪽으로 굽느냐에 따라 왼쪽으로도 오른쪽으로도 지날 수 있다.
 * - [near] 안이면 지나친 곳이라도 넣는다. 들른 곳에 차를 세웠을 때 옆이나 뒤에 있어도 목록에 있어야 방문 도장을 찍을 수 있다.
 * - 뒤쪽(±[behind]도 밖)에 있고, 가장 가까웠을 때보다 [receding]m 넘게 멀어졌으면 지나친 것으로 보고 다시 넣지 않는다.
 *   길이 굽어 잠깐 다시 앞에 보이는 곳을 두 번 소개하지 않기 위해서다. 멀어지는지까지 보는 것은 골목에서 방향이
 *   잠깐 크게 바뀔 때 아직 지나지 않은 곳을 빼지 않기 위해서다.
 * - 속도가 [minSpeed] 미만이면 진행 방향을 믿지 않고 마지막으로 믿을 수 있었던 방향을 쓴다.
 *   그런 방향이 아직 없으면(출발 전) 모든 장소를 쪽이 정해지지 않은 채로 가까운 순서로 내보낸다.
 * - 쪽은 지금 진행 방향 기준이라, 앞에 회전이 있으면 회전 뒤에 뒤집힐 수 있다. 우회전을 앞두고 회전 지점보다 먼
 *   오른쪽 장소는 꺾은 뒤 왼쪽이 될 수 있다(좌회전이면 반대). 이런 곳은 회전을 지날 때까지 소개하지 않는다.
 *   회전 반대쪽 장소는 꺾어도 쪽이 바뀌지 않는다. 또 이 목록을 쓰는 도슨트는 회전 600m 앞부터 새 이야기를
 *   꺼내지 않는다(`DocentSession`의 `quietWithin`).
 */
class AheadTracker(
    private var catalog: List<Poi>,
    private val cone: Double = 70.0,
    private val behind: Double = 100.0,
    private val radius: Int = 5_000,
    private val near: Int = 150,
    private val receding: Int = 200,
    private val undecided: Double = 15.0,
    private val undecidedBeyond: Int = 1_500,
    private val minSpeed: Double = 1.0,
) {
    private var trusted: Double? = null
    private val passed = mutableSetOf<String>()

    /** 가장 가까웠던 거리. */
    private val closest = mutableMapOf<String, Int>()

    /** 지금 쓰는 장소 목록. */
    val places: List<Poi> get() = catalog

    /**
     * 장소 목록을 바꾼다(관광공사 데이터가 늦게 도착한 경우). 지나친 곳과 가장 가까웠던 거리는 id로 기억하므로 유지된다.
     * [update]와 같은 스레드에서 불러야 한다.
     */
    fun setCatalog(places: List<Poi>) {
        catalog = places
    }

    /** 지나친 곳과 진행 방향 기억을 지운다. 같은 길을 처음부터 다시 달릴 때 쓴다. */
    fun reset() {
        trusted = null
        passed.clear()
        closest.clear()
    }

    /** [nextTurn]은 앞에 있는 다음 좌회전이나 우회전. 모르면 null. */
    fun update(fix: Fix, nextTurn: Maneuver? = null): List<PoiAhead> {
        if (fix.heading != null && fix.speedMetersPerSecond >= minSpeed) trusted = fix.heading
        val heading = trusted
        val measured = catalog.map { poi ->
            val distance = distanceMeters(fix.latitude, fix.longitude, poi.destination.latitude, poi.destination.longitude)
            val bearing = bearingDegrees(fix.latitude, fix.longitude, poi.destination.latitude, poi.destination.longitude)
            val delta = relative(bearing, heading ?: fix.heading ?: 0.0)
            Measured(poi, distance, delta)
        }
        if (heading == null) return measured.sortedBy { it.distance }.map { it.toAhead(settled = false) }

        val result = mutableListOf<PoiAhead>()
        for (m in measured) {
            val id = m.poi.destination.id
            val nearest = minOf(closest[id] ?: m.distance, m.distance).also { closest[id] = it }
            when {
                m.distance <= near -> result += m.toAhead(settled = true)
                id in passed -> Unit
                abs(m.delta) > behind && m.distance > nearest + receding -> passed += id
                else -> {
                    val decided = !(abs(m.delta) < undecided && m.distance > undecidedBeyond)
                    val ahead = m.toAhead(settled = false)
                    // 꺾는 쪽에 있고 회전 지점보다 멀면 꺾은 뒤 쪽이 뒤집힐 수 있다.
                    val forward = m.distance * cos(Math.toRadians(m.delta))
                    val flips = nextTurn?.turn == ahead.side && forward > nextTurn.distanceMeters
                    result += ahead.copy(settled = abs(m.delta) <= cone && m.distance <= radius && decided && !flips)
                }
            }
        }
        return result.sortedBy { it.distanceMeters }
    }

    private data class Measured(val poi: Poi, val distance: Int, val delta: Double) {
        fun toAhead(settled: Boolean) = PoiAhead(poi, distance, if (delta < 0) Side.LEFT else Side.RIGHT, settled)
    }

    companion object {
        private const val EARTH_RADIUS = 6_371_000.0

        /** 하버사인 공식으로 구한 두 점 사이의 거리(미터). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
            return (2 * EARTH_RADIUS * asin(sqrt(a))).roundToInt()
        }

        /** 첫 점에서 둘째 점을 향한 방위(북쪽 0도, 시계 방향, 0 이상 360 미만). */
        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lon2 - lon1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }

        /** [bearing]이 [heading]에서 얼마나 돌아가 있는지. -180 이상 180 미만이고 음수면 왼쪽이다. */
        fun relative(bearing: Double, heading: Double): Double = ((bearing - heading + 540) % 360) - 180
    }
}

/**
 * 위치가 바뀔 때마다 앞에 있는 장소를 다시 고른다.
 * [tracker]가 상태를 가지므로 [ahead]는 한 곳에서만 수집해야 한다. 지금은 `DocentSession` 하나다.
 */
class LiveStories(
    location: LocationGateway,
    private val tracker: AheadTracker,
    /** 앞에 있는 다음 좌회전이나 우회전. 실차는 내비의 회전 목록에서 얻는다. */
    nextTurn: Flow<Maneuver?> = flowOf(null),
) : StoryGateway {
    override val ahead: Flow<List<PoiAhead>> =
        combine(location.fixes, nextTurn) { fix, turn -> tracker.update(fix, turn) }.distinctUntilChanged()
}
