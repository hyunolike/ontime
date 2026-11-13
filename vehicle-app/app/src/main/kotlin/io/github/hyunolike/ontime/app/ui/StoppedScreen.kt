package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.BudgetSegment
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.SlackNumber
import io.github.hyunolike.ontime.design.component.StatusBadge
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.component.TimeBudgetBar
import io.github.hyunolike.ontime.design.component.budgetSegmentColor
import io.github.hyunolike.ontime.design.component.slackLabel
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.session.ParkingPreview
import io.github.hyunolike.ontime.session.SessionState
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.slack.Source

/**
 * 정차 중 화면. 숫자가 어떻게 나왔는지 풀어 보여 주고, 주차장을 비교해 직접 고를 수 있다.
 * 가로 화면은 두 단, 세로 화면은 한 단으로 쌓는다.
 */
@Composable
fun StoppedScreen(
    state: SessionState,
    plan: TripPlan,
    departureLabel: String,
    arrived: Boolean,
    previews: List<ParkingPreview>,
    choosing: String?,
    onChoose: (String) -> Unit,
    onStartDemo: (() -> Unit)?,
) {
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
        TripHeader(plan, departureLabel, state.source, trailing = "P 정차 중")
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    BreakdownPane(state, plan, arrived)
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ParkingPane(previews, choosing, arrived, onChoose, onStartDemo, state.source)
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                BreakdownPane(state, plan, arrived)
                ParkingPane(previews, choosing, arrived, onChoose, onStartDemo, state.source)
            }
        }
    }
}

/** 여유 숫자와 시간 예산. 막대의 구간이나 목록의 줄을 누르면 서로 같은 항목이 강조된다. */
@Composable
private fun BreakdownPane(state: SessionState, plan: TripPlan, arrived: Boolean) {
    val segments = state.budgetSegments()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Column {
            Text(plan.title, style = OnTimeTheme.type.title, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                SlackNumber(state.slack.displayMinutes, state.level.tone(), style = OnTimeTheme.type.displaySmall)
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    StatusBadge(state.level.label(), state.level.tone())
                    Text(
                        if (arrived) "${state.destinationName} 도착" else "${state.now.clock()} 기준 여유",
                        style = OnTimeTheme.type.label,
                        color = OnTimeTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        TimeBudgetBar(segments, selected = selected, onSelect = { selected = if (selected == it) null else it }, height = 36.dp)
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(Space.sm)) {
            Column {
                BreakdownRow(null, "마감까지 남은 시간", minutesText(state.untilDeadline), dimmed = selected != null, onClick = { selected = null })
                segments.forEachIndexed { index, segment ->
                    val last = index == segments.lastIndex
                    BreakdownRow(
                        marker = index to segments,
                        label = if (index == 0) "${segment.label} · ${state.destinationName}" else segment.label,
                        value = if (last) slackLabel(state.slack.displayMinutes) else "− ${segment.minutes.toInt()}분",
                        dimmed = selected != null && selected != index,
                        emphasized = last,
                        tone = if (last) state.level.tone() else null,
                        onClick = { selected = if (selected == index) null else index },
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    marker: Pair<Int, List<BudgetSegment>>?,
    label: String,
    value: String,
    dimmed: Boolean,
    emphasized: Boolean = false,
    tone: Tone? = null,
    onClick: () -> Unit,
) {
    val colors = OnTimeTheme.colors
    val alpha = if (dimmed) 0.45f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marker != null) {
            val (index, segments) = marker
            Box(Modifier.size(16.dp).clip(CircleShape).background(budgetSegmentColor(segments, index).copy(alpha = alpha)))
            Icon(segments[index].icon, tint = colors.textSecondary.copy(alpha = alpha))
        } else {
            Spacer(Modifier.size(16.dp))
            Icon(OnTimeIcon.Clock, tint = colors.textSecondary.copy(alpha = alpha))
        }
        Text(label, Modifier.weight(1f), style = OnTimeTheme.type.body, color = colors.textPrimary.copy(alpha = alpha), maxLines = 1)
        Text(
            value,
            style = if (emphasized) OnTimeTheme.type.title else OnTimeTheme.type.bodyLarge,
            color = (tone?.let { colors.tone(it) } ?: colors.textSecondary).copy(alpha = alpha),
            maxLines = 1,
        )
    }
}

/** 주차장 비교. 고르면 그 주차장으로 목적지를 바꾸고, 바뀐 여유가 왼쪽 숫자에 반영된다. */
@Composable
private fun ParkingPane(
    previews: List<ParkingPreview>,
    choosing: String?,
    arrived: Boolean,
    onChoose: (String) -> Unit,
    onStartDemo: (() -> Unit)?,
    source: Source,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text("주차장 비교", style = OnTimeTheme.type.title, maxLines = 1)
        if (previews.isEmpty()) {
            Text("비교할 주차장 정보를 불러오는 중입니다", color = OnTimeTheme.colors.textSecondary)
        }
        val best = previews.maxByOrNull { it.slack.exact }
        previews.forEach { preview ->
            val id = preview.destination.id
            Card(
                Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
                selected = preview.current,
                accent = OnTimeTheme.colors.tone(preview.level.tone()),
                onClick = if (!preview.current && !arrived && choosing == null) ({ onChoose(id) }) else null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(preview.destination.name, style = OnTimeTheme.type.bodyLarge, maxLines = 1)
                            when {
                                choosing == id -> Chip("변경 중", tone = Tone.Info)
                                preview.current -> Chip("안내 중", tone = Tone.Positive, icon = OnTimeIcon.Navigation)
                                preview == best -> Chip("가장 여유", tone = Tone.Info)
                            }
                        }
                        Text(
                            "주행 ${minutesText(preview.drive)} · 도보 ${minutesText(preview.walk)}",
                            style = OnTimeTheme.type.label,
                            color = OnTimeTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        slackLabel(preview.slack.displayMinutes),
                        style = OnTimeTheme.type.headline,
                        color = OnTimeTheme.colors.tone(preview.level.tone()),
                        maxLines = 1,
                    )
                }
            }
        }
        if (source == Source.REPLAY) {
            Text(
                "주행 시간과 시각은 시연용으로 재생한 값이고, 주차와 도보 시간은 설명용 가정입니다.",
                style = OnTimeTheme.type.caption,
                color = OnTimeTheme.colors.textSecondary,
            )
        }
        onStartDemo?.let {
            Spacer(Modifier.height(Space.xs))
            Button("시연 시작", onClick = it, icon = OnTimeIcon.Play, modifier = Modifier.fillMaxWidth())
        }
    }
}
