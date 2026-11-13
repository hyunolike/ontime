package io.github.hyunolike.ontime.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 간격은 4의 배수만 쓴다. */
object Space {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

object Radius {
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val pill: Dp = 999.dp
}

object Size {
    /** 누를 수 있는 요소의 최소 크기. Pleos 가이드에 수치가 없어 Android Automotive 기준(76dp)을 따른다. */
    val touchTarget: Dp = 76.dp
    val icon: Dp = 32.dp
    val iconLarge: Dp = 48.dp
    val screenPadding: Dp = 48.dp
}

/** Pleos 가이드의 그림자 3단계. 어두운 화면에서는 그림자 대신 면의 밝기 차로 높이를 나타낸다. */
object Elevation {
    val level1: Dp = 6.dp
    val level2: Dp = 8.dp
    val level3: Dp = 20.dp
}

/**
 * 움직임은 상태가 바뀌었음을 알릴 때만 쓴다. 반복되는 움직임은 쓰지 않는다.
 * 누름 반응은 0.25초 안에 끝나야 한다 (Pleos 가이드).
 */
object Motion {
    const val fast: Int = 120
    const val standard: Int = 240
    const val emphasized: Int = 420
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
