package io.github.hyunolike.ontime.slack

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 순서가 곧 심각도다. 뒤로 갈수록 나쁘다. */
enum class SlackLevel { COMFORT, CAUTION, DANGER }

data class SlackThresholds(
    val comfort: Duration = 15.minutes,
    val caution: Duration = 5.minutes,
    /** 좋아지는 방향으로 넘어갈 때 기준보다 더 필요한 여유. */
    val hysteresis: Duration = 2.minutes,
) {
    fun levelFor(slack: Duration, margin: Duration = Duration.ZERO): SlackLevel = when {
        slack >= comfort + margin -> SlackLevel.COMFORT
        slack >= caution + margin -> SlackLevel.CAUTION
        else -> SlackLevel.DANGER
    }
}

data class LevelChange(val from: SlackLevel?, val to: SlackLevel)

/**
 * 상태 전환을 추적한다. 나빠질 때는 바로 바꾸고,
 * 좋아질 때는 완충을 둬서 기준 근처에서 안내가 반복되지 않게 한다.
 */
class SlackLevelTracker(private val thresholds: SlackThresholds = SlackThresholds()) {
    var level: SlackLevel? = null
        private set

    /** 상태가 바뀌었을 때만 변화를 돌려준다. */
    fun update(slack: Duration): LevelChange? {
        val previous = level
        val raw = thresholds.levelFor(slack)
        val next = when {
            previous == null || raw >= previous -> raw
            // 좋아지는 방향: 완충을 넣어 다시 판정하되 지금보다 나빠지지는 않는다.
            else -> minOf(previous, thresholds.levelFor(slack, thresholds.hysteresis))
        }
        if (next == previous) return null
        level = next
        return LevelChange(previous, next)
    }
}
