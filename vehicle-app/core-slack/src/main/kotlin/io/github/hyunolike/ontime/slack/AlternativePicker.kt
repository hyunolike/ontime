package io.github.hyunolike.ontime.slack

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class ParkingOption(
    val id: String,
    val name: String,
    /** 주차장 진입부터 주차 완료까지. */
    val parking: Duration,
    /** 주차 후 목표 지점(체크인 카운터)까지. */
    val walk: Duration,
)

/** 바꿀 수 있는 주차장과 거기까지 남은 주행 시간. */
data class Candidate(val option: ParkingOption, val drive: Duration)

data class Alternative(val option: ParkingOption, val gain: Duration, val newSlack: SlackResult)

object AlternativePicker {
    val DEFAULT_MIN_GAIN: Duration = 3.minutes

    /** 여유를 가장 많이 늘리는 후보 하나를 고른다. 이득이 [minGain]보다 작으면 제안하지 않는다. */
    fun pick(
        current: SlackInput,
        candidates: List<Candidate>,
        minGain: Duration = DEFAULT_MIN_GAIN,
    ): Alternative? {
        val currentSlack = SlackCalculator.calculate(current).exact
        return candidates
            .map { candidate ->
                val slack = SlackCalculator.calculate(
                    current.copy(
                        drive = candidate.drive,
                        parking = candidate.option.parking,
                        walk = candidate.option.walk,
                    ),
                )
                Alternative(candidate.option, slack.exact - currentSlack, slack)
            }
            .filter { it.gain >= minGain }
            .maxByOrNull { it.gain }
    }
}
