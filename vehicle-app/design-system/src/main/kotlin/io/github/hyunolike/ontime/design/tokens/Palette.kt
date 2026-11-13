package io.github.hyunolike.ontime.design.tokens

/**
 * 원시 색상값(ARGB). 화면에서는 직접 쓰지 않고 [io.github.hyunolike.ontime.design.theme.OnTimeColorScheme]의
 * 의미 이름으로만 쓴다. Compose에 의존하지 않아 대비 검사를 단위 테스트로 돌릴 수 있다.
 */
object Palette {
    // 중립색
    const val Ink950 = 0xFF0B0F14
    const val Ink900 = 0xFF121820
    const val Ink850 = 0xFF18202A
    const val Ink800 = 0xFF212B37
    const val Ink700 = 0xFF303D4C
    const val Ink400 = 0xFF8C99A8
    const val Ink300 = 0xFFA9B4C0
    const val Ink100 = 0xFFE6EBF0
    const val Ink50 = 0xFFF4F6F8
    const val White = 0xFFFFFFFF
    const val Slate900 = 0xFF10161D
    const val Slate600 = 0xFF465261
    const val Slate300 = 0xFFC3CBD4
    const val Slate200 = 0xFFDCE2E8

    // 상태색: 어두운 배경용(밝은 톤)과 밝은 배경용(짙은 톤)
    const val Green300 = 0xFF3DDC97
    const val Green700 = 0xFF0B7A4B
    const val Amber300 = 0xFFFFB020
    const val Amber800 = 0xFF8F5300
    const val Red300 = 0xFFFF7A7A
    const val Red700 = 0xFFC62828
    const val Blue300 = 0xFF7CB7FF
    const val Blue700 = 0xFF1759B8
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
