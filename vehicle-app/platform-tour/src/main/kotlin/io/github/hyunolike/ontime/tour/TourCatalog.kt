package io.github.hyunolike.ontime.tour

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.slack.Source
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 경로를 따라 관광공사 관광지를 모아 도슨트 장소로 바꾼다.
 *
 * 조회 수를 묶어 둔다: 경로 지점은 [maxPoints]곳까지, 개요는 [maxPlaces]곳까지. 이미 있는 장소와 이름이 같거나
 * [nearMeters] 안이면 더하지 않는다. 조회가 실패하면 그 지점은 건너뛰고, 모두 실패하면 빈 목록이다.
 */
class TourCatalog(
    private val client: TourApiClient,
    private val maxPoints: Int = 7,
    private val maxPlaces: Int = 6,
    private val nearMeters: Int = 150,
) {
    /** [points]는 경로 위의 (위도, 경도). 경로 순서대로 준다. */
    fun load(points: List<Pair<Double, Double>>, existing: List<Poi>): List<Poi> {
        val found = mutableListOf<TourPlace>()
        for ((lat, lon) in spread(points, maxPoints)) {
            val nearby = runCatching { client.nearby(lat, lon) }.getOrDefault(emptyList())
            for (place in nearby) {
                if (isDuplicate(place, existing.map { it.destination }, found)) continue
                found += place
            }
        }
        return found.take(maxPlaces).mapNotNull { place ->
            val overview = runCatching { client.overview(place.contentId) }.getOrNull() ?: return@mapNotNull null
            Poi(
                Destination("tour-${place.contentId}", place.title, place.latitude, place.longitude, place.address),
                story = sentences(overview, 2),
                canStop = true,
                source = Source.LIVE,
                stayMinutes = 30,
                shortStory = sentences(overview, 1),
            )
        }
    }

    private fun isDuplicate(place: TourPlace, existing: List<Destination>, found: List<TourPlace>): Boolean {
        val name = place.title.replace(" ", "")
        return existing.any { it.name.replace(" ", "") == name || meters(it.latitude, it.longitude, place.latitude, place.longitude) <= nearMeters } ||
            found.any { it.contentId == place.contentId || it.title.replace(" ", "") == name || meters(it.latitude, it.longitude, place.latitude, place.longitude) <= nearMeters }
    }

    private companion object {
        /** 지점이 많으면 고르게 [n]곳을 뽑는다. */
        fun <T> spread(list: List<T>, n: Int): List<T> =
            if (list.size <= n) list else List(n) { i -> list[i * (list.size - 1) / (n - 1).coerceAtLeast(1)] }

        fun sentences(text: String, n: Int): String =
            text.split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }.take(n).joinToString(" ")

        fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * 6_371_000.0 * asin(sqrt(h))
        }
    }
}
