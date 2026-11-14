package io.github.hyunolike.ontime.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.app.PlacePhoto
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.ButtonVariant
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.JourneyRail
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Photo
import io.github.hyunolike.ontime.design.component.PhotoCaption
import io.github.hyunolike.ontime.design.component.RailStop
import io.github.hyunolike.ontime.design.component.SideBeacon
import io.github.hyunolike.ontime.design.component.StopKind
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.Motion
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.docent.DocentState
import io.github.hyunolike.ontime.docent.Narration
import io.github.hyunolike.ontime.docent.NarrationPhase
import io.github.hyunolike.ontime.docent.word
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.PoiAhead
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.session.withEuro
import io.github.hyunolike.ontime.slack.Source

private typealias Photos = Map<String, PlacePhoto>

@Composable
private fun Photos.painter(id: String?): Painter? = this[id]?.let { painterResource(it.resource) }

private fun distanceText(meters: Int): String = if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"

@Composable
private fun DocentHeader(title: String, state: DocentState, trailing: String) {
    val wide = LocalWideLayout.current
    val trip = "${state.destination.name}까지 ${minutesText(state.remaining)}"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(OnTimeIcon.Route, tint = OnTimeTheme.colors.textSecondary)
        Text(
            if (wide) "$title · $trip" else trip,
            modifier = Modifier.weight(1f),
            style = OnTimeTheme.type.body,
            color = OnTimeTheme.colors.textSecondary,
            maxLines = 1,
        )
        // 예시로 넣은 해설을 실제 관광 데이터처럼 보이게 두지 않는다.
        if (state.source == Source.REPLAY) Chip(if (wide) "예시 데이터 재생" else "예시 데이터", icon = OnTimeIcon.Play)
        Text(trailing, style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
    }
}

/**
 * 주행 중 화면. 이야기는 귀로 듣는 것이고, 화면은 "어느 쪽 창밖에 무엇이 있는지"를 사진 한 장과 큰 글자로만 보여 준다.
 * 사진은 그곳이 보일 쪽에 둔다. 누를 수 있는 것은 "들러 볼까요?"에 답하는 큰 버튼 두 개뿐이다.
 */
@Composable
fun DocentDrivingScreen(state: DocentState, title: String, photos: Photos, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        DocentHeader(title, state, "D 주행 중")

        // 장면이 바뀔 때만 한 번 겹쳐 바뀐다. 반복되는 움직임은 없다.
        val telling = state.narration
        val scene = when {
            telling != null -> "story:${telling.ahead.poi.destination.id}"
            state.quiet != null -> "quiet"
            else -> "idle:${state.upcoming.firstOrNull()?.poi?.destination?.id}"
        }
        AnimatedContent(
            targetState = scene,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(Motion.scene)) togetherWith fadeOut(tween(Motion.fast)) },
            label = "scene",
        ) { current ->
            val narration = state.narration
            val quiet = state.quiet
            when {
                current.startsWith("story") && narration != null -> StoryScene(narration, photos, onAnswer)
                current == "quiet" && quiet != null -> QuietScene(quiet, state.upcoming.firstOrNull())
                else -> IdleScene(state, photos)
            }
        }

        JourneyRail(state.railStops(short = !LocalWideLayout.current))
    }
}

/** 사진과 글을 나란히 둔다. 사진은 [side] 쪽에 온다. 폭이 좁으면 사진을 위에 둔다. */
@Composable
private fun SideBySide(side: Side, photo: @Composable (Modifier) -> Unit, text: @Composable () -> Unit) {
    if (LocalWideLayout.current) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.xxl), verticalAlignment = Alignment.CenterVertically) {
            if (side == Side.LEFT) photo(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) { text() }
            if (side == Side.RIGHT) photo(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            photo(Modifier.fillMaxWidth().weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) { text() }
        }
    }
}

