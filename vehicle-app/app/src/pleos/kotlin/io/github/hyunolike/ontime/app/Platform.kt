package io.github.hyunolike.ontime.app

import android.content.Context
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.SystemTimeSource
import io.github.hyunolike.ontime.pleos.PleosNavi
import io.github.hyunolike.ontime.pleos.PleosSpeech
import io.github.hyunolike.ontime.pleos.PleosVehicle
import io.github.hyunolike.ontime.session.ParkingChoice
import io.github.hyunolike.ontime.session.TripPlan
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Pleos Connect SDK에 연결하는 구성.
 *
 * 일정 등록 화면이 생기기 전까지는 시험용 일정을 쓴다. 마감은 앱을 켠 시각에서 90분 뒤다.
 * 주차와 도보 시간은 설명용 가정이고 좌표는 대략값이다.
 */
object Platform {
    private val LONG_TERM = Destination("icn-t1-long", "장기주차장", 37.4452, 126.4490, "인천 중구 공항로 272")
    private val SHORT_TERM = Destination("icn-t1-short", "단기주차장", 37.4478, 126.4525, "인천 중구 공항로 272")

    fun create(context: Context): PlatformBundle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val navi = PleosNavi(context, listOf(LONG_TERM, SHORT_TERM), scope).also { it.start() }
        val vehicle = PleosVehicle(context, scope).also { it.start() }
        val speech = PleosSpeech(context).also { it.start() }
        return PlatformBundle(
            plan = TripPlan(
                title = "시험용 일정 · 인천공항 제1터미널",
                deadline = Instant.now().plus(90, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES),
                parkingChoices = listOf(
                    ParkingChoice(LONG_TERM, parking = 6.minutes, walk = 8.minutes),
                    ParkingChoice(SHORT_TERM, parking = 6.minutes, walk = 5.minutes),
                ),
            ),
            departureLabel = "SDK 연동 시험",
            time = SystemTimeSource(),
            navi = navi,
            vehicle = vehicle,
            speech = speech,
            play = null,
        )
    }
}
