package io.github.hyunolike.ontime.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Space

/**
 * 장소 사진. 주행 중에는 한 장면에 한 장만, 움직이지 않는 사진으로 쓴다. 넘겨 보는 사진첩은 정차 중에만 둔다.
 * [scrim]을 켜면 아래쪽에 어두운 막을 깔아 [overlay]의 글자가 읽히게 한다. 사진이 없으면 면만 그린다.
 */
@Composable
fun Photo(
    painter: Painter?,
    description: String?,
    modifier: Modifier = Modifier,
    radius: Dp = Radius.xl,
    scrim: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = OnTimeTheme.colors
    Box(modifier.clip(RoundedCornerShape(radius)).background(colors.surface)) {
        if (painter != null) {
            Image(painter, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (scrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to colors.scrim.copy(alpha = 0.88f))),
            )
        }
        overlay()
    }
}

/**
 * 창밖 어느 쪽인지 알리는 표시. 사진의 바깥쪽 가장자리에 세로 띠와 화살표를 붙인다.
 * 화면에서의 좌우가 실제 창의 좌우와 같아야 하므로, 왼쪽이면 왼쪽 가장자리에만 쓴다.
 */
@Composable
fun BoxScope.SideBeacon(left: Boolean, description: String) {
    val colors = OnTimeTheme.colors
    val edge = if (left) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        Modifier
            .align(edge)
            .fillMaxHeight()
            .width(12.dp)
            .background(colors.accent),
    )
    Box(
        Modifier
            .align(edge)
            .padding(horizontal = Space.xl)
            .size(112.dp)
            .clip(CircleShape)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (left) OnTimeIcon.SideLeft else OnTimeIcon.SideRight, tint = colors.onAccent, size = 72.dp, description = description)
    }
}

/** 사진 아래쪽에 얹는 글자 자리. [Photo]의 scrim과 함께 쓴다. */
@Composable
fun BoxScope.PhotoCaption(content: @Composable () -> Unit) {
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.lg)) { content() }
}