/** 지금 들려주는 이야기. */
@Composable
private fun StoryScene(narration: Narration, photos: Photos, onAnswer: (Boolean) -> Unit) {
    val ahead = narration.ahead
    val name = ahead.poi.destination.name
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    SideBySide(
        side = ahead.side,
        photo = { modifier ->
            Photo(photos.painter(ahead.poi.destination.id), description = name, modifier = modifier) {
                SideBeacon(left = ahead.side == Side.LEFT, description = ahead.side.word())
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(OnTimeIcon.Story, tint = colors.accent)
            Text("${narration.lead} ${ahead.side.word()} 창밖".trim(), style = type.title, color = colors.accent, maxLines = 1)
        }
        Text(name, style = if (name.length <= 4) type.display else type.displaySmall, maxLines = 1)
        when (narration.phase) {
            // 해설은 음성으로 나간다. 화면에는 동승자가 읽을 수 있게 세 줄까지만 둔다.
            NarrationPhase.TELLING -> Text(ahead.poi.story, style = type.bodyLarge, color = colors.textSecondary, maxLines = 3)
            NarrationPhase.ASKING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OnTimeIcon.Mic, tint = colors.route)
                    Text("들러 볼까요? \"들를래\"라고 말해도 됩니다", style = type.bodyLarge, maxLines = 2)
                }
                Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Button("들르기", { onAnswer(true) }, Modifier.weight(1.3f), variant = ButtonVariant.Route, icon = OnTimeIcon.AddStop)
                    Button("지나가기", { onAnswer(false) }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                }
            }
            NarrationPhase.ADDED -> Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(OnTimeIcon.AddStop, tint = colors.route, size = Size.iconLarge)
                Text(narration.text, style = type.bodyLarge, color = colors.route, maxLines = 2)
            }
            NarrationPhase.SKIPPED -> Text(narration.text, style = type.bodyLarge, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** 회전이 가까워 이야기를 쉬는 중. 사진도 내려서 길 안내가 먼저라는 것을 화면으로도 알린다. */
@Composable
private fun QuietScene(maneuver: Maneuver, next: PoiAhead?) {
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            Icon(OnTimeIcon.Quiet, tint = colors.textSecondary, size = 120.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("잠시 쉽니다", style = type.displaySmall, color = colors.textSecondary, maxLines = 1)
                Text("${distanceText(maneuver.distanceMeters)} 앞 ${maneuver.instruction}. 길 안내가 먼저입니다", style = type.title, maxLines = 2)
            }
        }
        if (next != null) {
            Text("회전을 지나면 ${next.poi.destination.name} 이야기를 들려드립니다", style = type.bodyLarge, color = colors.textSecondary, maxLines = 2)
        }
    }
}

/** 이야기와 이야기 사이. 다음에 나올 곳을 조용히 예고한다. */
@Composable
private fun IdleScene(state: DocentState, photos: Photos) {
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    val next = state.upcoming.firstOrNull()
    val heading = state.heading
    val place = next?.poi?.destination ?: heading ?: state.destination
    SideBySide(
        // 이미 들은 곳으로 가는 길이어도 사진은 그곳이 보일 쪽에 둔다.
        side = next?.side ?: state.ahead.find { it.poi.destination == place }?.side ?: Side.RIGHT,
        photo = { modifier -> Photo(photos.painter(place.id), description = place.name, modifier = modifier) },
    ) {
        when {
            next != null -> {
                Text("다음 이야기", style = type.title, color = colors.textSecondary, maxLines = 1)
                Text(next.poi.destination.name, style = type.displaySmall, maxLines = 1)
                Text("${distanceText(next.distanceMeters)} 앞 ${next.side.word()}. 가까워지면 들려드립니다", style = type.bodyLarge, color = colors.textSecondary, maxLines = 2)
            }
            heading != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OnTimeIcon.AddStop, tint = colors.route)
                    Text("들르러 가는 길", style = type.title, color = colors.route, maxLines = 1)
                }
                Text(heading.name, style = type.displaySmall, maxLines = 1)
                Text("차를 세우면 방문 도장이 찍힙니다", style = type.bodyLarge, color = colors.textSecondary, maxLines = 2)
            }
            else -> {
                Text("목적지", style = type.title, color = colors.textSecondary, maxLines = 1)
                Text(place.name, style = type.displaySmall, maxLines = 1)
                Text("${minutesText(state.remaining)} 남았습니다. 오늘 이야기는 모두 들었습니다", style = type.bodyLarge, color = colors.textSecondary, maxLines = 2)
            }
        }
    }
}

