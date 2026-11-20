package io.github.hyunolike.ontime.webexport

import io.github.hyunolike.ontime.docent.AheadTracker
import io.github.hyunolike.ontime.docent.DocentSession
import io.github.hyunolike.ontime.docent.DocentState
import io.github.hyunolike.ontime.docent.LiveStories
import io.github.hyunolike.ontime.docent.nearbyOnFoot
import io.github.hyunolike.ontime.fake.ScenarioSongpa
import io.github.hyunolike.ontime.fake.track.ReplayBackedNavi
import io.github.hyunolike.ontime.fake.track.ReplayVehicle
import io.github.hyunolike.ontime.fake.track.TrackPlayer
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.Source
import java.io.File
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 웹 체험판이 재생할 송파 주행 기록을 만든다. 판단은 앱의 코드가 한다(docs/features/web-demo).
 *
 * 갈림길(들를지 묻는 순간, 출발 전 "여기로 안내")의 선택마다 기록 하나를 만든다. 선택 전까지의 프레임은 모든 기록이
 * 같으므로, 웹은 갈림길에서 고른 기록의 같은 프레임으로 넘어가면 된다.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "web/data").apply { mkdirs() }
    val result = Exporter().exportAll()
    File(out, "places.json").writeText(result.places.toString())
    result.paths.forEach { (key, path) -> File(out, "path-$key.json").writeText(path.toString()) }
    File(out, "index.json").writeText(
        buildJsonObject {
            putJsonArray("paths") { result.paths.keys.sorted().forEach { add(JsonPrimitive(it)) } }
            put("frameSeconds", FRAME_SIM_SECONDS)
            put("frameMillis", FRAME_REAL_MILLIS)
            put("answerFrames", ANSWER_FRAMES)
        }.toString(),
    )
    println("기록 ${result.paths.size}개를 $out 에 썼습니다: ${result.paths.keys.sorted()}")
}

/** 출발 전에 경유지로 넣을 곳([guide])과 질문에 "예"라고 답할 곳([yes]). */
data class Plan(val guide: String?, val yes: Set<String>) {
    /** 파일 이름. 예: "none", "seokchon", "g-bangi+seokchon". */
    val key: String
        get() {
            val parts = yes.map(::short).sorted().joinToString("+").ifEmpty { "none" }
            return if (guide != null) "g-${short(guide)}" + (if (yes.isEmpty()) "" else "+$parts") else parts
        }

    companion object {
        fun short(id: String) = id.removePrefix("songpa-")
    }
}

class ExportResult(val places: JsonObject, val paths: Map<String, JsonObject>)

/** 한 번의 주행. 프레임과, 이 주행에서 들를지 물은 곳의 id. */
class Drive(val frames: JsonArray, val asked: List<String>)

class Exporter {
    private val pois = ScenarioSongpa.pois

    fun exportAll(): ExportResult {
        val paths = linkedMapOf<String, JsonObject>()
        fun explore(plan: Plan) {
            if (plan.key in paths) return
            val drive = simulate(plan)
            paths[plan.key] = buildJsonObject {
                put("key", plan.key)
                put("guide", plan.guide?.let(Plan::short))
                putJsonArray("yes") { plan.yes.map(Plan::short).sorted().forEach { add(JsonPrimitive(it)) } }
                put("frames", drive.frames)
            }
            // 이 주행에서 물은 곳마다 "예"라고 답한 갈래를 만든다.
            drive.asked.filter { it !in plan.yes }.forEach { explore(plan.copy(yes = plan.yes + it)) }
        }
        explore(Plan(null, emptySet()))
        pois.filter { it.canStop }.forEach { explore(Plan(it.destination.id, emptySet())) }
        return ExportResult(places(), paths)
    }

