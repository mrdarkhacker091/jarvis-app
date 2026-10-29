package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognizerManager(private val ctx: Context) {
    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var listening = false

    fun isAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(ctx)
        } catch (e: Exception) {
            false
        }
    }

    fun start(l: Listener) {
        this.listener = l
        main.post {
            if (recognizer == null) {
                try {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                    recognizer?.setRecognitionListener(callback)
                } catch (e: Exception) {
                    listening = false
                    l.onError(SpeechRecognizer.ERROR_CLIENT)
                    return@post
                }
            }
            if (!listening) {
                listening = true
                try {
                    recognizer?.startListening(intent())
                } catch (e: Exception) {
                    listening = false
                    l.onError(SpeechRecognizer.ERROR_CLIENT)
                }
            }
        }
    }

    fun stop() {
        main.post {
            listening = false
            try {
                recognizer?.stopListening()
            } catch (e: Exception) {
                // Stop failed, usually because nothing was listening.
                android.util.Log.d("JarvisSpeech", "stop: " + e.message)
            }
        }
    }

    fun cancel() {
        main.post {
            listening = false
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                // Cancel failed, usually because nothing was listening.
                android.util.Log.d("JarvisSpeech", "cancel: " + e.message)
            }
        }
    }

    fun destroy() {
        main.post {
            listening = false
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                // Destroy failed, usually because the engine was already gone.
                android.util.Log.d("JarvisSpeech", "destroy: " + e.message)
            }
            recognizer = null
        }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
    }

    private val callback = object : RecognitionListener {
        override fun onReadyForSpeech(p: android.os.Bundle?) {
            try {
                listener?.onReady()
            } catch (e: Exception) {
                android.util.Log.d("JarvisSpeech", "onReady: " + e.message)
            }
        }

        override fun onBeginningOfSpeech() {
            // No-op: command capture waits for the final result.
        }

        override fun onRmsChanged(rmsdB: Float) {
            // No-op: level metering is not surfaced in the UI.
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // No-op: raw audio is never stored.
        }

        override fun onEndOfSpeech() {
            // No-op: onResults/onError carries the outcome.
        }

        override fun onError(error: Int) {
            listening = false
            try {
                listener?.onError(error)
            } catch (e: Exception) {
                android.util.Log.d("JarvisSpeech", "onError callback: " + e.message)
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val text = try {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
            } catch (e: Exception) {
                null
            }
            try {
                if (text.isNullOrEmpty()) {
                    listener?.onError(SpeechRecognizer.ERROR_NO_MATCH)
                } else {
                    listener?.onFinal(text)
                }
            } catch (e: Exception) {
                android.util.Log.d("JarvisSpeech", "onResults callback: " + e.message)
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = try {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            } catch (e: Exception) {
                null
            }
            if (text?.isNotEmpty() == true) {
                try {
                    listener?.onPartial(text)
                } catch (e: Exception) {
                    android.util.Log.d("JarvisSpeech", "onPartial callback: " + e.message)
                }
            }
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
            // No-op: vendor events are ignored.
        }
    }
}
