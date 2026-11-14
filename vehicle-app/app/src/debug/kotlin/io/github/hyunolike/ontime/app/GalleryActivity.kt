package io.github.hyunolike.ontime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.BudgetSegment
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.ButtonVariant
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.JourneyRail
import io.github.hyunolike.ontime.design.component.RailStop
import io.github.hyunolike.ontime.design.component.StopKind
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.SlackNumber
import io.github.hyunolike.ontime.design.component.StatusBadge
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.component.TimeBudgetBar
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Radius
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone

/** 디자인 시스템의 토큰과 구성 요소를 한 화면에 늘어놓는다. 주간과 야간을 바꿔 볼 수 있다. */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by remember { mutableStateOf(intent.getBooleanExtra("dark", true)) }
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                OnTimeTheme(darkTheme = dark) { Gallery(dark) { dark = !dark } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery(dark: Boolean, onToggleTheme: () -> Unit) {
    val colors = OnTimeTheme.colors
    val type = OnTimeTheme.type
    var minutes by remember { mutableIntStateOf(9) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val tone = when {
        minutes >= 15 -> Tone.Positive
        minutes >= 5 -> Tone.Caution
        else -> Tone.Critical
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Size.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.xxl),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Text("길벗 디자인 시스템", style = type.headline)
            Button(if (dark) "주간으로 보기" else "야간으로 보기", onToggleTheme, variant = ButtonVariant.Secondary)
        }

        Section("색") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                listOf(
                    "background" to colors.background, "surface" to colors.surface, "surfaceRaised" to colors.surfaceRaised,
                    "outline" to colors.outline, "track" to colors.track, "textPrimary" to colors.textPrimary,
                    "textSecondary" to colors.textSecondary, "accent 이야기" to colors.accent, "route 길" to colors.route,
                    "positive 다녀옴" to colors.positive, "caution" to colors.caution, "critical" to colors.critical,
                ).forEach { (name, color) -> Swatch(name, color) }
            }
        }

        Section("글자") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("대릉원", style = type.display)
                Text("displaySmall 76 · 국립경주박물관", style = type.displaySmall)
                Text("headline 44 · 오늘 길 위의 이야기", style = type.headline)
                Text("title 32 · 5분 뒤 오른쪽 창밖", style = type.title)
                Text("bodyLarge 28 · 신라의 왕과 귀족이 잠든 무덤 스물세 기가 모여 있습니다", style = type.bodyLarge)
                Text("body 24 · 선덕여왕 때 세운 천문 관측대입니다", style = type.body)
                Text("label 20 · 2.9km 앞 오른쪽", style = type.label)
                Text("caption 18 · 가장 작은 글자", style = type.caption)
            }
        }

        Section("여유 숫자와 상태") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Button("− 6분", { minutes -= 6 }, variant = ButtonVariant.Secondary)
                    Button("+ 6분", { minutes += 6 }, variant = ButtonVariant.Secondary)
                    StatusBadge("여유 있음", Tone.Positive)
                    StatusBadge("주의", Tone.Caution)
                    StatusBadge("위험", Tone.Critical)
                }
                SlackNumber(minutes, tone, style = type.displaySmall)
                TimeBudgetBar(
                    listOf(
                        BudgetSegment("주행", 38f, OnTimeIcon.Car),
                        BudgetSegment("주차", 6f, OnTimeIcon.Parking),
                        BudgetSegment("도보", 8f, OnTimeIcon.Walk),
                        BudgetSegment("안전 버퍼", 3f, OnTimeIcon.Buffer),
                        BudgetSegment("여유", minutes.coerceAtLeast(0).toFloat(), OnTimeIcon.Clock, tone),
                    ),
                    selected = selected,
                    onSelect = { selected = if (selected == it) null else it },
                    height = 36.dp,
                )
            }
        }

        Section("버튼") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Button("시연 시작", {}, icon = OnTimeIcon.Play)
                Button("여기로 안내", {}, variant = ButtonVariant.Route, icon = OnTimeIcon.Navigation)
                Button("지나가기", {}, variant = ButtonVariant.Secondary)
                Button("불러오는 중", {}, loading = true)
                Button("비활성", {}, enabled = false)
            }
        }

        Section("카드와 표식") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Chip("예시 데이터 재생", icon = OnTimeIcon.Play)
                    Chip("들음", tone = Tone.Accent, icon = OnTimeIcon.Story)
                    Chip("들르러 가는 중", tone = Tone.Info, icon = OnTimeIcon.AddStop)
                    Chip("다녀옴", tone = Tone.Positive, icon = OnTimeIcon.Stamp)
                }
                JourneyRail(
                    listOf(
                        RailStop("대릉원", StopKind.Visited),
                        RailStop("", StopKind.Here),
                        RailStop("첨성대 1.2km", StopKind.Waypoint),
                        RailStop("국립경주박물관 3.4km", StopKind.Ahead),
                        RailStop("불국사", StopKind.Destination),
                    ),
                )
                Card(Modifier.fillMaxWidth(), selected = true, accent = colors.route) { Text("선택된 카드 · 들르러 가는 곳", style = type.bodyLarge) }
                Card(Modifier.fillMaxWidth(), onClick = {}) { Text("누를 수 있는 카드", style = type.bodyLarge) }
            }
        }

        Section("아이콘") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                OnTimeIcon.entries.forEach { Icon(it, size = Size.iconLarge, description = it.name) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(title, style = OnTimeTheme.type.title, color = OnTimeTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val shape = RoundedCornerShape(Radius.md)
        Box(Modifier.size(150.dp, 72.dp).clip(shape).background(color).border(1.dp, OnTimeTheme.colors.outline, shape))
        Text(name, style = OnTimeTheme.type.caption, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
    }
}
