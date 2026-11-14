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
import io.github.hyunolike.ontime.app.ui.DocentDrivingScreen
import io.github.hyunolike.ontime.app.ui.DocentStoppedScreen
import io.github.hyunolike.ontime.app.ui.DrivingScreen
import io.github.hyunolike.ontime.app.ui.StoppedScreen
import io.github.hyunolike.ontime.app.ui.WaitingScreen
import io.github.hyunolike.ontime.design.theme.OnTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val runtime = (application as OnTimeApp).runtime
        intent?.getStringExtra("poc")?.let(runtime::poc)
        setContent {
            val state by runtime.state.collectAsStateWithLifecycle()
            val started by runtime.started.collectAsStateWithLifecycle()
            val previews by runtime.previews.collectAsStateWithLifecycle()
            val choosing by runtime.choosing.collectAsStateWithLifecycle()
            val current = state
            val docent = runtime.docent
            val docentState by runtime.docentState.collectAsStateWithLifecycle()
            val docentStarted by runtime.docentStarted.collectAsStateWithLifecycle()
            val tour = docentState
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 야간 색으로 고정한다. 주행 중 눈부심이 적고 콘셉트 시안과 같은 분위기다.
                // 주간 색은 디자인 시스템에 준비돼 있고 갤러리에서 확인할 수 있다.
                OnTimeTheme(darkTheme = true, driving = if (docent != null) tour?.driving == true else current?.driving == true) {
                    when {
                        // 도슨트가 있으면 도슨트가 앱의 본 화면이다.
                        docent != null && tour != null && tour.driving ->
                            DocentDrivingScreen(tour, docent.title, docent.photos, runtime::answerDocent)
                        docent != null && tour != null -> DocentStoppedScreen(
                            state = tour,
                            title = docent.title,
                            photos = docent.photos,
                            started = docentStarted,
                            onDrive = if (docent.play != null) runtime::startDocentDemo else null,
                            onGuide = runtime::guideTo,
                        )
                        docent != null -> Unit
                        // 내비에 등록된 주차장으로 가는 경로가 아직 없다.
                        current == null -> WaitingScreen(runtime.plan, runtime.departureLabel, runtime::startGuidance)
                        // 주행 중에는 숫자 하나만, 내역과 버튼은 정차 중에만 보여 준다.
                        current.driving -> DrivingScreen(current, runtime.plan, runtime.departureLabel)
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
