package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.platform.SpeechGateway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 음성 인식 없이 정해 둔 답을 돌려준다. 말한 문장은 [spoken]에 쌓는다. */
class ScriptedSpeech(
    private val answer: Boolean = true,
    private val answerDelay: Duration = 4.seconds,
    /** 한 문장을 말하는 데 걸리는 시간. 말하는 동안의 화면을 보여 주려고 둔다. */
    private val sayDelay: Duration = Duration.ZERO,
    /** 문장마다 말하는 시간. 있으면 [sayDelay] 대신 쓴다. 실제 음성처럼 긴 문장은 오래 걸린다. */
    private val sayDuration: ((String) -> Duration)? = null,
) : SpeechGateway {
    private val _spoken = MutableStateFlow<List<String>>(emptyList())
    val spoken: StateFlow<List<String>> = _spoken

    override suspend fun say(text: String) {
        _spoken.update { it + text }
        delay(sayDuration?.invoke(text) ?: sayDelay)
    }

    override suspend fun askYesNo(prompt: String): Boolean {
        say(prompt)
        delay(answerDelay)
        return answer
    }
}