    /** 장소, 경로 모양, 회전. 화면과 지도가 쓴다. */
    fun places(): JsonObject = buildJsonObject {
        val photos = mapOf(
            ScenarioSongpa.SEOKCHON.id to ("photo_seokchon.jpg" to "Saigen Jiro, CC0"),
            ScenarioSongpa.BANGI.id to ("photo_bangi.jpg" to "Gary Todd, CC0"),
            ScenarioSongpa.MONGCHON.id to ("photo_mongchon.jpg" to "Saigen Jiro, CC0"),
            ScenarioSongpa.HANSEONG.id to ("photo_mongchon.jpg" to "몽촌토성, Saigen Jiro, CC0"),
        )
        fun place(d: Destination, poi: Poi?) = buildJsonObject {
            put("id", Plan.short(d.id))
            put("name", d.name)
            put("lat", d.latitude)
            put("lon", d.longitude)
            poi?.let {
                put("story", it.story)
                put("highlight", it.highlight)
                put("canStop", it.canStop)
                it.stayMinutes?.let { m -> put("stay", m) }
                it.extraMinutes?.let { m -> put("extra", m) }
            }
            photos[d.id]?.let { (file, credit) ->
                put("photo", file)
                put("credit", credit)
            }
        }
        putJsonArray("places") { pois.forEach { add(place(it.destination, it)) } }
        put("destination", place(ScenarioSongpa.HANSEONG, null))
        putJsonArray("route") {
            ScenarioSongpa.track.points.forEach { p -> add(buildJsonArray { add(JsonPrimitive(p.latitude)); add(JsonPrimitive(p.longitude)) }) }
        }
        putJsonArray("maneuvers") {
            ScenarioSongpa.track.maneuvers.forEach { m ->
                add(buildJsonObject { put("at", m.atMeters); put("text", m.instruction); put("turn", m.turn?.name) })
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun simulate(plan: Plan): Drive {
        var drive: Drive? = null
        runTest(timeout = 10.seconds.times(60)) { drive = record(plan) }
        return drive!!
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.record(plan: Plan): Drive {
        val player = TrackPlayer(ScenarioSongpa.track, Instant.parse("2026-12-05T01:20:00Z"))
        val clock = { Instant.ofEpochMilli(testScheduler.currentTime) }
        val navi = ReplayBackedNavi(AcceptingNavi(ScenarioSongpa.HANSEONG, clock), player, clock = clock)
        val tracker = AheadTracker(pois, passedOnRoute = { player.hasPassed(it.destination) })
        val asked = mutableListOf<String>()
        val speech = PlanSpeech(plan.yes, pois, asked)
        // pleos 빌드와 같은 조합이다(회전 600m, 좌석 안내, 질문 10초).
        val session = DocentSession(
            player, navi, ReplayVehicle(player), speech, player, LiveStories(player, tracker, player.nextTurn),
            quietWithin = 600, seatHint = true, walkFrom = player, catalog = { pois }, askWindow = 10.seconds,
        )
        backgroundScope.launch { session.run() }
        runCurrent()

        val frames = mutableListOf<JsonObject>()
        val start = Instant.parse("2026-12-05T01:20:00Z")
        fun snap(stop: String? = null) {
            val state = session.state.value ?: return
            val fix = kotlinx.coroutines.runBlocking { player.fixes.first() }
            frames += frame(state, fix.latitude, fix.longitude, fix.heading, start, stop)
        }

        if (plan.guide != null) {
            val guided = backgroundScope.launch { session.guideTo(plan.guide) }
            advanceTimeBy(1_000); runCurrent()
            check(guided.isCompleted) { "출발 전 안내가 끝나지 않음: ${plan.guide}" }
        }
        repeat(READY_FRAMES) { snap() }

        var job: Job = launch { navi.drive() }
        while (true) {
            advanceTimeBy(FRAME_REAL_MILLIS); runCurrent()
            if (!job.isCompleted) {
                snap()
                continue
            }
            if (player.atVisitedStop) {
                // 경유지에 섰다. 웹은 여기서 "계속 안내"를 기다린다.
                snap(stop = "waypoint")
                job = launch { navi.drive() }
            } else {
                snap(stop = "end")
                break
            }
            check(frames.size < MAX_FRAMES) { "프레임이 너무 많음" }
        }
        return Drive(JsonArray(frames), asked)
    }

    private fun frame(s: DocentState, lat: Double, lon: Double, heading: Double?, start: Instant, stop: String?): JsonObject =
        buildJsonObject {
            put("t", JavaDuration.between(start, s.now).seconds)
            put("lat", round(lat, 6))
            put("lon", round(lon, 6))
            put("h", heading?.let { round(it, 1) })
            put("drv", s.driving)
            put("rem", s.remaining.inWholeSeconds)
            put("q", s.quiet?.let { buildJsonArray { add(JsonPrimitive(it.distanceMeters)); add(JsonPrimitive(it.instruction)) } } ?: JsonNull)
            put("n", s.narration?.let { n ->
                buildJsonObject {
                    put("id", Plan.short(n.ahead.poi.destination.id))
                    put("ph", n.phase.name)
                    put("text", n.text)
                    put("story", n.story)
                    put("lead", n.lead)
                    put("side", n.ahead.side.name.first().toString())
                    put("dist", n.ahead.distanceMeters)
                }
            } ?: JsonNull)
            putJsonArray("ah") {
                s.ahead.forEach { a ->
                    add(buildJsonArray {
                        add(JsonPrimitive(Plan.short(a.poi.destination.id)))
                        add(JsonPrimitive(a.distanceMeters))
                        add(JsonPrimitive(a.side.name.first().toString()))
                        add(JsonPrimitive(a.settled))
                    })
                }
            }
            putJsonArray("told") { s.told.forEach { add(JsonPrimitive(Plan.short(it.destination.id))) } }
            put("wp", s.waypoint?.let { JsonPrimitive(Plan.short(it.id)) } ?: JsonNull)
            putJsonArray("st") { s.stamps.forEach { add(JsonPrimitive(Plan.short(it.id))) } }
            if (stop != null) {
                put("stop", stop)
                // 서 있는 곳에서 걸어서 갈 곳. 웹의 "충전 시작"이 쓴다.
                val fix = io.github.hyunolike.ontime.platform.Fix(lat, lon, heading, 0.0)
                putJsonArray("walk") {
                    nearbyOnFoot(fix, pois).forEach { w ->
                        add(buildJsonArray { add(JsonPrimitive(Plan.short(w.poi.destination.id))); add(JsonPrimitive(w.walkMinutes)) })
                    }
                }
            }
        }

    private fun round(v: Double, digits: Int): Double = "%.${digits}f".format(java.util.Locale.ROOT, v).toDouble()
}

/** 경로 요청과 경유지를 곧바로 받아 주는 내비. 시연 목적지로 안내 중이라고 알려 둔다. */
private class AcceptingNavi(destination: Destination, clock: () -> Instant) : NaviGateway {
    override val route: Flow<RouteStatus> = MutableStateFlow(RouteStatus(destination, Reading(Duration.ZERO, clock(), Source.LIVE)))
    override suspend fun estimateRemainingDrive(to: Destination): Duration? = null
    override suspend fun changeDestination(to: Destination): Boolean = true
    override suspend fun addWaypoint(to: Destination): Boolean = true
    override suspend fun removeWaypoint(): Boolean = true
}

/** 해설은 3.5초, 질문에는 2초 뒤에 계획대로 답한다. 답한 곳을 [asked]에 남긴다. */
private class PlanSpeech(private val yes: Set<String>, private val pois: List<Poi>, private val asked: MutableList<String>) : SpeechGateway {
    private var lastPlace: String? = null

    override suspend fun say(text: String) {
        pois.firstOrNull { text.contains(it.destination.name + ".") }?.let { lastPlace = it.destination.id }
        delay(3.5.seconds)
    }

    override suspend fun askYesNo(prompt: String): Boolean = listenYesNo(prompt) == true

    override suspend fun listenYesNo(prompt: String): Boolean? {
        val place = lastPlace ?: return false
        asked += place
        delay(ANSWER_FRAMES * FRAME_REAL_MILLIS)
        return place in yes
    }
}

/** 프레임 하나는 앱 재생 한 걸음(재생 시각 2초, 실제 0.2초)이다. */
const val FRAME_SIM_SECONDS = 2
const val FRAME_REAL_MILLIS = 200L
/** 질문 뒤 이만큼의 프레임이 지나 답한 것으로 기록한다(2초). */
const val ANSWER_FRAMES = 10
private const val READY_FRAMES = 5
private const val MAX_FRAMES = 5_000
