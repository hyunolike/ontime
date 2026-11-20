package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.Poi
import kotlin.math.ceil

/** 걸어서 둘러볼 곳 하나와 걸리는 시간(분). */
data class Walkable(val poi: Poi, val walkMinutes: Int)

/**
 * 차를 세운 곳에서 걸어서 갈 만한 곳. 직선 [withinMeters] 안을 가까운 순서로 [limit]곳까지 고른다.
 * 걷는 시간은 직선 거리에 도로 굴곡 [detour]배를 곱하고 보행 속도 [walkSpeed](m/s)로 나눈다.
 */
fun nearbyOnFoot(
    fix: Fix,
    catalog: List<Poi>,
    withinMeters: Int = 1_200,
    limit: Int = 3,
    detour: Double = 1.3,
    walkSpeed: Double = 1.2,
): List<Walkable> = catalog
    .map { it to AheadTracker.distanceMeters(fix.latitude, fix.longitude, it.destination.latitude, it.destination.longitude) }
    .filter { (_, d) -> d <= withinMeters }
    .sortedBy { (_, d) -> d }
    .take(limit)
    .map { (poi, d) -> Walkable(poi, ceil(d * detour / walkSpeed / 60).toInt().coerceAtLeast(1)) }
