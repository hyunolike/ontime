package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context

/**
 * FusedLocation으로 위치와 진행 방향을 받는다.
 * 도슨트는 진행 방향(heading)과 장소의 좌표로 왼쪽 창밖인지 오른쪽 창밖인지를 가린다.
 */
class PleosLocation(context: Context) {
    private val fused = FusedLocation(context)

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
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
