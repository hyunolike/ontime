package io.github.hyunolike.ontime.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import io.github.hyunolike.ontime.app.ui.DemoControls
import io.github.hyunolike.ontime.app.ui.DemoPanel
import io.github.hyunolike.ontime.app.ui.DrivingScreen
import io.github.hyunolike.ontime.app.ui.LocalDemoControls
import io.github.hyunolike.ontime.design.theme.Space
import kotlin.time.Duration.Companion.minutes
import io.github.hyunolike.ontime.app.ui.StoppedScreen
import io.github.hyunolike.ontime.app.ui.WaitingScreen
import io.github.hyunolike.ontime.design.theme.OnTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val runtime = (application as OnTimeApp).runtime
        setContent {
            val state by runtime.state.collectAsStateWithLifecycle()
            val started by runtime.started.collectAsStateWithLifecycle()
            val previews by runtime.previews.collectAsStateWithLifecycle()
            val choosing by runtime.choosing.collectAsStateWithLifecycle()
            val demo = runtime.demo
            val demoState = demo?.state?.collectAsStateWithLifecycle()?.value
            var demoOpen by rememberSaveable { mutableStateOf(false) }
            val current = state
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 주간과 야간은 차량 시스템 설정을 따른다. 내비와 나란히 떠도 한 화면처럼 보이게 한다.
                OnTimeTheme(driving = current?.driving == true) {
                  CompositionLocalProvider(
                      LocalDemoControls provides demo?.let {
                          DemoControls(note = if (demoState?.virtualDriving == true) "가상 주행" else null, open = { demoOpen = true })
                      },
                  ) {
                    when {
                        // 내비에 등록된 주차장으로 가는 경로가 아직 없다.
                        current == null -> WaitingScreen(runtime.plan, runtime.departureLabel, runtime::startGuidance)
                        // 주행 중에는 숫자 하나만, 내역과 버튼은 정차 중에만 보여 준다.
                        current.driving -> DrivingScreen(current, runtime.plan, runtime.departureLabel) { runtime.answerProposal(true) }
                        else -> StoppedScreen(
                            state = current,
                            plan = runtime.plan,
                            departureLabel = runtime.departureLabel,
                            arrived = started && runtime.canPlay,
                            previews = previews,
                            choosing = choosing,
                            onChoose = runtime::chooseParking,
                            onStartDemo = if (runtime.canPlay && !started) runtime::startDemo else null,
                        )
                    }
                    if (demo != null && demoState != null && demoOpen) {
                        DemoPanel(
                            state = demoState,
                            onAddDelay = { demo.addDelay(18.minutes) },
                            onClearDelay = demo::clearDelay,
                            onToggleDriving = { demo.setVirtualDriving(!demoState.virtualDriving) },
                            onClose = { demoOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(Space.xl),
                        )
                    }
                  }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 화면이 보이는 동안에 시작해야 시스템이 허용한다.
        if ((application as OnTimeApp).runtime.keepAlive) KeepAliveService.start(this)
    }
}
