package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyunolike.ontime.session.SessionState
import io.github.hyunolike.ontime.session.TripPlan
import io.github.hyunolike.ontime.slack.Source
import kotlin.time.Duration

/** 정차 중 화면. 여유가 어떻게 나왔는지 항목별로 보여 준다. */
@Composable
fun StoppedScreen(
    state: SessionState,
    plan: TripPlan,
    departureLabel: String,
    arrived: Boolean,
    onStartDemo: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(plan.title, color = OnTimeColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$departureLabel · 수속 마감 ${plan.deadline.clock()}",
                    color = OnTimeColors.Muted,
                    fontSize = 22.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.source == Source.REPLAY) ReplayBadge()
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                slackText(state.slack.displayMinutes),
                color = state.level.color(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (arrived) "  ${state.destinationName} 도착" else "  ${state.now.clock()} 기준 여유",
                color = OnTimeColors.Muted,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 22.dp),
            )
        }

        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OnTimeColors.Surface)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BreakdownRow("마감까지 남은 시간", state.untilDeadline)
            BreakdownRow("남은 주행 (${state.destinationName})", -state.drive)
            BreakdownRow("주차", -state.parking)
            BreakdownRow("도보", -state.walk)
            BreakdownRow("안전 버퍼", -state.buffer)
        }

        if (state.source == Source.REPLAY) {
            Text(
                "주행 시간과 시각은 시연용으로 재생한 값이고, 주차와 도보 시간은 설명용 가정입니다.",
                color = OnTimeColors.Muted,
                fontSize = 18.sp,
            )
        }

        onStartDemo?.let {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = it,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnTimeColors.Comfort,
                    contentColor = OnTimeColors.Background,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "시연 시작",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Duration) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = OnTimeColors.Text, fontSize = 24.sp)
        Spacer(Modifier.weight(1f))
        val minutes = amount.inWholeMinutes
        Text(
            if (minutes < 0) "− ${-minutes}분" else "${minutes}분",
            color = OnTimeColors.Muted,
            fontSize = 24.sp,
        )
    }
}
