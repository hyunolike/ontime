package io.github.hyunolike.ontime.app

import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RoadGateway
import io.github.hyunolike.ontime.platform.StoryGateway
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
    /** SDK 호출을 시험하는 동작. `adb shell am start ... --es poc <이름>`으로 부른다. 없으면 null. */
    val poc: ((String) -> Unit)? = null,
    /** 드라이브 도슨트에 쓰는 입력. 있으면 앱은 도슨트 화면으로 시작한다. */
    val docent: DocentBundle? = null,
)

/** 도슨트에 쓰는 입력 묶음. 길의 상황([road])과 앞에 있는 이야기([stories])가 더 있다. */
class DocentBundle(
    val title: String,
    val time: TimeSource,
    val navi: NaviGateway,
    val vehicle: VehicleGateway,
    val speech: SpeechGateway,
    val road: RoadGateway,
    val stories: StoryGateway,
    val play: (suspend () -> Unit)?,
)
