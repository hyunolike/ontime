package io.github.hyunolike.ontime.platform

/**
 * 앞길의 모양. 경로를 아는 구현(재생기)이 내보낸다. 위치는 출발점에서 경로를 따라 잰 미터다.
 * 말할 순서를 미리 계획하는 데 쓴다(`NarrationPlanner`).
 */
interface RouteShape {
    /** 경로 전체 길이. */
    val length: Double

    /** 지금 경로를 따라 달린 거리. */
    val progress: Double

    /** 경로 위 [meters] 지점의 위치와 진행 방향. 속도는 달리는 중으로 둔다. */
    fun fixAt(meters: Double): Fix

    /** [meters] 지점에서 본 다음 좌회전이나 우회전. 지금 판정(`TrackPlayer.nextTurn`)과 같은 규칙이다. */
    fun turnAfter(meters: Double): Maneuver?

    /** 회전과 갈림길이 있는 경로 위 미터. 회전 앞 조용 구간을 구하는 데 쓴다. */
    val maneuverMeters: List<Double>

    /** 경로에서 [place]와 가장 가까운 지점의 미터. */
    fun meterOf(place: Destination): Double
}
