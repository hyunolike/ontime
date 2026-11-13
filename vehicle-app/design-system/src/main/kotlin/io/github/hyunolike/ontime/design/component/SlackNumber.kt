package io.github.hyunolike.ontime.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.hyunolike.ontime.design.theme.LocalDriving
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Tone

/**
 * 여유 시간 숫자. 온타임 화면의 중심이며 한 화면에 하나만 둔다.
 *
 * 값이 바뀔 때만 한 번 움직인다. 늘면 아래에서 올라오고 줄면 위에서 내려온다.
 * 주행 중에는 시선을 끌지 않도록 밀어 넣지 않고 짧게 겹쳐 바꾼다.
 */
@Composable
fun SlackNumber(
    minutes: Int,
    tone: Tone,
    modifier: Modifier = Modifier,
    style: TextStyle = OnTimeTheme.type.display,
) {
    val color by animateColorAsState(OnTimeTheme.colors.tone(tone), tween(Motion.emphasized), label = "tone")
    val driving = LocalDriving.current
    AnimatedContent(
        targetState = minutes,
        modifier = modifier.semantics { contentDescription = "여유 ${slackLabel(minutes)}" },
        transitionSpec = {
            val up = targetState > initialState
            if (driving) {
                fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.fast))
            } else {
                (slideInVertically(tween(Motion.emphasized, easing = Motion.standardEasing)) { if (up) it / 3 else -it / 3 } +
                    fadeIn(tween(Motion.standard))) togetherWith
                    (slideOutVertically(tween(Motion.standard)) { if (up) -it / 3 else it / 3 } + fadeOut(tween(Motion.fast)))
            } using SizeTransform(clip = false)
        },
        label = "slack",
    ) { value ->
        Text(slackLabel(value), style = style, color = color, maxLines = 1)
    }
}

/** 여유는 부호를 붙여 읽는다. 음수는 이미 마감을 지키기 어렵다는 뜻이다. */
fun slackLabel(minutes: Int): String = if (minutes >= 0) "+${minutes}분" else "−${-minutes}분"
