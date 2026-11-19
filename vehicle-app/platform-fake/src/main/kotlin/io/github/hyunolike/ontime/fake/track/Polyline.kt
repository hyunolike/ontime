package io.github.hyunolike.ontime.fake.track

data class LatLon(val latitude: Double, val longitude: Double)

/** 구글 polyline 인코딩(정밀도 5)을 푼다. OSRM이 경로 모양을 이 형식으로 준다. */
object Polyline {
    fun decode(encoded: String): List<LatLon> {
        val points = mutableListOf<LatLon>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLon, afterLon) = readValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            points += LatLon(lat / 1e5, lon / 1e5)
        }
        return points
    }

    private fun readValue(encoded: String, start: Int): Pair<Int, Int> {
        var index = start
        var result = 0
        var shift = 0
        while (true) {
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f) shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return value to index
    }
}
