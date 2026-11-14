package io.github.hyunolike.ontime.design.tokens

/**
 * 원시 색상값(ARGB). 화면에서는 직접 쓰지 않고 [io.github.hyunolike.ontime.design.theme.OnTimeColorScheme]의
 * 의미 이름으로만 쓴다. Compose에 의존하지 않아 대비 검사를 단위 테스트로 돌릴 수 있다.
 */
object Palette {
    // 밤길의 바탕. 푸른 기를 빼고 먹색에 가깝게 잡아 사진의 색이 먼저 보이게 한다.
    const val Night950 = 0xFF0B0C0E
    const val Night900 = 0xFF131519
    const val Night850 = 0xFF1A1D22
    const val Night800 = 0xFF23272E
    const val Night700 = 0xFF343A43
    const val Paper300 = 0xFFB9B4AA
    const val Paper50 = 0xFFF6F3EC
    const val White = 0xFFFFFFFF

    // 낮의 바탕. 한지처럼 살짝 따뜻한 흰색.
    const val Stone900 = 0xFF16181C
    const val Stone600 = 0xFF4C5058
    const val Stone300 = 0xFFC9C6BE
    const val Stone200 = 0xFFE0DDD5

    // 뜻이 있는 색 다섯 가지. 어두운 바탕용(300)과 밝은 바탕용(700, 800)을 짝으로 둔다.
    /** 등불. 길벗이 이야기하고 있다는 표시. */
    const val Gold300 = 0xFFFFC857
    const val Gold800 = 0xFF7A4E00
    /** 길. 경로를 바꾸는 동작. */
    const val Sky300 = 0xFF8AB8FF
    const val Sky700 = 0xFF1759B8
    /** 옥. 다녀온 곳. */
    const val Jade300 = 0xFF5EDDB0
    const val Jade700 = 0xFF0B7A4B
    const val Orange300 = 0xFFFFA05C
    const val Orange800 = 0xFF8F4A00
    const val Red300 = 0xFFFF8A80
    const val Red700 = 0xFFC62828
}

/** WCAG 2.x 상대 휘도와 대비율. */
object Contrast {
    private fun channel(value: Long): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    fun luminance(argb: Long): Double =
        0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun ratio(foreground: Long, background: Long): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
