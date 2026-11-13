package io.github.hyunolike.ontime.voice

/**
 * 제안에 대한 답을 수락, 거절, 모름으로 나눈다.
 * 경로를 바꾸는 일이라 확실할 때만 수락으로 보고, 섞여 있으면 거절로 본다.
 */
object YesNoParser {
    private val negatives = listOf("아니", "괜찮", "됐어", "됐습니다", "그대로", "하지마", "바꾸지마", "싫", "취소", "안해", "안돼")

    /** 문장 어디에 있어도 수락으로 보는 표현. */
    private val positivePhrases = listOf("그렇게해", "그렇게하", "바꿔", "변경해", "좋아", "좋습니다", "부탁", "오케이", "그래", "알겠")

    /** 짧아서 다른 낱말에 섞일 수 있는 표현. 낱말 하나로 떨어져 있을 때만 수락으로 본다. */
    private val positiveWords = setOf("응", "어", "네", "예", "넵", "웅")

    /** true는 수락, false는 거절, null은 알 수 없음. */
    fun parse(utterance: String): Boolean? {
        val words = utterance.split(Regex("[\\s.,!?~]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val joined = words.joinToString("")
        return when {
            negatives.any { it in joined } -> false
            positivePhrases.any { it in joined } -> true
            words.any { it in positiveWords } -> true
            else -> null
        }
    }
}
