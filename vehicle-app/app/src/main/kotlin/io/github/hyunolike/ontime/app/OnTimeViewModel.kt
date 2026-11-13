package io.github.hyunolike.ontime.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hyunolike.ontime.session.OnTimeSession
import io.github.hyunolike.ontime.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val platform = Platform.create(application)
    private val session = OnTimeSession(
        platform.plan,
        platform.time,
        platform.navi,
        platform.vehicle,
        platform.speech,
    )

    val plan = platform.plan
    val departureLabel = platform.departureLabel
    val canPlay = platform.play != null
    val state: StateFlow<SessionState?> = session.state

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started

    init {
        viewModelScope.launch { session.run() }
    }

    fun startDemo() {
        val play = platform.play ?: return
        if (_started.compareAndSet(expect = false, update = true)) {
            viewModelScope.launch { play() }
        }
    }
}
