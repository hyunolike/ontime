package io.github.hyunolike.ontime.design

import io.github.hyunolike.ontime.design.theme.DarkRawColors
import io.github.hyunolike.ontime.design.theme.LightRawColors
import io.github.hyunolike.ontime.design.theme.RawColorScheme
import io.github.hyunolike.ontime.design.tokens.Contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pleos Connect UI Design Guide: 아이콘과 글자의 대비율은 4.5:1 이상이어야 한다. */
class ContrastTest {
    private fun check(name: String, scheme: RawColorScheme) {
        val backgrounds = mapOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceRaised" to scheme.surfaceRaised,
        )
        val foregrounds = mapOf(
            "textPrimary" to scheme.textPrimary,
            "textSecondary" to scheme.textSecondary,
            "accent" to scheme.accent,
            "route" to scheme.route,
            "positive" to scheme.positive,
            "caution" to scheme.caution,
            "critical" to scheme.critical,
        )
        for ((bgName, bg) in backgrounds) for ((fgName, fg) in foregrounds) {
            val ratio = Contrast.ratio(fg, bg)
            assertTrue(ratio >= 4.5, "$name: $fgName on $bgName = ${"%.2f".format(ratio)}")
        }
        // 상태색을 채운 버튼이나 배지 위의 글자
        for ((accentName, accent) in listOf("accent" to scheme.accent, "route" to scheme.route, "positive" to scheme.positive, "caution" to scheme.caution, "critical" to scheme.critical)) {
            val ratio = Contrast.ratio(scheme.onAccent, accent)
            assertTrue(ratio >= 4.5, "$name: onAccent on $accentName = ${"%.2f".format(ratio)}")
        }
    }

    @Test
    fun `야간 색은 대비 기준을 지킨다`() = check("dark", DarkRawColors)

    @Test
    fun `주간 색은 대비 기준을 지킨다`() = check("light", LightRawColors)

    @Test
    fun `사진 위 글자는 막 위에서 대비 기준을 지킨다`() {
        for (scheme in listOf(DarkRawColors, LightRawColors)) {
            assertTrue(Contrast.ratio(scheme.onPhoto, scheme.scrim) >= 4.5)
        }
    }

    @Test
    fun `검정과 흰색의 대비율은 21이다`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01)
    }
}
