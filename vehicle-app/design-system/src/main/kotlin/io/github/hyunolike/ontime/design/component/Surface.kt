package io.github.hyunolike.ontime.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone

/**
 * 내용을 묶는 면. [onClick]이 있으면 누를 수 있고, [selected]면 테두리로 강조한다.
 * 어두운 화면에서는 그림자 대신 면의 밝기로 높이를 구분한다.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = OnTimeTheme.colors.positive,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = Space.xl, vertical = Space.lg),
    content: @Composable () -> Unit,
) {
    val colors = OnTimeTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    val border by animateColorAsState(if (selected) accent else colors.outline, tween(Motion.standard), label = "border")
    Box(
        modifier
            .clip(shape)
            .background(if (selected) colors.surfaceRaised else colors.surface)
            .border(if (selected) 3.dp else 1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

/** 상태를 아이콘과 글자로 함께 알린다. 색만으로 구분하지 않는다. */
@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier, icon: OnTimeIcon? = tone.defaultIcon()) {
    val color = OnTimeTheme.colors.tone(tone)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, tint = color) }
        Text(text, style = OnTimeTheme.type.title, color = color, maxLines = 1)
    }
}

fun Tone.defaultIcon(): OnTimeIcon? = when (this) {
    Tone.Positive -> OnTimeIcon.Check
    Tone.Caution -> OnTimeIcon.Warning
    Tone.Critical -> OnTimeIcon.Error
    Tone.Info -> OnTimeIcon.Info
    Tone.Neutral -> null
}

/** 보조 정보를 담는 작은 표식. 예: "시연 재생", "안내 중". */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: OnTimeIcon? = null) {
    val colors = OnTimeTheme.colors
    val color = if (tone == Tone.Neutral) colors.textSecondary else colors.tone(tone)
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        modifier
            .clip(shape)
            .border(1.5.dp, color, shape)
            .padding(horizontal = Space.lg, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, tint = color, size = 24.dp) }
        Text(text, style = OnTimeTheme.type.caption, color = color, maxLines = 1)
    }
}
