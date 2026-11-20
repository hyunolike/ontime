package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.platform.Fix
import kotlin.test.Test
import kotlin.test.assertEquals

class OnFootTest {
    private val atMuseum = Fix(ScenarioSongpa.HANSEONG.latitude, ScenarioSongpa.HANSEONG.longitude, null, 0.0)

    @Test
    fun `한성백제박물관에서는 몽촌토성과 방이동 고분군이 걸어서 갈 곳이고 석촌동 고분군은 멀다`() {
        val walkable = nearbyOnFoot(atMuseum, ScenarioSongpa.pois)
        assertEquals(listOf(ScenarioSongpa.MONGCHON, ScenarioSongpa.BANGI), walkable.map { it.poi.destination })
        // 777m와 908m. 굴곡 1.3배, 초속 1.2m로 15분, 17분.
        assertEquals(listOf(15, 17), walkable.map { it.walkMinutes })
    }

    @Test
    fun `가까운 순서로 최대 개수까지만 고른다`() {
        assertEquals(1, nearbyOnFoot(atMuseum, ScenarioSongpa.pois, limit = 1).size)
    }
}