/** 지나온 곳, 차, 앞에 있는 곳, 목적지 순서. 폭이 좁으면 거리를 빼고 이름만 둔다. */
private fun DocentState.railStops(short: Boolean): List<RailStop> {
    val aheadIds = ahead.map { it.poi.destination.id }.toSet()
    val toldIds = told.map { it.destination.id }.toSet()
    val passed = told.filter { it.destination.id !in aheadIds }.map {
        RailStop(it.destination.name, if (it.destination in stamps) StopKind.Visited else StopKind.Told)
    }
    val coming = ahead.map {
        val place = it.poi.destination
        val kind = when {
            place in stamps -> StopKind.Visited
            place == heading -> StopKind.Waypoint
            place.id in toldIds -> StopKind.Told
            else -> StopKind.Ahead
        }
        RailStop(if (short || it.distanceMeters == 0) place.name else "${place.name} ${distanceText(it.distanceMeters)}", kind)
    }
    return passed + RailStop("", StopKind.Here) + coming + RailStop(destination.name, StopKind.Destination)
}

/**
 * 정차 중 화면. 오늘 길에 어떤 이야기가 있는지를 사진과 함께 보여 주고, 고른 곳으로 바로 안내를 시작할 수 있다.
 * 가로 화면은 두 단, 세로 화면은 한 단이다.
 */
@Composable
fun DocentStoppedScreen(
    state: DocentState,
    title: String,
    photos: Photos,
    started: Boolean,
    /** 예시 드라이브를 시작하거나 이어 간다. 재생이 없는 빌드에서는 null. */
    onDrive: (() -> Unit)?,
    onGuide: (String) -> Unit,
) {
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
        DocentHeader(title, state, "P 정차 중")
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) { PlacesPane(state, photos, onGuide) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { TripPane(state, photos, started, onDrive) }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                TripPane(state, photos, started, onDrive)
                PlacesPane(state, photos, onGuide)
            }
        }
    }
}

/** 지금 서 있는 곳이 들르기로 했던 곳이면 그곳. */
private fun DocentState.arrivedAt() = stamps.lastOrNull()?.takeIf { place ->
    ahead.any { it.poi.destination == place && it.distanceMeters == 0 }
}

@Composable
private fun PlacesPane(state: DocentState, photos: Photos, onGuide: (String) -> Unit) {
    val aheadIds = state.ahead.map { it.poi.destination.id }.toSet()
    val passed = state.told.filter { it.destination.id !in aheadIds }
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text("오늘 길 위의 이야기", style = OnTimeTheme.type.headline, maxLines = 1)
        passed.forEach { PlaceRow(it, null, state, photos, onGuide) }
        state.ahead.forEach { PlaceRow(it.poi, it, state, photos, onGuide) }
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Photo(photos.painter(state.destination.id), description = null, modifier = Modifier.size(88.dp), radius = Radius.md)
                Column(Modifier.weight(1f)) {
                    Text(state.destination.name, style = OnTimeTheme.type.title, maxLines = 1)
                    Text("목적지 · ${minutesText(state.remaining)}", style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
                }
                Icon(OnTimeIcon.Navigation, tint = OnTimeTheme.colors.textSecondary)
            }
        }
    }
}

