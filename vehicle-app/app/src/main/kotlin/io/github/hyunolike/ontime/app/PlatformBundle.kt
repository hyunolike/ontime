package io.github.hyunolike.ontime.app

import androidx.annotation.DrawableRes
import io.github.hyunolike.ontime.platform.LocationGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.Poi
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
    /** 멈출 곳까지 달린다. 서 있다가 다시 부르면 이어서 달린다. 재생이 없는 빌드에서는 null. */
    val play: (suspend () -> Unit)?,
    /** 장소 id별 사진. 예시 빌드는 앱에 넣어 둔 사진을, 실제 빌드는 관광 데이터의 대표 사진을 쓴다. */
    val photos: Map<String, PlacePhoto> = emptyMap(),
    /** 음성으로 묻고 답할 수 있으면 true. false면 화면에 "말해도 된다"는 안내를 내지 않는다. */
    val voice: StateFlow<Boolean> = MutableStateFlow(false),
    /** 재생 값을 쓰고 있다는 표시. 넓은 화면과 좁은 화면용. */
    val replayChip: String = "예시 데이터 재생",
    val replayChipShort: String = "예시 데이터",
    /** 정차 화면 아래에 적는, 무엇이 실제 값이고 무엇이 예시인지에 대한 설명. */
    val replayNote: String = "예시 경로를 재생합니다. 거리와 해설, 들르는 데 드는 시간은 예시 값이고 해설은 화면 자막으로 보여 드립니다.",
    /** 출발 전 정차 화면의 출발 버튼. */
    val driveLabel: String = "경주 코스 시연 시작",
    /** 거리가 직선 거리면 true. 출발 전에는 진행 방향을 몰라 정차 화면에 쪽을 적지 않는다. */
    val straightLine: Boolean = false,
    /** 회전이 이 거리 안이면 새 이야기를 꺼내지 않는다. 직선 거리로 쪽을 정하는 빌드는 회전 뒤 쪽이 뒤집힐 수 있어 넓게 둔다. */
    val quietWithin: Int = 400,
    /** 충전하는 동안 걸어서 둘러볼 곳을 고를 때 쓰는 지금 위치. 없으면 그 카드를 보여 주지 않는다. */
    val walkFrom: LocationGateway? = null,
    /** 이 길의 장소 전부. 충전 대기와 이어 가기에 쓴다. */
    val catalog: () -> List<Poi> = { emptyList() },
    /** 들은 곳과 도장을 저장해 앱이 다시 시작돼도 이어 가면 true. 시연을 반복하는 예시 빌드는 끈다. */
    val remember: Boolean = false,
    /** 재생과 내비 경유지, 위치 추적을 출발 전으로 되돌린다. 없으면 "처음부터 다시"를 보여 주지 않는다. */
    val reset: (suspend () -> Unit)? = null,
    /** 들를지 묻고 기다리는 시간. 실제 음성은 무응답이면 답을 주지 않으므로 정해 둔다. */
    val askWindow: kotlin.time.Duration = kotlin.time.Duration.INFINITE,
    /** 위치 출처가 바뀌면(실제 위치 모드) 칩과 설명을 이 값으로 바꾼다. null이면 위의 기본 문구. */
    val modeChip: StateFlow<String?> = MutableStateFlow(null),
    val modeNote: StateFlow<String?> = MutableStateFlow(null),
    /** 있으면 앞길 전체를 보고 말할 순서와 길이를 정한다. */
    val planner: io.github.hyunolike.ontime.docent.NarrationPlanner? = null,
)

class PlacePhoto(@DrawableRes val resource: Int, val credit: String)
