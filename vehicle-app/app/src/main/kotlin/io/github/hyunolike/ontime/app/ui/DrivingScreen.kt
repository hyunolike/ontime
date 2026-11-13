package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.hyunolike.ontime.session.SessionState
import io.github.hyunolike.ontime.session.Suggestion
import io.github.hyunolike.ontime.session.SuggestionStatus
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.slack.Source

/** 주행 중 화면. 큰 숫자 하나가 중심이고 만질 수 있는 요소는 없다. */
@Composable
fun DrivingScreen(state: SessionState, plan: TripPlan, departureLabel: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$departureLabel · 수속 마감 ${plan.deadline.clock()}",
                color = OnTimeColors.Muted,
                fontSize = 22.sp,
            )
            Spacer(Modifier.weight(1f))
            if (state.source == Source.REPLAY) {
                ReplayBadge()
                Spacer(Modifier.width(16.dp))
            }
            Text("D 주행 중", color = OnTimeColors.Muted, fontSize = 22.sp)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 화면 비율이 달라도 숫자가 넘치지 않게 가로와 세로 중 작은 쪽에 맞춘다.
            val numberSize = with(LocalDensity.current) { min(maxWidth / 3.4f, maxHeight / 1.9f).toSp() }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("여유", color = OnTimeColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Text(state.level.label(), color = state.level.color(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (state.stale) {
                        Spacer(Modifier.width(12.dp))
                        Text("${state.now.clock()} 기준", color = OnTimeColors.Muted, fontSize = 22.sp)
                    }
                }
                Text(
                    slackText(state.slack.displayMinutes),
                    color = state.level.color(),
                    fontSize = numberSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                )
                Text("목적지 ${state.destinationName}", color = OnTimeColors.Muted, fontSize = 24.sp)
            }
        }

        state.suggestion?.let { SuggestionStrip(it) }
    }
}

@Composable
fun ReplayBadge() {
    Text(
        "시연 재생",
        color = OnTimeColors.Background,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OnTimeColors.Muted)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 제안은 말로 주고받는다. 화면에는 지금 무슨 말이 오갔는지만 한 줄로 남긴다. */
@Composable
private fun SuggestionStrip(suggestion: Suggestion) {
    val (text, hint) = when (suggestion.status) {
        SuggestionStatus.PROPOSED -> suggestion.text to "\"그렇게 해 줘\"라고 말하면 바꿉니다"
        SuggestionStatus.ACCEPTED -> (suggestion.result ?: suggestion.text) to null
        SuggestionStatus.DECLINED -> "경로를 그대로 유지합니다" to null
        SuggestionStatus.FAILED -> (suggestion.result ?: "경로를 바꾸지 못했습니다") to null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OnTimeColors.Surface)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, color = OnTimeColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            hint?.let { Text(it, color = OnTimeColors.Muted, fontSize = 20.sp) }
        }
    }
}
