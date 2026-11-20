package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryLengthTest {
    // 60자 남짓: 초당 6자로 약 10초.
    private val long = "저 둥근 언덕들은 신라 시대 무덤이에요. 그중 천마총은 봉분 안을 볼 수 있어요. 천마도가 발견된 곳이죠."
    private fun poi(id: String, short: String = "") =
        Poi(Destination(id, id, 0.0, 0.0), long, canStop = true, source = Source.REPLAY, shortStory = short)
    private fun ahead(poi: Poi, meters: Int, settled: Boolean = true) = PoiAhead(poi, meters, Side.RIGHT, settled)

    private val speed = 12.0

    @Test
    fun `여유가 있으면 기본 해설이다`() {
        assertEquals(long, pickStory(ahead(poi("a"), 2_500), speed, nextManeuver = null, others = emptyList()))
    }

    @Test
    fun `그곳까지 1분이 남지 않았으면 짧은 해설이다`() {
        assertEquals("짧게.", pickStory(ahead(poi("a", "짧게."), 700), speed, null, emptyList()))
    }

    @Test
    fun `짧은 해설이 없으면 기본 해설의 첫 문장이다`() {
        assertEquals("저 둥근 언덕들은 신라 시대 무덤이에요.", pickStory(ahead(poi("a"), 700), speed, null, emptyList()))
    }

    @Test
    fun `소수점에서는 문장을 끊지 않는다`() {
        val poi = Poi(Destination("a", "a", 0.0, 0.0), "성벽이 1.2km 이어져요. 걸어서 돌 수 있어요.", canStop = false, source = Source.REPLAY)
        assertEquals("성벽이 1.2km 이어져요.", pickStory(ahead(poi, 700), speed, null, emptyList()))
    }

    @Test
    fun `다음 회전까지가 읽는 시간보다 짧으면 짧은 해설이다`() {
        val turn = Maneuver(100, "우회전")
        assertEquals("짧게.", pickStory(ahead(poi("a", "짧게."), 2_500), speed, turn, emptyList()))
    }

    @Test
    fun `읽는 동안 다른 곳이 소개 범위에 들어오면 짧은 해설이다`() {
        val next = ahead(poi("b"), 3_050)
        assertEquals("짧게.", pickStory(ahead(poi("a", "짧게."), 2_500), speed, null, listOf(next)))
        // 아직 소개할 수 없는 곳은 세지 않는다.
        assertEquals(long, pickStory(ahead(poi("a", "짧게."), 2_500), speed, null, listOf(next.copy(settled = false))))
    }
}
