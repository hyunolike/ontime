package io.github.hyunolike.ontime.platform

import io.github.hyunolike.ontime.slack.Source
import kotlinx.coroutines.flow.Flow

/** 차의 진행 방향을 기준으로 어느 쪽 창밖인지. */
enum class Side { LEFT, RIGHT }

/**
 * 이야기가 있는 곳 하나. [story]는 운전 중 듣기 좋게 두세 문장으로 줄인 해설이다.
 * [source]가 REPLAY면 실제 관광 데이터가 아니라 예시로 넣은 해설이다.
 */
data class Poi(val destination: Destination, val story: String, val canStop: Boolean, val source: Source)

/** 앞으로 지나갈 곳. 경로를 따라 남은 거리와, 그곳이 보일 쪽. */
data class PoiAhead(val poi: Poi, val distanceMeters: Int, val side: Side)

/** 다음 회전이나 합류. 운전자가 길 안내에 집중해야 하는 구간을 알기 위해 쓴다. */
data class Maneuver(val distanceMeters: Int, val instruction: String)

/** 지금 달리는 길의 상황. 실제 구현은 NaviHelper의 회전 안내와 속도 정보를 쓴다. */
interface RoadGateway {
    /** 다음 회전. 가까운 회전이 없으면 null. */
    val nextManeuver: Flow<Maneuver?>

    val speedMetersPerSecond: Flow<Double>
}

/** 경로 앞쪽에 있는 이야기들. 실제 구현은 위치와 진행 방향, 관광 데이터를 겹쳐 만든다. */
interface StoryGateway {
    /** 가까운 순서. 이미 지나친 곳은 빠진다. */
    val ahead: Flow<List<PoiAhead>>
}
