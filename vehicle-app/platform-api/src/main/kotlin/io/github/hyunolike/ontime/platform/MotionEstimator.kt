package io.github.hyunolike.ontime.platform

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 연속한 위치로 속도와 진행 방향을 계산한다. 위치 SDK가 방향을 주지 않거나(에뮬레이터에서 FusedLocation의
 * heading은 0) 속도를 주지 않을 때 쓴다. [minMoveMeters]보다 덜 움직였으면 정차로 보고 방향은 직전 값을 유지한다.
 */
class MotionEstimator(
    private val minMoveMeters: Double = 2.0,
    /** 한 번에 이보다 멀리 가면 순간 이동(위치를 새로 넣음)으로 보고 속도와 방향을 새로 시작한다. */
    private val jumpMeters: Double = 1_000.0,
) {
    private var last: Triple<Double, Double, Long>? = null
    private var heading: Double? = null

    fun next(latitude: Double, longitude: Double, atMillis: Long): Fix {
        val previous = last
        last = Triple(latitude, longitude, atMillis)
        if (previous == null) return Fix(latitude, longitude, heading, 0.0)
        val (lat0, lon0, t0) = previous
        val meters = distance(lat0, lon0, latitude, longitude)
        val seconds = (atMillis - t0) / 1000.0
        if (meters > jumpMeters) {
            heading = null
            return Fix(latitude, longitude, null, 0.0)
        }
        if (meters < minMoveMeters || seconds <= 0) return Fix(latitude, longitude, heading, 0.0)
        heading = bearing(lat0, lon0, latitude, longitude)
        return Fix(latitude, longitude, heading, meters / seconds)
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_000.0 * asin(sqrt(h))
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
