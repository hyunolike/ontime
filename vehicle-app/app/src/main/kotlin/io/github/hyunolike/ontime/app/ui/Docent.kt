package io.github.hyunolike.ontime.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.docent.DocentState
import io.github.hyunolike.ontime.docent.Narration
import io.github.hyunolike.ontime.docent.NarrationPhase
import io.github.hyunolike.ontime.docent.word
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source

private fun Side.icon(): OnTimeIcon = if (this == Side.LEFT) OnTimeIcon.SideLeft else OnTimeIcon.SideRight

private fun distanceText(meters: Int): String = if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"

@Composable
private fun DocentHeader(title: String, state: DocentState, trailing: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(OnTimeIcon.Route, tint = OnTimeTheme.colors.textSecondary)
        Text(
            "$title · ${state.destination.name}까지 ${minutesText(state.remaining)}",
            style = OnTimeTheme.type.body,
            color = OnTimeTheme.colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        // 예시로 넣은 해설을 실제 관광 데이터처럼 보이게 두지 않는다.
        if (state.source == Source.REPLAY) Chip("예시 데이터 재생", icon = OnTimeIcon.Play)
        Text(trailing, style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
    }
}

/**
 * 주행 중 화면. 이야기는 귀로 듣는 것이고 화면은 "어느 쪽, 무엇"만 크게 보여 준다.
 * 누를 수 있는 요소는 없다. 회전이 가까우면 이야기를 쉬고 있다는 것만 알린다.
 */
@Composable
fun DocentDrivingScreen(state: DocentState, title: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl)) {
        DocentHeader(title, state, "D 주행 중")

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            // 장면이 바뀔 때만 한 번 겹쳐 바뀐다. 반복되는 움직임은 없다.
            val telling = state.narration
            val scene = when {
                telling != null -> "story:${telling.ahead.poi.destination.id}"
                state.quiet != null -> "quiet"
                else -> "idle:${state.upcoming.firstOrNull()?.poi?.destination?.id}"
            }
            AnimatedContent(
                targetState = scene,
                transitionSpec = { fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.fast)) },
                label = "scene",
            ) { current ->
                val narration = state.narration
                val quiet = state.quiet
                when {
                    current.startsWith("story") && narration != null -> StoryHero(narration)
                    current == "quiet" && quiet != null -> QuietHero(quiet, state.upcoming.firstOrNull())
                    else -> IdleHero(state.upcoming.firstOrNull())
                }
            }
        }

        UpNext(state)
    }
}

