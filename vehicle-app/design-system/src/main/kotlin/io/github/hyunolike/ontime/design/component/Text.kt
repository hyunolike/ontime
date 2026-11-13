package io.github.hyunolike.ontime.design.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.hyunolike.ontime.design.theme.OnTimeTheme

/** 글자. 색과 크기는 토큰에서만 고른다. 주행 중 문장은 세 줄을 넘기지 않는다. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = OnTimeTheme.type.body,
    color: Color = OnTimeTheme.colors.textPrimary,
    maxLines: Int = 3,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
