package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.fake.track.TrackPlayer
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioSongpaTest {
    @Test
    fun `경로는 내비 출발점에서 시작해 한성백제박물관에서 끝나고 길이는 OSRM 값과 같다`() {
        val points = ScenarioSongpa.track.points
        assertEquals(315, points.size)
        assertTrue(abs(points.first().latitude - 37.4127) < 0.001 && abs(points.first().longitude - 127.0947) < 0.001)
        assertTrue(abs(points.last().latitude - ScenarioSongpa.HANSEONG.latitude) < 0.001)
        assertTrue(abs(points.last().longitude - ScenarioSongpa.HANSEONG.longitude) < 0.001)
        val length = TrackPlayer(ScenarioSongpa.track, Instant.EPOCH).length
        assertTrue(abs(length - 14_293) < 50, "length=$length")
    }
}
