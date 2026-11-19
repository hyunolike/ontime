package io.github.hyunolike.ontime.fake.track

import kotlin.test.Test
import kotlin.test.assertEquals

class PolylineTest {
    @Test
    fun `구글 문서의 예시 문자열을 푼다`() {
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), points)
    }

    @Test
    fun `빈 문자열은 빈 목록이다`() {
        assertEquals(emptyList(), Polyline.decode(""))
    }
}
