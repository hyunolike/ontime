package io.github.hyunolike.ontime.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.LocalDriving
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.outing.OutingPlan
import io.github.hyunolike.ontime.outing.OutingState
import io.github.hyunolike.ontime.outing.PlaceForecast
import io.github.hyunolike.ontime.outing.label
import io.github.hyunolike.ontime.platform.CrowdForecast
import io.github.hyunolike.ontime.platform.CrowdLevel
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import java.time.ZoneId

fun CrowdLevel.tone(): Tone = when (this) {
    CrowdLevel.RELAXED -> Tone.Positive
    CrowdLevel.NORMAL -> Tone.Info
    CrowdLevel.BUSY -> Tone.Caution
    CrowdLevel.PACKED -> Tone.Critical
}

fun CrowdLevel.icon(): OnTimeIcon = when (this) {
    CrowdLevel.RELAXED -> OnTimeIcon.CrowdRelaxed
    CrowdLevel.NORMAL -> OnTimeIcon.CrowdNormal
    CrowdLevel.BUSY -> OnTimeIcon.CrowdBusy
    CrowdLevel.PACKED -> OnTimeIcon.CrowdPacked
}

private fun Instant.hour(): Int = atZone(ZoneId.of("Asia/Seoul")).hour

/**
 * 주행 중 화면. 도착할 때의 혼잡 단계 하나만 크게 두고 누를 수 있는 요소는 없다.
 * 아래 막대는 도착 시각 앞뒤의 시간대별 예보이고, 도착하는 시간대에 표시가 붙는다.
 */
@Composable
fun OutingDrivingScreen(state: OutingState, plan: OutingPlan) {
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl)) {
        ScreenHeader(OnTimeIcon.Outing, "${plan.title} · ${plan.target.name}", state.source, trailing = "D 주행 중")

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("도착 예보", style = OnTimeTheme.type.title, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                Text("${state.arrival.clock()} 도착", style = OnTimeTheme.type.title, maxLines = 1)
            }
            CrowdHero(state.crowd, OnTimeTheme.type.display)
            ForecastStrip(state.hourly, state.arrival, Modifier.padding(top = Space.md), barHeight = 64.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(OnTimeIcon.Navigation, tint = OnTimeTheme.colors.textSecondary)
                Text(state.destination.name, style = OnTimeTheme.type.bodyLarge, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text("도착까지 ${minutesText(state.drive)}", style = OnTimeTheme.type.body, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
            }
        }

        SuggestionBanner(state.suggestion)
    }
}

/** 혼잡 단계를 아이콘, 글자, 색으로 함께 보여 준다. 단계가 바뀔 때 한 번만 겹쳐 바뀐다. */
@Composable
private fun CrowdHero(crowd: CrowdForecast, style: TextStyle) {
    val color by animateColorAsState(OnTimeTheme.colors.tone(crowd.level.tone()), tween(Motion.emphasized), label = "crowd")
    AnimatedContent(
        targetState = crowd.level,
        transitionSpec = { fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.fast)) },
        label = "level",
    ) { level ->
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            Icon(level.icon(), tint = color, size = (style.fontSize.value * 0.72f).dp, description = null)
            Text(level.label(), style = style, color = color, maxLines = 1)
        }
    }
}

/**
 * 시간대별 혼잡 예보. 막대의 높이가 혼잡도이고 색이 단계다.
 * 도착하는 시간대는 테두리와 "도착" 글자로 표시해, 지금이 아니라 그 시각을 본다는 것이 드러나게 한다.
 */
