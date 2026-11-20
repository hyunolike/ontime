package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Maneuver
import io.github.hyunolike.ontime.platform.NaviGateway
import io.github.hyunolike.ontime.platform.RouteStatus
import io.github.hyunolike.ontime.slack.Reading
import io.github.hyunolike.ontime.slack.Source
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NaviHelper로 남은 주행 시간을 받고 목적지를 바꾼다.
 *
 * 에뮬레이터에서 확인한 것: `DestinationInfo.remainTime`은 초 단위이고,
 * `getDestinationInfo()`를 부를 때마다 `onDestinationInfo`로 답이 온다. 그래서 [pollEvery]마다 조회한다.
 * 아직 확인하지 못한 것: `requestTrip()`의 응답과 `TripInfo.eta`의 단위. 같은 초로 가정했다.
 */
class PleosNavi(
    context: Context,
    /** 일정에 등록된 목적지. 내비가 알려 준 목적지를 이 가운데 하나로 맞춘다. */
    private val known: List<Destination>,
    private val scope: CoroutineScope,
    private val pollEvery: Duration = 10.seconds,
) : NaviGateway {
    private val helper = NaviHelper(context)
    private val _route = MutableSharedFlow<RouteStatus>(replay = 1)
    private val _maneuver = MutableSharedFlow<Maneuver?>(replay = 1)
    private val tripMutex = Mutex()
    private var pendingTrip: CompletableDeferred<TripInfo?>? = null

    override val route: Flow<RouteStatus> = _route

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            PleosLog.event(
                "onDestinationInfo",
                "name=${destinationInfo.name} remainTime=${destinationInfo.remainTime} " +
                    "remainDistance=${destinationInfo.remainDistance} " +
                    "lat=${destinationInfo.latitude} lon=${destinationInfo.longitude}",
            )
            val reading = Reading(
                destinationInfo.remainTime.toDuration(REMAIN_TIME_UNIT),
                Instant.now(),
                Source.LIVE,
            )
            _route.tryEmit(RouteStatus(match(destinationInfo), reading))
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            PleosLog.event("onWaypointChanged", waypointChangedInfo.toString())
            runCatching { helper.getWaypointInfo() }
        }

        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            PleosLog.event(
                "onWayPointInfo",
                waypointInfo.joinToString { "${it.name} remainTime=${it.remainTime} remainDistance=${it.remainDistance}" }
                    .ifEmpty { "(없음)" },
            )
        }

        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            PleosLog.event("onTBTInfo", tbtInfo.take(30).joinToString { "${it.type} ${it.distance} ${it.description}" }.ifEmpty { "(없음)" })
            _maneuver.tryEmit(tbtInfo.firstOrNull()?.let { Maneuver(it.distance.toInt(), it.description.orEmpty().ifEmpty { it.type.name }) })
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            PleosLog.event(
                "onCurrentLocationInfo",
                "lat=${currentLocationInfo.latitude} lon=${currentLocationInfo.longitude} heading=${currentLocationInfo.heading}",
            )
        }

        override fun onRoutingCompleted() {
            PleosLog.event("onRoutingCompleted")
            requestDestinationInfo()
        }

        override fun onReRouteEnded() {
            // 길을 벗어나 경로를 다시 잡았다. 도슨트의 앞에 있는 장소는 위치로 계산하므로 다시 계산할 것은 없다.
            PleosLog.event("onReRouteEnded")
            requestDestinationInfo()
        }

        override fun onDestinationChanged() {
            PleosLog.event("onDestinationChanged")
            requestDestinationInfo()
        }

        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            PleosLog.event("onNavigationStatus", navigationStatus.name)
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            PleosLog.event(
                "onTripInfo",
                "eta=${tripInfo.eta} distance=${tripInfo.distance} socAtDest=${tripInfo.estimatedSocAtDest}",
            )
            pendingTrip?.complete(tripInfo)
        }

        override fun onError(errorCode: NaviErrorCode) {
            PleosLog.error("onError", errorCode.name)
            pendingTrip?.complete(null)
        }
    }

    /** 내비 서비스에 연결하고 목적지 정보를 받기 시작한다. */
    fun start() {
        PleosLog.call("NaviHelper.initialize")
        helper.initialize(
            {
                PleosLog.event("NaviHelper.initialize", "ok")
                helper.addListener(listener)
                scope.launch {
                    while (true) {
                        requestDestinationInfo()
                        delay(pollEvery)
                    }
                }
            },
            { cause -> PleosLog.error("NaviHelper.initialize", cause) },
        )
    }

    fun stop() {
        helper.removeListener(listener)
        helper.release()
    }

    override suspend fun estimateRemainingDrive(to: Destination): Duration? = tripMutex.withLock {
        val pending = CompletableDeferred<TripInfo?>()
        pendingTrip = pending
        PleosLog.call("requestTrip", to.name)
        helper.requestTrip(TripRequest(to.latitude, to.longitude, "", to.name, ""))
        val trip = withTimeoutOrNull(TRIP_TIMEOUT) { pending.await() }
        pendingTrip = null
        trip?.eta?.toDuration(REMAIN_TIME_UNIT)
    }

    /** 다음 회전. 도슨트가 말을 멈출 구간을 판단하는 데 쓴다. */
    val nextManeuver: Flow<Maneuver?> = _maneuver

    override suspend fun addWaypoint(to: Destination): Boolean {
        PleosLog.call("addWaypoint", to.name)
        return runCatching {
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = to.latitude,
                    longitude = to.longitude,
                    poiName = to.name,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = to.address,
                    poiSubId = "0",
                ),
            )
        }.onFailure { PleosLog.error("addWaypoint", it) }.isSuccess
    }

    override suspend fun removeWaypoint(): Boolean {
        PleosLog.call("removeWaypoint", "FIRST")
        return runCatching { helper.removeWaypoint(WaypointIndex.FIRST) }
            .onFailure { PleosLog.error("removeWaypoint", it) }.isSuccess
    }

    override suspend fun changeDestination(to: Destination): Boolean {
        PleosLog.call("requestRoute", to.name)
        return runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = to.latitude,
                    longitude = to.longitude,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = to.name,
                    poiId = "",
                    address = to.address,
                    poiSubId = "0",
                ),
            )
        }.onFailure { PleosLog.error("requestRoute", it) }.isSuccess
    }

    private fun requestDestinationInfo() {
        runCatching { helper.getDestinationInfo() }.onFailure { PleosLog.error("getDestinationInfo", it) }
        // 회전 안내와 현재 위치도 조회해야 답이 온다.
        runCatching { helper.getTBTInfo() }.onFailure { PleosLog.error("getTBTInfo", it) }
        runCatching { helper.getCurrentLocationInfo() }.onFailure { PleosLog.error("getCurrentLocationInfo", it) }
    }

    /** 좌표가 가까운 등록 목적지를 찾는다. 없으면 내비가 알려 준 그대로 쓴다. */
    private fun match(info: DestinationInfo): Destination =
        known.firstOrNull {
            abs(it.latitude - info.latitude) < NEAR_DEGREES && abs(it.longitude - info.longitude) < NEAR_DEGREES
        } ?: Destination("navi:${info.name}", info.name.orEmpty(), info.latitude, info.longitude, info.address.orEmpty())

    private companion object {
        /** remainTime 6600이 내비 화면의 1시간 50분과 같았다. */
        val REMAIN_TIME_UNIT = DurationUnit.SECONDS
        val TRIP_TIMEOUT = 15.seconds

        /** 약 150m. */
        const val NEAR_DEGREES = 0.0015
    }
}
