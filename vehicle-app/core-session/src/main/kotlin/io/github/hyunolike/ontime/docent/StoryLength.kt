package io.github.hyunolike.ontime.docent

import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead

/** 해설을 읽는 데 걸리는 시간(초). 한국어 기준 초당 6자로 잡는다. */
fun readSeconds(text: String): Double = text.length / CHARS_PER_SECOND

/** 시간이 촉박할 때의 해설. 따로 적어 둔 것이 없으면 기본 해설의 첫 문장이다. */
fun Poi.short(): String = shortStory.ifEmpty { firstSentence(story) }

/**
 * 지금 들려줄 해설을 고른다. 다음 가운데 하나라도 맞으면 짧은 해설이다.
 * - 그곳까지 1분이 남지 않았다.
 * - 다음 회전까지가 기본 해설을 읽는 시간보다 짧다.
 * - 기본 해설을 읽는 동안 소개를 기다리는 다른 곳이 소개 범위([announceWithin]) 안으로 들어온다.
 */
fun pickStory(
    ahead: PoiAhead,
    speedMetersPerSecond: Double,
    nextManeuver: Maneuver?,
    others: List<PoiAhead>,
    announceWithin: Int = 3_000,
): String {
    val poi = ahead.poi
    val speed = speedMetersPerSecond.coerceAtLeast(1.0)
    val reading = readSeconds(poi.story)
    val hurried = ahead.distanceMeters / speed < 60 ||
        (nextManeuver != null && nextManeuver.distanceMeters / speed < reading) ||
        others.any { it.settled && it.poi != poi && (it.distanceMeters - announceWithin) / speed < reading }
    return if (hurried) poi.short() else poi.story
}

/** 문장부호 뒤에 공백이 오는 곳에서 끊는다. "1.2km" 같은 소수점에서는 끊지 않는다. */
private fun firstSentence(text: String): String = text.split(SENTENCE_END, limit = 2).first()

private val SENTENCE_END = Regex("(?<=[.?!])\\s+")

private const val CHARS_PER_SECOND = 6.0
