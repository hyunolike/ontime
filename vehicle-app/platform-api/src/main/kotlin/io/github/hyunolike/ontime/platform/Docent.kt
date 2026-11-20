package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Source
import kotlinx.coroutines.flow.Flow

/** 차의 진행 방향을 기준으로 어느 쪽 창밖인지. */
enum class Side { LEFT, RIGHT }

/**
 * 이야기가 있는 곳 하나. [story]는 운전 중 듣기 좋게 두세 문장으로 줄인 해설이다.
 * [source]가 REPLAY면 실제 관광 데이터가 아니라 예시로 넣은 해설이다.
 */
data class Poi(
    val destination: Destination,
    val story: String,
    val canStop: Boolean,
    val source: Source,
    /** 들르면 무엇을 하게 되는지 한 줄. 들를지 정할 때 보여 준다. */
    val highlight: String = "",
    /** 둘러보는 데 드는 시간(분). */
    val stayMinutes: Int? = null,
    /**
     * 들르면 목적지 도착이 늦어지는 시간(분). 둘러보는 시간을 포함한다.
     * 실제 구현은 내비의 소요 시간 조회로 얻은 우회 시간에 [stayMinutes]를 더한다.
     */
    val extraMinutes: Int? = null,
    /** 시간이 촉박할 때 쓰는 한 문장 해설. 비어 있으면 [story]의 첫 문장을 쓴다. */
    val shortStory: String = "",
)

/**
 * 앞으로 지나갈 곳. 그곳까지 남은 거리와, 그곳이 보일 쪽.
 * 경로를 아는 구현은 경로를 따라 남은 거리를, 위치로 계산하는 구현은 직선 거리를 넣는다.
 */
data class PoiAhead(
    val poi: Poi,
    val distanceMeters: Int,
    val side: Side,
    /**
     * [side]를 믿고 소개해도 되면 true. 위치로 계산하는 구현은 정면에 가까운 먼 곳이나 옆, 뒤에 있는 곳을
     * false로 둔다. 도슨트는 true인 곳만 이야기를 시작하고, 화면은 false인 곳의 쪽을 적지 않는다.
     */
    val settled: Boolean = true,
)

/** 다음 회전이나 합류. 운전자가 길 안내에 집중해야 하는 구간을 알기 위해 쓴다. */
data class Maneuver(
    val distanceMeters: Int,
    val instruction: String,
    /** 꺾는 쪽. 좌회전, 우회전처럼 진행 방향이 크게 바뀌는 회전만 넣고, 갈림길이나 직진은 null. */
    val turn: Side? = null,
)

/** 지금 달리는 길의 상황. 실제 구현은 NaviHelper의 회전 안내와 속도 정보를 쓴다. */
interface RoadGateway {
    /** 다음 회전. 가까운 회전이 없으면 null. */
    val nextManeuver: Flow<Maneuver?>

    val speedMetersPerSecond: Flow<Double>
}

/** 경로 앞쪽에 있는 이야기들. 실차 구현은 위치와 진행 방향, 장소 목록을 겹쳐 만든다(`LiveStories`). */
interface StoryGateway {
    /** 가까운 순서. 이미 지나친 곳은 빠진다. */
    val ahead: Flow<List<PoiAhead>>
}

/** 위치 한 점. [heading]은 북쪽 0도, 시계 방향이고 모르면 null. [speedMetersPerSecond]는 m/s. */
data class Fix(val latitude: Double, val longitude: Double, val heading: Double?, val speedMetersPerSecond: Double)

/** 차의 위치. 실차는 위치 센서, 시연은 경로 재생이 내보낸다. */
interface LocationGateway {
    val fixes: Flow<Fix>
}
