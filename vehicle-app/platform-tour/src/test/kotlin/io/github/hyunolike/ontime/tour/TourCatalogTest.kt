package io.github.hyunolike.ontime.tour

import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.slack.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TourCatalogTest {
    // 한국관광공사 KorService 응답 형식(response.body.items.item[]). 실제 키로 받은 응답과 대조해야 한다.
    private fun list(vararg items: String) = """
        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
         "body":{"items":{"item":[${items.joinToString(",")}]},"numOfRows":10,"pageNo":1,"totalCount":${items.size}}}}
    """.trimIndent()

    private fun item(id: String, title: String, lat: Double, lon: Double, image: String = "") =
        """{"addr1":"서울특별시 송파구","contentid":"$id","contenttypeid":"12","dist":"300.1","firstimage":"$image","mapx":"$lon","mapy":"$lat","title":"$title"}"""

    private val emptyList = """{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":10,"pageNo":1,"totalCount":0}}}"""

    private fun overview(text: String) =
        """{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[{"contentid":"1","overview":"$text"}]}}}}"""

    private val seokchon = Poi(Destination("songpa-seokchon", "석촌동 고분군", 37.5024456, 127.1022937), "", canStop = true, source = Source.REPLAY)

    /** URL에 따라 정해 둔 응답을 돌려주고, 부른 URL을 기록한다. */
    private class FakeHttp(private val answer: (String) -> String) : (String) -> String {
        val calls = mutableListOf<String>()
        override fun invoke(url: String): String {
            calls += url
            return answer(url)
        }
    }

    private val routePoints = listOf(37.41 to 127.09, 37.43 to 127.10, 37.50 to 127.10)

    @Test
    fun `경로 지점마다 관광지를 찾아 개요 앞 두 문장을 해설로 쓴다`() = runTest {
        val http = FakeHttp { url ->
            when {
                "locationBasedList2" in url && "mapY=37.5" in url -> list(item("100", "올림픽공원", 37.5203, 127.1214))
                "locationBasedList2" in url -> emptyList
                "detailCommon2" in url -> overview("1988년 서울올림픽을 위해 만든 공원이에요.<br>몽촌토성이 공원 안에 있어요. 조각 작품도 많아요.")
                else -> error(url)
            }
        }
        val places = TourCatalog(TourApiClient("KEY", http)).load(routePoints, existing = listOf(seokchon))
        val park = places.single()
        assertEquals("tour-100", park.destination.id)
        assertEquals("올림픽공원", park.destination.name)
        assertEquals(37.5203, park.destination.latitude)
        assertEquals("1988년 서울올림픽을 위해 만든 공원이에요. 몽촌토성이 공원 안에 있어요.", park.story)
        assertEquals(Source.LIVE, park.source)
        assertTrue(http.calls.all { "serviceKey=KEY" in it && "_type=json" in it })
        assertTrue(http.calls.first().contains("KorService2/locationBasedList2"))
    }

    @Test
    fun `이미 있는 장소와 이름이 같거나 150m 안이면 더하지 않는다`() = runTest {
        val http = FakeHttp { url ->
            when {
                "locationBasedList2" in url -> list(
                    item("1", "석촌동 고분군", 37.5030, 127.1030),
                    item("2", "석촌동고분군 주차장 옆 공원", 37.50250, 127.10235),
                    item("3", "송파책박물관", 37.4988875, 127.1044813),
                )
                else -> overview("책을 주제로 한 박물관이에요.")
            }
        }
        val places = TourCatalog(TourApiClient("KEY", http)).load(routePoints.take(1), existing = listOf(seokchon))
        assertEquals(listOf("송파책박물관"), places.map { it.destination.name })
    }

    @Test
    fun `더하는 곳은 최대 개수까지이고 조회 수도 제한한다`() = runTest {
        var n = 0
        val http = FakeHttp { url ->
            if ("locationBasedList2" in url) {
                list(*Array(10) { i -> n++; item("$n", "장소$n", 37.0 + n * 0.01, 127.0) })
            } else {
                overview("설명이에요.")
            }
        }
        val many = List(20) { 37.0 + it * 0.02 to 127.0 }
        val places = TourCatalog(TourApiClient("KEY", http), maxPoints = 7, maxPlaces = 6).load(many, existing = emptyList())
        assertEquals(6, places.size)
        assertEquals(7, http.calls.count { "locationBasedList2" in it })
        assertEquals(6, http.calls.count { "detailCommon2" in it })
    }

    @Test
    fun `조회가 실패하면 빈 목록이고 예외를 밖으로 던지지 않는다`() = runTest {
        val http = FakeHttp { throw java.io.IOException("offline") }
        assertEquals(emptyList(), TourCatalog(TourApiClient("KEY", http)).load(routePoints, existing = emptyList()))
    }

    @Test
    fun `한 번 받은 응답은 캐시에 두고 통신이 끊겨도 다시 쓴다`() = runTest {
        val cache = MemoryCache()
        val online = FakeHttp { url ->
            if ("locationBasedList2" in url) list(item("7", "몽촌역사관", 37.52, 127.115)) else overview("백제 유물을 보여 줘요.")
        }
        val first = TourCatalog(TourApiClient("KEY", online, cache)).load(routePoints.take(1), existing = emptyList())
        val offline = FakeHttp { throw java.io.IOException("offline") }
        val second = TourCatalog(TourApiClient("KEY", offline, cache)).load(routePoints.take(1), existing = emptyList())
        assertEquals(first, second)
        assertEquals(1, second.size)
        // 캐시 열쇠에는 서비스 키가 들어가지 않는다.
        assertTrue(cache.keys().none { "KEY" in it })
    }

    @Test
    fun `결과가 하나뿐이면 배열이 아니라 객체로 와도 읽는다`() = runTest {
        val single = """{"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":${item("9", "풍납토성", 37.528, 127.112)}}}}}"""
        val http = FakeHttp { url -> if ("locationBasedList2" in url) single else overview("백제의 성이에요.") }
        assertEquals(listOf("풍납토성"), TourCatalog(TourApiClient("KEY", http)).load(routePoints.take(1), emptyList()).map { it.destination.name })
    }
}