@Composable
fun ForecastStrip(hourly: List<CrowdForecast>, arrival: Instant, modifier: Modifier = Modifier, barHeight: Dp = 96.dp) {
    val colors = OnTimeTheme.colors
    val reduce = LocalDriving.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        hourly.forEach { slot ->
            val isArrival = slot.at.hour() == arrival.hour()
            val fraction by animateFloatAsState(
                (slot.percent / 100f).coerceIn(0.08f, 1f),
                tween(if (reduce) Motion.standard else Motion.emphasized),
                label = "bar",
            )
            val tone = colors.tone(slot.level.tone())
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(colors.track)
                        .then(if (isArrival) Modifier.border(3.dp, colors.textPrimary, RoundedCornerShape(Radius.sm)) else Modifier),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(if (isArrival) tone else tone.copy(alpha = 0.45f)),
                    )
                }
                Text(
                    if (isArrival) "${slot.at.hour()}시 도착" else "${slot.at.hour()}시",
                    style = OnTimeTheme.type.label,
                    color = if (isArrival) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 정차 중 화면. 왼쪽은 도착 예보의 근거이고, 오른쪽은 갈 만한 곳들의 비교다.
 * 비교는 곳마다 도착 시각이 다르므로, 각자의 도착 시각에 맞는 예보를 보여 준다.
 */
@Composable
fun OutingStoppedScreen(
    state: OutingState,
    plan: OutingPlan,
    arrived: Boolean,
    options: List<PlaceForecast>,
    choosing: String?,
    onChoose: (String) -> Unit,
    onStartDemo: (() -> Unit)?,
) {
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
        ScreenHeader(OnTimeIcon.Outing, "${plan.title} · ${plan.target.name}", state.source, trailing = "P 정차 중")
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) { ForecastPane(state, arrived) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ComparePane(options, choosing, arrived, onChoose, onStartDemo, state.source)
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                ForecastPane(state, arrived)
                ComparePane(options, choosing, arrived, onChoose, onStartDemo, state.source)
            }
        }
    }
}

@Composable
private fun ForecastPane(state: OutingState, arrived: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Column {
            Text(state.destination.name, style = OnTimeTheme.type.title, maxLines = 1)
            CrowdHero(state.crowd, OnTimeTheme.type.displaySmall)
            Text(
                if (arrived) "도착했습니다 · 혼잡도 ${state.crowd.percent}%" else "${state.arrival.clock()}에 도착하면 혼잡도 ${state.crowd.percent}%",
                style = OnTimeTheme.type.bodyLarge,
                color = OnTimeTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
        ForecastStrip(state.hourly, state.arrival)
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                FactRow(OnTimeIcon.Clock, "지금", state.now.clock())
                FactRow(OnTimeIcon.Car, "남은 주행 (내비)", minutesText(state.drive))
                FactRow(OnTimeIcon.Arrival, "도착 예정", state.arrival.clock())
                FactRow(state.crowd.level.icon(), "그 시간대의 혼잡 예보", "${state.crowd.level.label()} · ${state.crowd.percent}%")
            }
        }
    }
}

@Composable
private fun FactRow(icon: OnTimeIcon, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint = OnTimeTheme.colors.textSecondary)
        Text(label, Modifier.weight(1f), style = OnTimeTheme.type.body, maxLines = 1)
        Text(value, style = OnTimeTheme.type.bodyLarge, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun ComparePane(
    options: List<PlaceForecast>,
    choosing: String?,
    arrived: Boolean,
    onChoose: (String) -> Unit,
    onStartDemo: (() -> Unit)?,
    source: Source,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text("도착할 때의 혼잡 비교", style = OnTimeTheme.type.title, maxLines = 1)
        if (options.isEmpty()) {
            Text("비교할 곳의 예보를 불러오는 중입니다", color = OnTimeTheme.colors.textSecondary)
        }
        val quietest = options.minWithOrNull(compareBy<PlaceForecast> { it.crowd.percent })
        options.forEach { option ->
            val id = option.destination.id
            val tone: Color = OnTimeTheme.colors.tone(option.crowd.level.tone())
            Card(
                Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
                selected = option.current,
                accent = tone,
                onClick = if (!option.current && !arrived && choosing == null) ({ onChoose(id) }) else null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.destination.name, style = OnTimeTheme.type.bodyLarge, maxLines = 1)
                            when {
                                choosing == id -> Chip("변경 중", tone = Tone.Info)
                                option.current -> Chip("안내 중", tone = Tone.Positive, icon = OnTimeIcon.Navigation)
                                option == quietest -> Chip("가장 한적", tone = Tone.Positive)
                            }
                        }
                        Text(
                            "${option.arrival.clock()} 도착 · 주행 ${minutesText(option.drive)}",
                            style = OnTimeTheme.type.label,
                            color = OnTimeTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(option.crowd.level.icon(), tint = tone, size = Size.iconLarge)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(option.crowd.level.label(), style = OnTimeTheme.type.title, color = tone, maxLines = 1)
                            Text("${option.crowd.percent}%", style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (source == Source.REPLAY) {
            Text(
                "주행 시간과 혼잡도는 예시 값입니다. 혼잡 예보는 한적 서비스의 관광지 집중률과 서울 실시간 도시데이터로 바꿀 예정입니다.",
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
