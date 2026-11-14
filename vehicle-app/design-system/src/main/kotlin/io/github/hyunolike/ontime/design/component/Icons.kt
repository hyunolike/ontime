package io.github.hyunolike.ontime.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.hyunolike.ontime.design.R
import io.github.hyunolike.ontime.design.theme.OnTimeTheme
import io.github.hyunolike.ontime.design.theme.Size

/** Material Symbols Rounded(Apache 2.0)에서 가져온 아이콘. 48dp 격자 기준이다. */
enum class OnTimeIcon(@DrawableRes val resource: Int) {
    Car(R.drawable.ic_directions_car),
    Parking(R.drawable.ic_local_parking),
    Walk(R.drawable.ic_directions_walk),
    Buffer(R.drawable.ic_shield),
    Clock(R.drawable.ic_schedule),
    Flight(R.drawable.ic_flight_takeoff),
    Mic(R.drawable.ic_mic),
    Check(R.drawable.ic_check_circle),
    Warning(R.drawable.ic_warning),
    Error(R.drawable.ic_error),
    Navigation(R.drawable.ic_navigation),
    Swap(R.drawable.ic_swap_horiz),
    Play(R.drawable.ic_play_arrow),
    Info(R.drawable.ic_info),
    ArrowForward(R.drawable.ic_arrow_forward),
    Charger(R.drawable.ic_ev_station),

    // 도슨트용 아이콘은 이 앱을 위해 따로 그린 것이다.
    SideLeft(R.drawable.ic_side_left),
    SideRight(R.drawable.ic_side_right),
    Story(R.drawable.ic_story),
    Quiet(R.drawable.ic_quiet),
    Stamp(R.drawable.ic_stamp),
    AddStop(R.drawable.ic_add_stop),
    Route(R.drawable.ic_route),
}

/** 뜻을 전하는 아이콘에는 [description]을 넣고, 옆 글자를 꾸미기만 하면 null로 둔다. */
@Composable
fun Icon(
    icon: OnTimeIcon,
    modifier: Modifier = Modifier,
    tint: Color = OnTimeTheme.colors.textPrimary,
    size: Dp = Size.icon,
    description: String? = null,
) {
    Image(
        painter = painterResource(icon.resource),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
