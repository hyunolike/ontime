package io.github.hyunolike.ontime.platform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotionEstimatorTest {
    private val lat0 = 37.45
    private val lon0 = 127.10
    private fun north(m: Double) = m / 111_195.0
    private fun east(m: Double) = m / (111_195.0 * kotlin.math.cos(Math.toRadians(lat0)))

    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected±$tolerance but was $actual")

    @Test
    fun `첫 위치는 방향을 모르고 속도는 0이다`() {
        val fix = MotionEstimator().next(lat0, lon0, atMillis = 0)
        assertNull(fix.heading)
        assertEquals(0.0, fix.speedMetersPerSecond)
    }

    @Test
    fun `북쪽으로 1초에 50m 가면 진행 방향 0도, 초속 50m다`() {
        val estimator = MotionEstimator()
        estimator.next(lat0, lon0, 0)
        val fix = estimator.next(lat0 + north(50.0), lon0, 1_000)
        near(0.0, fix.heading!!, 0.5)
        near(50.0, fix.speedMetersPerSecond, 0.5)
    }

    @Test
    fun `동쪽으로 가면 90도다`() {
        val estimator = MotionEstimator()
        estimator.next(lat0, lon0, 0)
        near(90.0, estimator.next(lat0, lon0 + east(30.0), 500).heading!!, 0.5)
    }

    @Test
    fun `1km 넘게 튀면 순간 이동으로 보고 방향과 속도를 새로 시작한다`() {
        val estimator = MotionEstimator()
        estimator.next(lat0, lon0, 0)
        estimator.next(lat0 + north(50.0), lon0, 1_000)
        val jumped = estimator.next(lat0 + north(50_000.0), lon0, 2_000)
        assertNull(jumped.heading)
        assertEquals(0.0, jumped.speedMetersPerSecond)
    }

    @Test
    fun `2m 미만으로 움직이면 정차로 보고 방향은 직전 값을 유지한다`() {
        val estimator = MotionEstimator()
        estimator.next(lat0, lon0, 0)
        estimator.next(lat0, lon0 + east(30.0), 1_000)
        val still = estimator.next(lat0 + north(1.0), lon0 + east(30.0), 2_000)
        assertEquals(0.0, still.speedMetersPerSecond)
        near(90.0, still.heading!!, 0.5)
    }
}
