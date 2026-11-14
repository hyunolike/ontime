package io.github.hyunolike.ontime.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.hyunolike.ontime.design.tokens.Palette

/**
 * 색의 역할. 색만으로 구분하지 않도록 아이콘, 글과 함께 쓴다.
 * Accent는 이야기, Info는 길(경로를 바꾸는 동작), Positive는 다녀온 곳이다.
 */
enum class Tone { Accent, Positive, Caution, Critical, Info, Neutral }

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
    val accent = Color(raw.accent)
    val route = Color(raw.route)
    val onAccent = Color(raw.onAccent)
    val positive = Color(raw.positive)
    val caution = Color(raw.caution)
    val critical = Color(raw.critical)
    val info = route

    /** 사진 위에 글자를 올릴 때 까는 막. 주간과 야간 모두 어둡게 깔고 글자는 [onPhoto]로 쓴다. */
    val scrim = Color(raw.scrim)
    val onPhoto = Color(raw.onPhoto)

    fun tone(tone: Tone): Color = when (tone) {
        Tone.Accent -> accent
        Tone.Positive -> positive
        Tone.Caution -> caution
        Tone.Critical -> critical
        Tone.Info -> route
        Tone.Neutral -> textSecondary
    }
}

data class RawColorScheme(
    val background: Long,
    val surface: Long,
    val surfaceRaised: Long,
    val outline: Long,
    /** 막대나 여정 선의 빈 자리. */
    val track: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    /** 이야기. 길벗이 말하고 있는 것, 주된 버튼. */
    val accent: Long,
    /** 길. 경유지 추가처럼 경로를 바꾸는 동작. */
    val route: Long,
    /** 색을 채운 면 위에 올리는 글자. */
    val onAccent: Long,
    val positive: Long,
    val caution: Long,
    val critical: Long,
    val scrim: Long = Palette.Night950,
    val onPhoto: Long = Palette.Paper50,
)

/** 야간. 차량 화면의 기본값이다. */
val DarkRawColors = RawColorScheme(
    background = Palette.Night950,
    surface = Palette.Night850,
    surfaceRaised = Palette.Night800,
    outline = Palette.Night700,
    track = Palette.Night700,
    textPrimary = Palette.Paper50,
    textSecondary = Palette.Paper300,
    accent = Palette.Gold300,
    route = Palette.Sky300,
    onAccent = Palette.Night950,
    positive = Palette.Jade300,
    caution = Palette.Orange300,
    critical = Palette.Red300,
)

/** 주간. */
val LightRawColors = RawColorScheme(
    background = Palette.Paper50,
    surface = Palette.White,
    surfaceRaised = Palette.White,
    outline = Palette.Stone300,
    track = Palette.Stone200,
    textPrimary = Palette.Stone900,
    textSecondary = Palette.Stone600,
    accent = Palette.Gold800,
    route = Palette.Sky700,
    onAccent = Palette.White,
    positive = Palette.Jade700,
    caution = Palette.Orange800,
    critical = Palette.Red700,
)
