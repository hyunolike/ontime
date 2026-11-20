package io.github.hyunolike.ontime.webexport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 웹 체험판이 기대하는 기록의 성질. 웹은 갈림길에서 다른 기록의 같은 프레임으로 넘어가므로 정렬이 맞아야 한다. */
class ExportTest {
    private val result = Exporter().exportAll()
    private fun frames(key: String): List<JsonObject> = result.paths.getValue(key)["frames"]!!.jsonArray.map { it.jsonObject }

    private fun askIndex(frames: List<JsonObject>, id: String) = frames.indexOfFirst { f ->
        (f["n"] as? JsonObject)?.let { it["id"]!!.jsonPrimitive.content == id && it["ph"]!!.jsonPrimitive.content == "ASKING" } == true
    }

    @Test
    fun `갈래는 답하는 순간까지 프레임이 같다`() {
        listOf(
            Triple("none", "seokchon", "seokchon"),
            Triple("none", "bangi", "bangi"),
            Triple("seokchon", "bangi+seokchon", "bangi"),
            Triple("g-seokchon", "g-seokchon+bangi", "bangi"),
        ).forEach { (base, branch, id) ->
            val a = frames(base)
            val b = frames(branch)
            val ask = askIndex(a, id)
            assertTrue(ask > 0, "$base 에서 $id 질문이 없음")
            val diverge = a.zip(b).indexOfFirst { (x, y) -> x != y }
            assertTrue(diverge >= ask + ANSWER_FRAMES - 1, "$base → $branch: 질문 $ask, 갈라짐 $diverge")
        }
    }

    @Test
    fun `모든 갈래가 지날 때의 쪽으로 소개하고 목적지에서 끝난다`() {
        result.paths.keys.forEach { key ->
            val f = frames(key)
            val sides = f.mapNotNull { it["n"] as? JsonObject }
                .filter { it["ph"]!!.jsonPrimitive.content == "TELLING" }
                .associate { it["id"]!!.jsonPrimitive.content to it["side"]!!.jsonPrimitive.content }
            assertEquals(mapOf("seoul-airbase" to "R", "seokchon" to "L", "bangi" to "L", "mongchon" to "L"), sides, key)
            assertEquals("end", f.last()["stop"]!!.jsonPrimitive.content, key)
        }
    }

    @Test
    fun `예라고 답한 곳마다 경유지에 서서 도장을 찍는다`() {
        assertEquals(listOf("seokchon", "bangi"), (frames("bangi+seokchon").last()["st"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(2, frames("bangi+seokchon").count { it["stop"]?.jsonPrimitive?.content == "waypoint" })
        assertEquals(0, (frames("none").last()["st"] as JsonArray).size)
    }
}
