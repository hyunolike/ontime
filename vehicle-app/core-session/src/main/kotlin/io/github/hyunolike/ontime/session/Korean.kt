package io.github.hyunolike.ontime.session

/** 받침에 따라 "으로" 또는 "로"를 붙인다. 한글이 아니면 "으로"를 쓴다. */
fun String.withEuro(): String {
    val last = lastOrNull() ?: return this
    if (last !in '가'..'힣') return this + "으로"
    val jong = (last.code - 0xAC00) % 28
    return this + if (jong == 0 || jong == 8) "로" else "으로"
}

/** 여유 분을 부호와 함께 읽는다. */
internal fun signedMinutes(minutes: Int): String = if (minutes >= 0) "+${minutes}분" else "${minutes}분"

/** 받침에 따라 "을" 또는 "를"을 붙인다. 한글이 아니면 "를"을 쓴다. */
fun String.withEulReul(): String {
    val last = lastOrNull() ?: return this
    if (last !in '가'..'힣') return this + "를"
    return this + if ((last.code - 0xAC00) % 28 == 0) "를" else "을"
}
