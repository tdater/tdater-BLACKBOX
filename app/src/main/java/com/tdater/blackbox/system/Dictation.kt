package com.tdater.blackbox.system

import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech to text that only uses the phone's on-device recognizer (Android 12+). If the phone has no offline speech
 * model, dictation is reported unavailable rather than quietly falling back to a cloud recognizer.
 */
class Dictation(private val context: Context, private val onPartial: (String) -> Unit, private val onDone: (String?, String?) -> Unit) {
    private var recognizer: SpeechRecognizer? = null

    val listening get() = recognizer != null

    @SuppressLint("NewApi") // available() checks API 31+.
    fun start(): Boolean {
        if (!available(context)) return false
        finish()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                finish(); onDone(first(results), null)
            }
            override fun onError(error: Int) {
                finish()
                onDone(null, when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch anything. Try again a bit closer."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for dictation."
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                        "No offline speech model for this language. Download one in Android Settings → Speech / On-device recognition."
                    else -> "Dictation stopped (error $error)."
                })
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        return true
    }

    /** Stops listening; whatever was heard so far still arrives through onDone. */
    fun stop() { recognizer?.stopListening() }

    fun cancel() { finish() }

    private fun finish() { recognizer?.destroy(); recognizer = null }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        fun available(context: Context) =
            Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
