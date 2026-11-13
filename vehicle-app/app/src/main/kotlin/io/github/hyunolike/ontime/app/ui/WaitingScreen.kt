package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Size
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.session.TripPlan

/** 일정은 있는데 내비 경로가 아직 없을 때의 화면. 주차장으로 안내를 시작할 수 있다. */
@Composable
fun WaitingScreen(plan: TripPlan, departureLabel: String, onStartGuidance: () -> Unit) {
    val first = plan.parkingChoices.firstOrNull()?.destination
    Column(
        Modifier.fillMaxSize().padding(horizontal = Size.screenPadding, vertical = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        TripHeader(plan, departureLabel, source = null)
        Column(Modifier.weight(1f).widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(Space.lg, androidx.compose.ui.Alignment.CenterVertically)) {
            Text(plan.title, style = OnTimeTheme.type.headline, maxLines = 2)
            Text(
                "아직 이 일정으로 가는 경로가 없습니다. 안내를 시작하면 수속 마감까지의 여유를 계산해 드립니다.",
                style = OnTimeTheme.type.bodyLarge,
                color = OnTimeTheme.colors.textSecondary,
            )
            if (first != null) {
                Button("${first.name} 안내 시작", onClick = onStartGuidance, icon = OnTimeIcon.Navigation)
            }
        }
    }
}
