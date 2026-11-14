package io.github.hyunolike.ontime.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.theme.LocalDriving
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space

/** Primary는 이야기를 시작하는 주된 동작, Route는 경로를 바꾸는 동작, Secondary는 그 밖의 선택지. */
enum class ButtonVariant { Primary, Route, Secondary }

/**
 * 기본 버튼. 구조와 상태는 Pleos의 Basic button을 따른다: 앞 아이콘, 글자, 그리고 눌림, 비활성, 로딩.
 * 높이는 최소 터치 크기 이상이고, 주행 중([LocalDriving])에는 더 크게 그린다.
 * 눌렀을 때의 반응은 0.12초 안에 끝난다.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: OnTimeIcon? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = OnTimeTheme.colors
    val driving = LocalDriving.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.fast), label = "press")
    val container = when (variant) {
        ButtonVariant.Primary -> colors.accent
        ButtonVariant.Route -> colors.route
        ButtonVariant.Secondary -> colors.surfaceRaised
    }
    val content = if (variant == ButtonVariant.Secondary) colors.textPrimary else colors.onAccent
    val height = if (driving) Size.touchTargetDriving else Size.touchTarget
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minWidth = height, minHeight = height)
            .clip(shape)
            .background(container)
            .then(if (variant == ButtonVariant.Secondary) Modifier.border(2.dp, colors.outline, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.xl, vertical = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(Size.icon), color = content, strokeWidth = 3.dp)
            icon != null -> Icon(icon, tint = content, size = if (driving) Size.iconLarge else Size.icon)
        }
        val style = if (driving) OnTimeTheme.type.title else OnTimeTheme.type.bodyLarge.copy(fontWeight = OnTimeTheme.type.title.fontWeight)
        Text(text, style = style, color = content, maxLines = 1)
    }
}
