package io.github.hyunolike.ontime.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.hyunolike.ontime.design.tokens.Palette

/** 상태를 나타내는 색의 역할. 색만으로 구분하지 않도록 아이콘, 글과 함께 쓴다. */
enum class Tone { Positive, Caution, Critical, Info, Neutral }

/**
 * 의미 이름으로 정리한 색. 글자와 아이콘은 배경 대비 4.5:1 이상을 지킨다
 * (Pleos Connect UI Design Guide). 값은 [raw]에 ARGB로도 두어 테스트로 검사한다.
 */
@Immutable
data class OnTimeColorScheme(val raw: RawColorScheme) {
    val background = Color(raw.background)
    val surface = Color(raw.surface)
    val surfaceRaised = Color(raw.surfaceRaised)
    val outline = Color(raw.outline)
    val track = Color(raw.track)
    val textPrimary = Color(raw.textPrimary)
    val textSecondary = Color(raw.textSecondary)
    val onAccent = Color(raw.onAccent)
    val positive = Color(raw.positive)
    val caution = Color(raw.caution)
    val critical = Color(raw.critical)
    val info = Color(raw.info)

    fun tone(tone: Tone): Color = when (tone) {
        Tone.Positive -> positive
        Tone.Caution -> caution
        Tone.Critical -> critical
        Tone.Info -> info
        Tone.Neutral -> textSecondary
    }
}

data class RawColorScheme(
    val background: Long,
    val surface: Long,
    val surfaceRaised: Long,
    val outline: Long,
    /** 막대나 게이지의 빈 자리. */
    val track: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    /** 상태색 위에 올리는 글자. */
    val onAccent: Long,
    val positive: Long,
    val caution: Long,
    val critical: Long,
    val info: Long,
)

/** 야간. 차량 화면의 기본값이다. */
val DarkRawColors = RawColorScheme(
    background = Palette.Ink950,
    surface = Palette.Ink850,
    surfaceRaised = Palette.Ink800,
    outline = Palette.Ink700,
    track = Palette.Ink800,
    textPrimary = Palette.Ink50,
    textSecondary = Palette.Ink300,
    onAccent = Palette.Ink950,
    positive = Palette.Green300,
    caution = Palette.Amber300,
    critical = Palette.Red300,
    info = Palette.Blue300,
)

/** 주간. */
val LightRawColors = RawColorScheme(
    background = Palette.Ink50,
    surface = Palette.White,
    surfaceRaised = Palette.White,
    outline = Palette.Slate300,
    track = Palette.Slate200,
    textPrimary = Palette.Slate900,
    textSecondary = Palette.Slate600,
    onAccent = Palette.White,
    positive = Palette.Green700,
    caution = Palette.Amber800,
    critical = Palette.Red700,
    info = Palette.Blue700,
)
