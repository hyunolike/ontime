package io.github.hyunolike.ontime.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import io.github.hyunolike.ontime.platform.SpeechGateway
import io.github.hyunolike.ontime.voice.YesNoParser
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * TextToSpeech로 말하고 SpeechToText로 답을 한 번 듣는다.
 *
 * 앱 등록이 필요 없는 ON_DEVICE 모드로 시작한다. 한국어 인식이 안 되면
 * Playground My Project의 Client ID로 등록하는 서버 모드로 바꾼다.
 * Gleo Interface는 공모전 참가팀이 쓸 수 없어 답은 앱이 직접 해석한다.
 */
class PleosSpeech(context: Context) : SpeechGateway {
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private val mutex = Mutex()
    private var speaking: CompletableDeferred<Unit>? = null
    private var hearing: CompletableDeferred<String?>? = null

    private val ttsListener = object : EventListener {
        override fun onStart() { PleosLog.event("tts.onStart") }
        override fun onReady() { PleosLog.event("tts.onReady") }
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            PleosLog.event("tts.onDone")
            speaking?.complete(Unit)
        }

        override fun onStop() {
            PleosLog.event("tts.onStop")
            speaking?.complete(Unit)
        }

        override fun onError(errorType: TtsErrorType) {
            PleosLog.error("tts.onError", errorType)
            speaking?.complete(Unit)
        }
    }

    private val sttListener = object : ResultListener {
        override fun onReady() { PleosLog.event("stt.onReady") }
        override fun onStartedRecognition() { PleosLog.event("stt.onStartedRecognition") }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            PleosLog.event("stt.onUpdated", "completed=$completed text=$stt")
            if (completed) hearing?.complete(stt)
        }

        override fun onEndedRecognition() {
            PleosLog.event("stt.onEndedRecognition")
            hearing?.complete(null)
        }

        override fun onError(errorType: SttErrorType) {
            PleosLog.error("stt.onError", errorType)
            hearing?.complete(null)
        }
    }

    fun start() {
        PleosLog.call("TextToSpeech.initialize")
        tts.initialize()
        tts.setLocale(Locale.KOREAN)
        tts.addEventListener(ttsListener)
        PleosLog.call("SpeechToText.initialize")
        stt.initialize()
        stt.setLocale(Locale.KOREAN)
        stt.addListener(sttListener)
    }

    fun stop() {
        tts.removeEventListener(ttsListener)
        tts.release()
        stt.removeListener(sttListener)
        stt.release()
    }

    override suspend fun say(text: String) = mutex.withLock { speak(text) }

    override suspend fun askYesNo(prompt: String): Boolean = mutex.withLock {
        speak(prompt)
        val heard = CompletableDeferred<String?>()
        hearing = heard
        PleosLog.call("stt.request")
        stt.request()
        val text = withTimeoutOrNull(LISTEN_TIMEOUT) { heard.await() }
        hearing = null
        runCatching { stt.stop() }
        // 못 알아들었거나 답이 없으면 경로를 유지한다.
        YesNoParser.parse(text.orEmpty()) == true
    }

    private suspend fun speak(text: String) {
        val done = CompletableDeferred<Unit>()
        speaking = done
        PleosLog.call("tts.speak", text)
        tts.speak(text, true)
        withTimeoutOrNull(SPEAK_TIMEOUT) { done.await() }
        speaking = null
    }

    private companion object {
        val SPEAK_TIMEOUT = 15.seconds
        val LISTEN_TIMEOUT = 7.seconds
    }
}
