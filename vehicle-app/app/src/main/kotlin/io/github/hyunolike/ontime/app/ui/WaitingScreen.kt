package io.github.hyunolike.ontime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyunolike.ontime.session.TripPlan

/** 일정은 있는데 내비 경로가 아직 없을 때의 화면. 주차장으로 안내를 시작할 수 있다. */
@Composable
fun WaitingScreen(plan: TripPlan, departureLabel: String, onStartGuidance: () -> Unit) {
    val first = plan.parkingChoices.firstOrNull()?.destination
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(plan.title, color = OnTimeColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "$departureLabel · 수속 마감 ${plan.deadline.clock()}",
            color = OnTimeColors.Muted,
            fontSize = 22.sp,
        )
        Text(
            "내비에 이 일정으로 가는 경로가 없습니다. 안내를 시작하면 여유 시간을 계산합니다.",
            color = OnTimeColors.Text,
            fontSize = 24.sp,
        )
        if (first != null) {
            Button(
                onClick = onStartGuidance,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnTimeColors.Comfort,
                    contentColor = OnTimeColors.Background,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "${first.name} 안내 시작",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}
