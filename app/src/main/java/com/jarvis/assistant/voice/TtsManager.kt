package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(ctx: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null
    var onStart: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    init {
        engine = try {
            TextToSpeech(ctx.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    try {
                        engine?.language = Locale.getDefault()
                    } catch (e: Exception) {
                        // Keep the default engine voice when the locale is unsupported.
                        android.util.Log.d("JarvisTts", "locale: " + e.message)
                    }
                    val pending = queued
                    queued = null
                    if (pending != null) {
                        speak(pending)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("JarvisTts", "init: " + e.message)
            null
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                try {
                    onStart?.invoke()
                } catch (e: Exception) {
                    android.util.Log.d("JarvisTts", "onStart: " + e.message)
                }
            }

            override fun onDone(utteranceId: String?) {
                try {
                    onDone?.invoke()
                } catch (e: Exception) {
                    android.util.Log.d("JarvisTts", "onDone: " + e.message)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                try {
                    onDone?.invoke()
                } catch (e: Exception) {
                    android.util.Log.d("JarvisTts", "onError: " + e.message)
                }
            }
        })
    }

    fun setRate(rate: Float) {
        try {
            engine?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        } catch (e: Exception) {
            android.util.Log.d("JarvisTts", "setRate: " + e.message)
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return
        }
        if (!ready) {
            queued = clean
            return
        }
        try {
            engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis-" + System.nanoTime())
        } catch (e: Exception) {
            android.util.Log.d("JarvisTts", "speak: " + e.message)
        }
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (e: Exception) {
            // Stop failed, usually because the engine was already idle.
            android.util.Log.d("JarvisTts", "stop: " + e.message)
        }
    }

    fun shutdown() {
        try {
            engine?.stop()
        } catch (e: Exception) {
            // Best effort before shutdown.
            android.util.Log.d("JarvisTts", "shutdown stop: " + e.message)
        }
        try {
            engine?.shutdown()
        } catch (e: Exception) {
            // Engine already gone.
            android.util.Log.d("JarvisTts", "shutdown: " + e.message)
        }
        engine = null
        ready = false
    }
}
