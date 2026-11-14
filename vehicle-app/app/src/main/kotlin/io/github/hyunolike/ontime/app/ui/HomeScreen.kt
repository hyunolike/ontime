package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.hyunolike.ontime.app.Mode
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.LocalWideLayout
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.design.theme.Tone
import io.github.hyunolike.ontime.outing.OutingPlan
import io.github.hyunolike.ontime.session.TripPlan

/** 일정 목록. 정차 중에만 보인다. 일정의 종류에 따라 온타임이 보는 것이 다르다. */
@Composable
fun HomeScreen(outing: OutingPlan, airport: TripPlan, airportLabel: String, onSelect: (Mode) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Size.screenPadding, vertical = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("온타임", style = OnTimeTheme.type.headline, maxLines = 1)
            Text(
                "내비는 언제 도착하는지 알려 줍니다. 온타임은 도착했을 때 그곳이 어떨지 알려 줍니다.",
                style = OnTimeTheme.type.bodyLarge,
                color = OnTimeTheme.colors.textSecondary,
            )
        }
        val cards: @Composable (Modifier) -> Unit = { modifier ->
            TripCard(
                modifier = modifier,
                icon = OnTimeIcon.Outing,
                kind = outing.title,
                title = outing.target.name,
                description = "도착할 때 얼마나 붐빌지 예보하고, 붐빌 것 같으면 한적한 곳을 제안합니다.",
                onClick = { onSelect(Mode.OUTING) },
            )
            TripCard(
                modifier = modifier,
                icon = OnTimeIcon.Flight,
                kind = "$airportLabel · ${airport.deadlineLabel} ${airport.deadline.clock()}",
                title = airport.title,
                description = "탑승 마감까지 남는 여유를 계산하고, 줄어들면 더 가까운 주차장을 제안합니다.",
                onClick = { onSelect(Mode.AIRPORT) },
            )
        }
        if (LocalWideLayout.current) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) { cards(Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) { cards(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun TripCard(
    modifier: Modifier,
    icon: OnTimeIcon,
    kind: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(modifier, onClick = onClick, padding = PaddingValues(Space.xxl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, tint = OnTimeTheme.colors.positive, size = Size.iconLarge)
                Text(kind, style = OnTimeTheme.type.body, color = OnTimeTheme.colors.textSecondary, maxLines = 1)
            }
            Text(title, style = OnTimeTheme.type.headline, maxLines = 1)
            Text(description, style = OnTimeTheme.type.body, color = OnTimeTheme.colors.textSecondary)
            Chip("예시 일정", tone = Tone.Info, icon = OnTimeIcon.Play)
        }
    }
}
