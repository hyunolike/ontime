package io.github.hyunolike.ontime.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Tone

@Immutable
data class BudgetSegment(
    val label: String,
    val minutes: Float,
    val icon: OnTimeIcon,
    /** 여유 구간이면 상태색으로 칠한다. 나머지는 중립색의 농도로만 구분한다. */
    val tone: Tone? = null,
)

/**
 * 마감까지 남은 시간을 주행, 주차, 도보, 버퍼, 여유로 나눠 보여 주는 막대.
 *
 * 쓰는 시간은 중립색으로 가라앉히고 남는 여유만 상태색으로 칠해, 숫자를 읽지 않아도
 * 얼마나 남는지 비율로 알 수 있게 한다. [onSelect]가 있으면 구간을 눌러 고를 수 있다(정차 중에만).
 */
@Composable
fun TimeBudgetBar(
    segments: List<BudgetSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val colors = OnTimeTheme.colors
    val total = segments.sumOf { it.minutes.coerceAtLeast(0f).toDouble() }.toFloat().coerceAtLeast(1f)
    val gap = 4.dp
    val description = segments.joinToString(", ") { "${it.label} ${it.minutes.toInt()}분" }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.track)
            .semantics { contentDescription = description },
    ) {
        val usable = maxWidth - gap * (segments.size - 1).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            var neutralIndex = 0
            segments.forEachIndexed { index, segment ->
                val fraction by animateFloatAsState(
                    segment.minutes.coerceAtLeast(0f) / total,
                    tween(Motion.emphasized, easing = Motion.standardEasing),
                    label = "fraction",
                )
                val base = if (segment.tone != null) {
                    colors.tone(segment.tone)
                } else {
                    // 중립 구간은 뒤로 갈수록 옅어진다.
                    colors.textSecondary.copy(alpha = (0.75f - 0.16f * neutralIndex++).coerceAtLeast(0.2f))
                }
                val dimmed = selected != null && selected != index
                val color by animateColorAsState(
                    if (dimmed) base.copy(alpha = base.alpha * 0.35f) else base,
                    tween(Motion.standard),
                    label = "segment",
                )
                Box(
                    Modifier
                        .width(usable * fraction)
                        .fillMaxHeight()
                        .background(color)
                        .then(if (onSelect != null) Modifier.clickable { onSelect(index) } else Modifier),
                )
            }
        }
    }
}

/** 막대 구간의 색을 범례나 목록에서 다시 쓸 때. */
@Composable
fun budgetSegmentColor(segments: List<BudgetSegment>, index: Int): Color {
    val colors = OnTimeTheme.colors
    val segment = segments[index]
    if (segment.tone != null) return colors.tone(segment.tone)
    val neutralIndex = segments.take(index).count { it.tone == null }
    return colors.textSecondary.copy(alpha = (0.75f - 0.16f * neutralIndex).coerceAtLeast(0.2f))
}
