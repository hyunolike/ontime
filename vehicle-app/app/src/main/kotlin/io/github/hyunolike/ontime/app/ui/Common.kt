package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.hyunolike.ontime.design.component.Chip
import io.github.hyunolike.ontime.design.component.Icon
import io.github.hyunolike.ontime.design.component.OnTimeIcon
import io.github.hyunolike.ontime.design.component.Text
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Space
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.slack.Source

/** 화면 맨 위의 일정 한 줄. 어느 화면에서나 같은 자리에 둔다. */
@Composable
fun TripHeader(
    plan: TripPlan,
    departureLabel: String,
    source: Source?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(OnTimeIcon.Flight, tint = OnTimeTheme.colors.textSecondary)
        Text(
            "$departureLabel · 수속 마감 ${plan.deadline.clock()}",
            style = OnTimeTheme.type.body,
            color = OnTimeTheme.colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        // 재생한 값을 실제 값처럼 보이게 두지 않는다.
        if (source == Source.REPLAY) Chip("시연 재생", icon = OnTimeIcon.Play)
        trailing?.let { Text(it, style = OnTimeTheme.type.label, color = OnTimeTheme.colors.textSecondary, maxLines = 1) }
    }
}