/** 이야기가 있는 곳 하나. 아직 앞에 있고 들를 수 있는 곳이면 바로 안내를 시작하는 버튼이 붙는다. */
@Composable
private fun PlaceRow(poi: Poi, ahead: PoiAhead?, state: DocentState, photos: Photos, onGuide: (String) -> Unit) {
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    val place = poi.destination
    val told = poi in state.told
    val visited = place in state.stamps
    val heading = place == state.heading
    val canGuide = ahead != null && ahead.distanceMeters > 0 && poi.canStop && !visited && state.heading == null
    Card(Modifier.fillMaxWidth(), selected = heading, accent = colors.route, padding = PaddingValues(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Photo(photos.painter(place.id), description = null, modifier = Modifier.size(if (canGuide || told) 136.dp else 88.dp), radius = Radius.md)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(place.name, style = type.title, maxLines = 1)
                    when {
                        visited -> Chip("다녀옴", tone = Tone.Positive, icon = OnTimeIcon.Stamp)
                        heading -> Chip("들르러 가는 중", tone = Tone.Info, icon = OnTimeIcon.AddStop)
                        told -> Chip("들음", tone = Tone.Accent, icon = OnTimeIcon.Story)
                    }
                }
                Text(
                    when {
                        ahead == null -> "지나왔습니다"
                        ahead.distanceMeters == 0 -> "지금 여기"
                        else -> "${distanceText(ahead.distanceMeters)} 앞 ${ahead.side.word()}"
                    },
                    style = type.label,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                // 아직 듣지 않은 이야기는 미리 보여 주지 않는다. 길에서 듣는 재미를 남겨 둔다.
                if (told && !canGuide) Text(poi.story, style = type.body, color = colors.textSecondary, maxLines = 2)
                if (canGuide) {
                    Button(
                        "여기로 안내",
                        { onGuide(place.id) },
                        Modifier.padding(top = Space.xs),
                        variant = ButtonVariant.Route,
                        icon = OnTimeIcon.Navigation,
                    )
                }
            }
        }
    }
}

@Composable
private fun TripPane(state: DocentState, photos: Photos, started: Boolean, onDrive: (() -> Unit)?) {
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    val arrived = state.arrivedAt()
    val finished = started && state.remaining.inWholeSeconds == 0L
    val hero = arrived ?: state.heading ?: state.destination
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Photo(photos.painter(hero.id), description = hero.name, modifier = Modifier.fillMaxWidth().height(280.dp), scrim = true) {
            PhotoCaption {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        when {
                            arrived != null -> "도착했습니다"
                            finished -> "목적지에 도착했습니다"
                            state.heading != null -> "들를 곳"
                            else -> "목적지 · ${minutesText(state.remaining)}"
                        },
                        style = type.label,
                        color = if (arrived != null) colors.positive else colors.onPhoto,
                        maxLines = 1,
                    )
                    Text(hero.name, style = type.headline, color = colors.onPhoto, maxLines = 1)
                }
            }
        }

        StampStrip(state)

        if (onDrive != null && !finished) {
            when {
                !started -> Button("시연 시작", onDrive, Modifier.fillMaxWidth(), icon = OnTimeIcon.Play)
                state.heading == null ->
                    Button("${state.destination.name.withEuro()} 계속 안내", onDrive, Modifier.fillMaxWidth(), variant = ButtonVariant.Route, icon = OnTimeIcon.Navigation)
            }
        }
        if (state.source == Source.REPLAY) {
            Text(
                "거리와 해설은 예시 데이터이고 음성은 화면 자막으로 대신합니다. 사진은 위키미디어 공용의 CC0 사진입니다.",
                style = type.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** 들를 수 있는 곳마다 도장 자리를 하나씩 둔다. 차를 세워 들른 곳만 채워진다. */
@Composable
private fun StampStrip(state: DocentState) {
    val colors = OnTimeTheme.colors
    val places = (state.told + state.ahead.map { it.poi }).filter { it.canStop }.map { it.destination }.distinct()
    Card(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("방문 도장 ${state.stamps.size}/${places.size}", style = OnTimeTheme.type.title, maxLines = 1)
                Text("차를 세워 들른 곳에만 찍힙니다", style = OnTimeTheme.type.label, color = colors.textSecondary, maxLines = 1)
            }
            places.forEach { place ->
                val stamped = place in state.stamps
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .then(if (stamped) Modifier.background(colors.positive) else Modifier.border(2.dp, colors.outline, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(OnTimeIcon.Stamp, tint = if (stamped) colors.onAccent else colors.outline, size = 44.dp, description = if (stamped) "다녀옴" else null)
                    }
                    Text(place.name, style = OnTimeTheme.type.caption, color = if (stamped) colors.positive else colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}
