package io.github.hyunolike.ontime.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.SlackNumber
import io.github.hyunolike.ontime.design.component.StatusBadge
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.component.TimeBudgetBar
import io.github.hyunolike.ontime.design.component.slackLabel
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.session.SessionState
import io.github.hyunolike.ontime.session.Suggestion
import io.github.hyunolike.ontime.session.SuggestionStatus
import io.github.hyunolike.ontime.session.TripPlan
import kotlinx.coroutines.delay

/**
 * 주행 중 화면. 1~2초 안에 읽히도록 숫자 하나와 상태만 크게 두고, 누를 수 있는 요소는 없다.
 * 움직임은 값이나 상태가 바뀔 때 한 번뿐이고 반복되는 움직임은 쓰지 않는다.
 */
@Composable
fun DrivingScreen(state: SessionState, plan: TripPlan, departureLabel: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl)) {
        TripHeader(plan, departureLabel, state.source, trailing = "D 주행 중")

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("여유", style = OnTimeTheme.type.title, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                StatusBadge(state.level.label(), state.level.tone())
                if (state.stale) {
                    Text("${state.now.clock()} 기준", style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                }
            }
            SlackNumber(state.slack.displayMinutes, state.level.tone())
            TimeBudgetBar(state.budgetSegments(), Modifier.padding(top = Space.sm), height = 20.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(OnTimeIcon.Navigation, tint = OnTimeTheme.colors.textSecondary)
                Text(state.destinationName, style = OnTimeTheme.type.bodyLarge, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text("마감까지 ${minutesText(state.untilDeadline)}", style = OnTimeTheme.type.body, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
            }
        }

        SuggestionBanner(state.suggestion)
    }
}

/**
 * 제안과 그 결과를 한 줄로 남긴다. 주고받는 것은 음성이고 화면은 확인용이다.
 * 결과는 잠시 보여 준 뒤 스스로 사라져 숫자만 남긴다.
 */
@Composable
internal fun SuggestionBanner(suggestion: Suggestion?) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(suggestion) {
        visible = suggestion != null
        if (suggestion != null && suggestion.status != SuggestionStatus.PROPOSED) {
            delay(RESULT_VISIBLE_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible && suggestion != null, enter = fadeIn(tween(Motion.standard)), exit = fadeOut(tween(Motion.standard))) {
        val shown = suggestion ?: return@AnimatedVisibility
        val (icon, tone, text) = when (shown.status) {
            SuggestionStatus.PROPOSED -> Triple(OnTimeIcon.Mic, Tone.Info, shown.text)
            SuggestionStatus.ACCEPTED -> Triple(OnTimeIcon.Check, Tone.Positive, shown.result ?: shown.text)
            SuggestionStatus.DECLINED -> Triple(OnTimeIcon.Navigation, Tone.Neutral, "경로를 그대로 유지합니다")
            SuggestionStatus.FAILED -> Triple(OnTimeIcon.Error, Tone.Critical, shown.result ?: "경로를 바꾸지 못했습니다")
        }
        val before = shown.currentLabel ?: shown.currentMinutes?.let(::slackLabel)
        val after = shown.expectedLabel ?: shown.expectedMinutes?.let(::slackLabel)
        val showChange = shown.status == SuggestionStatus.PROPOSED && before != null && after != null
        // 내비와 나란히 떠 폭이 좁으면 여유 변화를 문장 아래로 내린다.
        val wide = LocalWideLayout.current
        Card(Modifier.fillMaxWidth(), selected = shown.status == SuggestionStatus.PROPOSED, accent = OnTimeTheme.colors.tone(tone)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, tint = OnTimeTheme.colors.tone(tone), size = Size.iconLarge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(text, style = OnTimeTheme.type.bodyLarge, maxLines = 3)
                    if (shown.status == SuggestionStatus.PROPOSED) {
                        Text("\"그렇게 해 줘\"라고 말하면 바꿉니다", style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                    }
                    if (showChange && !wide) SlackChange(before!!, after!!)
                }
                if (showChange && wide) SlackChange(before!!, after!!)
            }
        }
    }
}

/** 제안을 받아들이면 무엇이 어떻게 바뀌는지. 예: +9분 → +21분, 붐빔 → 여유 */
@Composable
private fun SlackChange(before: String, after: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(before, style = OnTimeTheme.type.title, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
        Icon(OnTimeIcon.ArrowForward, tint = OnTimeTheme.colors.textSecondary)
        Text(after, style = OnTimeTheme.type.headline, color = OnTimeTheme.colors.positive, maxLines = 1)
    }
}

private const val RESULT_VISIBLE_MS = 8_000L
