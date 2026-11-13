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
) : SpeechGateway {
    private val _spoken = MutableStateFlow<List<String>>(emptyList())
    val spoken: StateFlow<List<String>> = _spoken

    override suspend fun say(text: String) {
        _spoken.update { it + text }
    }

    override suspend fun askYesNo(prompt: String): Boolean {
        say(prompt)
        delay(answerDelay)
        return answer
    }
}
