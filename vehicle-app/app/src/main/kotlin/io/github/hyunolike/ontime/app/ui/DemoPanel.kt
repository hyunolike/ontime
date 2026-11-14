package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.hyunolike.ontime.design.component.Button
import io.github.hyunolike.ontime.design.component.ButtonVariant
import io.github.hyunolike.ontime.design.component.Card
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.platform.DemoState

/**
 * 발표자용 시연 조정 패널. 에뮬레이터는 실제로 달리지 않으므로 정체와 주행 상태만 만들어 넣는다.
 * 목적지 변경과 소요 시간 조회는 실제 SDK가 그대로 처리한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(
    state: DemoState,
    onAddDelay: () -> Unit,
    onClearDelay: () -> Unit,
    onToggleDriving: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth(), selected = true, accent = OnTimeTheme.colors.info) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text("시연 조정", style = OnTimeTheme.type.title)
            Text(
                "정체 지연 ${state.delay.inWholeMinutes}분 · 가상 주행 ${if (state.virtualDriving) "켜짐" else "꺼짐"}",
                style = OnTimeTheme.type.label,
                color = OnTimeTheme.colors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Button("정체 +18분", onAddDelay, icon = OnTimeIcon.Warning)
                Button("정체 해소", onClearDelay, variant = ButtonVariant.Secondary)
                Button(if (state.virtualDriving) "가상 주행 끄기" else "가상 주행 켜기", onToggleDriving, variant = ButtonVariant.Secondary, icon = OnTimeIcon.Car)
                Button("닫기", onClose, variant = ButtonVariant.Secondary)
            }
        }
    }
}
