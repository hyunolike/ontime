package io.github.hyunolike.ontime.tour

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 관광공사 위치 기반 조회 결과 한 곳. */
data class TourPlace(val contentId: String, val title: String, val latitude: Double, val longitude: Double, val address: String)

/** 응답 본문 캐시. 통신이 끊겨도 같은 경로의 장소를 다시 쓰기 위해 둔다. */
interface TextCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MemoryCache : TextCache {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    fun keys(): Set<String> = map.keys
}

/** 파일 하나에 응답 하나. 파일 이름은 요청(서비스 키 제외)의 해시다. */
class FileCache(private val dir: File) : TextCache {
    override fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()
    override fun put(key: String, value: String) {
        dir.mkdirs()
        file(key).writeText(value)
    }

    private fun file(key: String) = File(dir, sha1(key) + ".json")
    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * 한국관광공사 TourAPI(KorService2) 클라이언트.
 *
 * 응답 형식은 KorService1과 같다고 보고 읽는다: `response.body.items.item`이 배열이거나(여러 곳) 객체이고(한 곳),
 * 결과가 없으면 `items`가 빈 문자열이다. 실제 키로 받은 응답과 대조해 확인해야 한다(docs/features/docent-plus).
 *
 * @param http URL을 받아 본문을 돌려준다. 시험에서는 고정 응답을 넣는다.
 */
class TourApiClient(
    private val serviceKey: String,
    private val http: (String) -> String = ::httpGet,
    private val cache: TextCache? = null,
) {
    /** 관광지(콘텐츠 유형 12) 가운데 [radiusMeters] 안에 있는 곳을 가까운 순서로. */
    fun nearby(latitude: Double, longitude: Double, radiusMeters: Int = 1_000, rows: Int = 10): List<TourPlace> {
        val body = fetch(
            "locationBasedList2",
            "mapX" to "$longitude", "mapY" to "$latitude", "radius" to "$radiusMeters",
            "contentTypeId" to "12", "arrange" to "E", "numOfRows" to "$rows", "pageNo" to "1",
        )
        return items(body).mapNotNull { item ->
            val id = item.text("contentid") ?: return@mapNotNull null
            val title = item.text("title") ?: return@mapNotNull null
            val lat = item.text("mapy")?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.text("mapx")?.toDoubleOrNull() ?: return@mapNotNull null
            TourPlace(id, title, lat, lon, item.text("addr1").orEmpty())
        }
    }

    /** 공통정보의 개요. HTML 줄바꿈은 공백으로 바꾼다. */
    fun overview(contentId: String): String? {
        val body = fetch("detailCommon2", "contentId" to contentId)
        return items(body).firstOrNull()?.text("overview")
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun fetch(operation: String, vararg params: Pair<String, String>): String {
        val query = (listOf("MobileOS" to "AND", "MobileApp" to "Gilbeot", "_type" to "json") + params)
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        // 캐시 열쇠에는 서비스 키를 넣지 않는다.
        val key = "$operation?$query"
        val url = "$BASE/$operation?serviceKey=$serviceKey&$query"
        return runCatching { http(url) }
            .onSuccess { cache?.put(key, it) }
            .getOrElse { error -> cache?.get(key) ?: throw error }
    }

    private fun items(body: String): List<JsonObject> {
        val item = Json.parseToJsonElement(body).jsonObject["response"]?.obj()?.get("body")?.obj()
            ?.get("items")?.obj()?.get("item") ?: return emptyList()
        return when (item) {
            is JsonArray -> item.mapNotNull { it.obj() }
            is JsonObject -> listOf(item)
            else -> emptyList()
        }
    }

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val BASE = "https://apis.data.go.kr/B551011/KorService2"
        const val TIMEOUT_MS = 5_000

        fun httpGet(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            return try {
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
        }
    }
}
