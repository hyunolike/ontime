package io.github.hyunolike.ontime.app

import io.github.hyunolike.ontime.platform.DemoAdjuster
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.platform.TimeSource
import io.github.hyunolike.ontime.platform.VehicleGateway
import io.github.hyunolike.ontime.session.TripPlan

/** 빌드 종류(flavor)마다 `Platform.create()`가 만들어 주는 차량 입력 묶음. */
class PlatformBundle(
    val plan: TripPlan,
    /** 화면에 보여 줄 출발 시각 문구. */
    val departureLabel: String,
    val time: TimeSource,
    val navi: NaviGateway,
    val vehicle: VehicleGateway,
    val speech: SpeechGateway,
    /** 시연 재생을 시작한다. 재생이 없는 빌드에서는 null. */
    val play: (suspend () -> Unit)?,
    /** 화면이 뒤로 가도 계산을 이어 가야 하면 true. 실제 차량 입력을 받는 빌드에서 켠다. */
    val keepAlive: Boolean = false,
    /** 실제 차량 입력 위에 시연용 조정을 얹을 수 있으면 그 조정기. 재생 빌드에서는 null. */
    val demo: DemoAdjuster? = null,
)
