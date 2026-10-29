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
        fun onReady(); fun onPartial(text: String); fun onFinal(text: String); fun onError(code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(l: Listener) {
        this.listener = l
        main.post {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                recognizer?.setRecognitionListener(callback)
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
                // already stopped
            }
        }
    }

    fun cancel() {
        main.post {
            listening = false
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                // already cancelled
            }
        }
    }

    fun destroy() {
        main.post {
            listening = false
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                // already destroyed
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
        override fun onReadyForSpeech(p: android.os.Bundle?) { listener?.onReady() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { listening = false; listener?.onError(error) }
        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) listener?.onError(SpeechRecognizer.ERROR_NO_MATCH) else listener?.onFinal(text)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text?.isNotEmpty() == true) listener?.onPartial(text)
        }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }
}
