package io.github.hyunolike.ontime.design.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LocalColors = staticCompositionLocalOf { OnTimeColorScheme(DarkRawColors) }
private val LocalTypography = staticCompositionLocalOf { OnTimeTypography() }

/** 주행 중이면 true. 구성 요소가 움직임을 줄이고 누를 수 있는 요소를 감춘다. */
val LocalDriving = staticCompositionLocalOf { false }

/** 화면 방향. 가로와 세로 배치를 고르는 데 쓴다. */
val LocalWideLayout = staticCompositionLocalOf { true }

object OnTimeTheme {
    val colors: OnTimeColorScheme
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: OnTimeTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}

/**
 * 온타임 화면의 바탕. 주간과 야간 색을 고르고, 앱 영역 크기에 맞춰 전체를 같은 비율로 키우거나 줄인다.
 *
 * 차량 화면은 12.9인치 세로부터 17인치 가로까지 크기가 다양하고 앱 두 개가 나란히 뜨기도 한다.
 * 기준 크기(가로 1280x720dp, 세로 720dp 폭)에 맞춰 설계하고 실제 영역과의 비율만큼 밀도를 조정한다.
 */
@Composable
fun OnTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    driving: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = OnTimeColorScheme(if (darkTheme) DarkRawColors else LightRawColors)
    val typography = OnTimeTypography()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val wide = maxWidth >= maxHeight
        val scale = if (wide) {
            minOf(maxWidth.value / REFERENCE_WIDTH, maxHeight.value / REFERENCE_HEIGHT)
        } else {
            maxWidth.value / REFERENCE_PORTRAIT_WIDTH
        }.coerceIn(MIN_SCALE, MAX_SCALE)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalTypography provides typography,
            LocalDriving provides driving,
            LocalWideLayout provides wide,
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalContentColor provides colors.textPrimary,
            LocalTextStyle provides typography.body,
            LocalTextSelectionColors provides LocalTextSelectionColors.current,
            content = content,
        )
    }
}

private const val REFERENCE_WIDTH = 1280f
private const val REFERENCE_HEIGHT = 720f
private const val REFERENCE_PORTRAIT_WIDTH = 720f
private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 2.4f