/** 지금 들려주는 이야기. 어느 쪽 창밖인지를 화살표와 글자로 가장 크게 둔다. */
@Composable
private fun StoryHero(narration: Narration) {
    val ahead = narration.ahead
    val colors = OnTimeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(OnTimeIcon.Story, tint = colors.info)
            Text("${ahead.side.word()} 창밖 · ${distanceText(ahead.distanceMeters)} 앞", style = OnTimeTheme.type.title, color = colors.info, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            if (ahead.side == Side.LEFT) Icon(ahead.side.icon(), tint = colors.textPrimary, size = 132.dp, description = "왼쪽")
            Text(
                ahead.poi.destination.name,
                style = if (ahead.poi.destination.name.length <= 4) OnTimeTheme.type.display else OnTimeTheme.type.displaySmall,
                maxLines = 1,
            )
            if (ahead.side == Side.RIGHT) Icon(ahead.side.icon(), tint = colors.textPrimary, size = 132.dp, description = "오른쪽")
        }
        when (narration.phase) {
            // 해설은 음성으로 나간다. 화면에는 동승자가 읽을 수 있게 세 줄까지만 둔다.
            NarrationPhase.TELLING -> Text(ahead.poi.story, style = OnTimeTheme.type.bodyLarge, color = colors.textSecondary, maxLines = 3)
            NarrationPhase.ASKING -> Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Chip("듣는 중", tone = Tone.Info, icon = OnTimeIcon.Mic)
                Text("들러 볼까요? \"들를래\"라고 말하면 경유지로 넣습니다", style = OnTimeTheme.type.bodyLarge, maxLines = 2)
            }
            NarrationPhase.ADDED -> Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(OnTimeIcon.AddStop, tint = colors.positive, size = Size.iconLarge)
                Text(narration.text, style = OnTimeTheme.type.bodyLarge, color = colors.positive, maxLines = 2)
            }
            NarrationPhase.SKIPPED -> Text(narration.text, style = OnTimeTheme.type.bodyLarge, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** 회전이 가까워 이야기를 쉬는 중. 길 안내가 먼저라는 것을 화면으로도 알린다. */
@Composable
private fun QuietHero(maneuver: Maneuver, next: PoiAhead?) {
    val colors = OnTimeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            Icon(OnTimeIcon.Quiet, tint = colors.textSecondary, size = 132.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("잠시 쉽니다", style = OnTimeTheme.type.displaySmall, color = colors.textSecondary, maxLines = 1)
                Text("${distanceText(maneuver.distanceMeters)} 앞 ${maneuver.instruction}. 길 안내가 먼저입니다", style = OnTimeTheme.type.title, maxLines = 1)
            }
        }
        if (next != null) {
            Text("회전을 지나면 ${next.poi.destination.name} 이야기를 들려드립니다", style = OnTimeTheme.type.bodyLarge, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun IdleHero(next: PoiAhead?) {
    val colors = OnTimeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text("다음 이야기", style = OnTimeTheme.type.title, color = colors.textSecondary, maxLines = 1)
        if (next == null) {
            Text("남은 이야기가 없습니다", style = OnTimeTheme.type.displaySmall, color = colors.textSecondary, maxLines = 1)
        } else {
            Text(next.poi.destination.name, style = OnTimeTheme.type.displaySmall, maxLines = 1)
            Text("${distanceText(next.distanceMeters)} 앞 ${next.side.word()}. 가까워지면 들려드립니다", style = OnTimeTheme.type.bodyLarge, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** 화면 아래의 한 줄: 들르기로 한 곳과 앞에 남은 이야기. */
@Composable
private fun UpNext(state: DocentState) {
    val colors = OnTimeTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        state.waypoint?.let { Chip("경유지 ${it.name}", tone = Tone.Positive, icon = OnTimeIcon.AddStop) }
        state.upcoming.take(2).forEach { ahead ->
            Chip("${ahead.poi.destination.name} ${distanceText(ahead.distanceMeters)}", icon = ahead.side.icon())
        }
        Spacer(Modifier.weight(1f))
        Text("이야기 ${state.told.size}개 들음", style = OnTimeTheme.type.label, color = colors.textSecondary, maxLines = 1)
    }
}

/**
 * 정차 중 화면. 오늘 길에 어떤 이야기가 있는지와, 들른 곳의 도장을 보여 준다.
 * 가로 화면은 두 단, 세로 화면은 한 단이다.
 */
@Composable
fun DocentStoppedScreen(state: DocentState, title: String, started: Boolean, onStartDemo: (() -> Unit)?) {
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
        DocentHeader(title, state, "P 정차 중")
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState())) { StoriesPane(state, started) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { StampPane(state, onStartDemo) }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                StoriesPane(state, started)
                StampPane(state, onStartDemo)
            }
        }
    }
}

@Composable
private fun StoriesPane(state: DocentState, started: Boolean) {
    val colors = OnTimeTheme.colors
    val arrivedAt = state.stamps.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(
            if (started && arrivedAt != null) "${arrivedAt.name}에 도착했습니다" else "오늘 길 위의 이야기",
            style = OnTimeTheme.type.headline,
            maxLines = 1,
        )
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                state.told.forEach { poi ->
                    StoryRow(OnTimeIcon.Check, colors.positive, poi.destination.name, "들었습니다", poi.story)
                }
                state.upcoming.forEach { ahead ->
                    StoryRow(
                        ahead.side.icon(),
                        colors.info,
                        ahead.poi.destination.name,
                        "${distanceText(ahead.distanceMeters)} 앞 ${ahead.side.word()}",
                        null,
                    )
                }
                StoryRow(OnTimeIcon.Navigation, colors.textSecondary, state.destination.name, "목적지 · ${minutesText(state.remaining)}", null)
            }
        }
    }
}

@Composable
private fun StoryRow(icon: OnTimeIcon, tint: androidx.compose.ui.graphics.Color, name: String, meta: String, story: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.Top) {
        Icon(icon, tint = tint, size = Size.iconLarge)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = OnTimeTheme.type.title, maxLines = 1)
                Text(meta, style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
            }
            story?.let { Text(it, style = OnTimeTheme.type.body, color = OnTimeTheme.colors.textSecondary, maxLines = 3) }
        }
    }
}

@Composable
private fun StampPane(state: DocentState, onStartDemo: (() -> Unit)?) {
    val colors = OnTimeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text("방문 도장", style = OnTimeTheme.type.title, maxLines = 1)
        Card(Modifier.fillMaxWidth(), selected = state.stamps.isNotEmpty()) {
            if (state.stamps.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OnTimeIcon.Stamp, tint = colors.textSecondary, size = Size.iconLarge)
                    Text("차를 세워 들른 곳에 도장이 찍힙니다", style = OnTimeTheme.type.body, color = colors.textSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    state.stamps.forEach { place ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(OnTimeIcon.Stamp, tint = colors.positive, size = 72.dp)
                            Column {
                                Text(place.name, style = OnTimeTheme.type.headline, color = colors.positive, maxLines = 1)
                                Text("기어 P로 확인한 방문", style = OnTimeTheme.type.label, color = colors.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            RuleRow(OnTimeIcon.Story, "3km 앞에서 미리, 어느 쪽 창밖인지와 함께 들려줍니다")
            RuleRow(OnTimeIcon.Quiet, "회전 400m 앞에서는 말하지 않습니다")
            RuleRow(OnTimeIcon.AddStop, "\"들를래\"라고 답하면 경유지로 넣습니다")
        }
        if (state.source == Source.REPLAY) {
            Text(
                "거리와 해설은 예시 데이터이고, 음성은 화면 자막으로 대신합니다. 해설은 한국관광공사 관광 데이터로 바꿀 예정입니다.",
                style = OnTimeTheme.type.caption,
                color = colors.textSecondary,
            )
        }
        onStartDemo?.let {
            Spacer(Modifier.height(Space.xs))
            Button("시연 시작", onClick = it, icon = OnTimeIcon.Play, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RuleRow(icon: OnTimeIcon, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint = OnTimeTheme.colors.textSecondary)
        Text(text, style = OnTimeTheme.type.body, maxLines = 2)
    }
}
