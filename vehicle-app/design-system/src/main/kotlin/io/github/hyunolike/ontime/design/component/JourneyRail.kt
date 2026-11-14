package io.github.hyunolike.ontime.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Space

/** 여정 선 위의 한 점이 어떤 곳인지. */
enum class StopKind {
    /** 이야기를 들은 곳. */
    Told,
    /** 차를 세워 들른 곳. */
    Visited,
    /** 지금 차가 있는 자리. */
    Here,
    /** 앞에 있는, 아직 듣지 않은 이야기. */
    Ahead,
    /** 들르기로 한 곳. */
    Waypoint,
    Destination,
}

data class RailStop(val label: String, val kind: StopKind)

/**
 * 오늘 길을 한 줄로 보여 준다. 지나온 구간은 이야기 색으로 채우고 앞의 구간은 비워 둔다.
 * 간격은 실제 거리에 비례하지 않는다. 순서와 "어디쯤인지"만 전한다.
 */
@Composable
fun JourneyRail(stops: List<RailStop>, modifier: Modifier = Modifier) {
    val colors = OnTimeTheme.colors
    val here = stops.indexOfFirst { it.kind == StopKind.Here }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        stops.forEachIndexed { index, stop ->
            if (index > 0) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Space.sm)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (here < 0 || index <= here) colors.accent else colors.track),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                when (stop.kind) {
                    StopKind.Told -> Box(Modifier.size(18.dp).clip(CircleShape).background(colors.accent))
                    StopKind.Ahead -> Box(Modifier.size(18.dp).clip(CircleShape).border(3.dp, colors.textSecondary, CircleShape))
                    StopKind.Visited -> Icon(OnTimeIcon.Stamp, tint = colors.positive, size = 28.dp)
                    StopKind.Here -> Icon(OnTimeIcon.Car, tint = colors.textPrimary, size = 32.dp)
                    StopKind.Waypoint -> Icon(OnTimeIcon.AddStop, tint = colors.route, size = 28.dp)
                    StopKind.Destination -> Icon(OnTimeIcon.Navigation, tint = colors.textSecondary, size = 28.dp)
                }
                if (stop.label.isNotEmpty()) {
                    Text(
                        stop.label,
                        style = OnTimeTheme.type.caption,
                        color = when (stop.kind) {
                            StopKind.Waypoint -> colors.route
                            StopKind.Visited -> colors.positive
                            StopKind.Here -> colors.textPrimary
                            else -> colors.textSecondary
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
