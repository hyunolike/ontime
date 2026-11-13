package io.github.hyunolike.ontime.pleos

import android.util.Log

/**
 * SDK와 주고받은 내용을 한 태그로 남긴다.
 * `adb logcat -s OnTimePleos`로 모아 보고, 확인된 값은 docs/pleos-sdk-notes.md에 옮긴다.
 */
internal object PleosLog {
    private const val TAG = "OnTimePleos"

    fun call(api: String, detail: String = "") = Log.i(TAG, "-> $api $detail")

    fun event(api: String, detail: String = "") = Log.i(TAG, "<- $api $detail")

    fun error(api: String, cause: Any?) = Log.w(TAG, "!! $api $cause")
}
