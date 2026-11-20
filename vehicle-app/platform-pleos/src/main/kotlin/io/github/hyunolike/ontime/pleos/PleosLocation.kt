package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import io.github.hyunolike.ontime.platform.Fix
import io.github.hyunolike.ontime.platform.LocationGateway
import io.github.hyunolike.ontime.platform.MotionEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * FusedLocation으로 위치와 진행 방향을 받는다.
 * 도슨트는 진행 방향(heading)과 장소의 좌표로 왼쪽 창밖인지 오른쪽 창밖인지를 가린다.
 */
class PleosLocation(context: Context) : LocationGateway {
    private val fused = FusedLocation(context)
    private val estimator = MotionEstimator()
    private val _fixes = MutableSharedFlow<Fix>(replay = 1, extraBufferCapacity = 16)

    /**
     * FusedLocation 위치. 에뮬레이터의 heading은 0이고 속도도 오지 않아, 진행 방향과 속도는 연속한 위치로 계산한다.
     * 에뮬레이터에서는 `adb emu geo fix`로 넣은 좌표가 들어온다(tools/geo-drive.py).
     */
    override val fixes: Flow<Fix> = _fixes

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            data?.let { _fixes.tryEmit(estimator.next(it.latitude, it.longitude, System.currentTimeMillis())) }
            PleosLog.event(
                "fused.onChanged",
                data?.let { "lat=${it.latitude} lon=${it.longitude} heading=${it.heading} confidence=${it.confidence}" } ?: "(없음)",
            )
        }

        override fun onError(e: Exception) {
            PleosLog.error("fused.onError", e)
        }
    }

    fun start() {
        runCatching {
            val supported = fused.isSupported()
            PleosLog.event("fused.isSupported", supported.toString())
            if (!supported) return
            fused.initialize()
            fused.registerFusedLocationCallback(listener)
        }.onFailure { PleosLog.error("fused.start", it) }
    }

    fun logLastKnown() {
        PleosLog.call("fused.getLastKnownLocation")
        runCatching { fused.getLastKnownLocation(listener) }.onFailure { PleosLog.error("fused.getLastKnownLocation", it) }
    }
}
