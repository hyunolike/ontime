package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.github.hyunolike.ontime.platform.Destination
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
 * SDK 파일에서 확인한 것: `DestinationInfo.remainTime`, `TripInfo.eta`, `requestTrip()`.
 * 에뮬레이터에서 아직 확인하지 못한 것: 두 값의 단위, 정보가 저절로 오는지 조회해야 오는지.
 * 그래서 단위는 [REMAIN_TIME_UNIT] 한 곳에 두고, 정보는 [pollEvery]마다 직접 조회한다.
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

        override fun onRoutingCompleted() {
            PleosLog.event("onRoutingCompleted")
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
    }

    /** 좌표가 가까운 등록 목적지를 찾는다. 없으면 내비가 알려 준 그대로 쓴다. */
    private fun match(info: DestinationInfo): Destination =
        known.firstOrNull {
            abs(it.latitude - info.latitude) < NEAR_DEGREES && abs(it.longitude - info.longitude) < NEAR_DEGREES
        } ?: Destination("navi:${info.name}", info.name.orEmpty(), info.latitude, info.longitude, info.address.orEmpty())

    private companion object {
        /** 에뮬레이터 로그로 확인한 뒤 고친다. 초라고 가정했다. */
        val REMAIN_TIME_UNIT = DurationUnit.SECONDS
        val TRIP_TIMEOUT = 5.seconds

        /** 약 150m. */
        const val NEAR_DEGREES = 0.0015
    }
}
