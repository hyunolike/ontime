package io.github.hyunolike.ontime.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hyunolike.ontime.app.ui.DrivingScreen
import io.github.hyunolike.ontime.app.ui.OnTimeColors
import io.github.hyunolike.ontime.app.ui.StoppedScreen

class MainActivity : ComponentActivity() {
    private val viewModel: OnTimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val started by viewModel.started.collectAsStateWithLifecycle()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OnTimeColors.Background)
                    .safeDrawingPadding(),
            ) {
                val current = state ?: return@Box
                // 주행 중에는 숫자 하나만, 내역과 버튼은 정차 중에만 보여 준다.
                if (current.driving) {
                    DrivingScreen(current, viewModel.plan, viewModel.departureLabel)
                } else {
                    StoppedScreen(
                        state = current,
                        plan = viewModel.plan,
                        departureLabel = viewModel.departureLabel,
                        arrived = started,
                        onStartDemo = if (viewModel.canPlay && !started) viewModel::startDemo else null,
                    )
                }
            }
        }
    }
}
