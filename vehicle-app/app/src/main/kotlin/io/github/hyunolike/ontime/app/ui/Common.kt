package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.component.BudgetSegment
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.component.budgetSegmentColor
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.slack.Source

/** 시연 조정 패널을 여는 방법과 지금 켜진 조정. 실제 SDK 빌드에서만 제공된다. */
class DemoControls(val note: String?, val open: () -> Unit)

val LocalDemoControls = staticCompositionLocalOf<DemoControls?> { null }

/**
 * 화면 맨 위의 일정 한 줄. 어느 화면에서나 같은 자리에 둔다.
 * 시연 조정이 가능하면 이 줄을 길게 눌러 조정 패널을 연다. 운전자에게는 보이지 않는 발표자용 동작이다.
 */
@Composable
fun TripHeader(plan: TripPlan, departureLabel: String, source: Source?, modifier: Modifier = Modifier) {
    val demo = LocalDemoControls.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (demo != null) Modifier.pointerInput(demo) { detectTapGestures(onLongPress = { demo.open() }) } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OnTimeIcon.Flight, tint = OnTimeTheme.colors.textSecondary)
        Text(
            "$departureLabel · 수속 마감 ${plan.deadline.clock()}",
            style = OnTimeTheme.type.body,
            color = OnTimeTheme.colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        // 재생하거나 조정한 값을 실제 값처럼 보이게 두지 않는다.
        when (source) {
            Source.REPLAY -> Chip("시연 재생", icon = OnTimeIcon.Play)
            Source.ADJUSTED -> Chip("시연 조정", icon = OnTimeIcon.Play)
            else -> Unit
        }
        demo?.note?.let { Chip(it, icon = OnTimeIcon.Play) }
    }
}

/** 막대 아래의 범례. 막대의 색 구간이 무엇인지 글자로 붙여 1~2초 안에 읽히게 한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetLegend(segments: List<BudgetSegment>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // 마지막 구간(여유)은 큰 숫자가 이미 말해 주므로 범례에서 뺀다.
        segments.dropLast(1).forEachIndexed { index, segment ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(budgetSegmentColor(segments, index)))
                Text(
                    "${segment.label} ${segment.minutes.toInt()}분",
                    style = OnTimeTheme.type.label,
                    color = OnTimeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
