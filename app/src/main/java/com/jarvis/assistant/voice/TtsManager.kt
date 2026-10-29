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
        engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true; engine?.language = Locale.getDefault()
                queued?.let { speak(it) }; queued = null
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart?.invoke() }
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
    }
    fun setRate(rate: Float) { engine?.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            queued = clean
            return
        }
        engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis-" + System.nanoTime())
    }
    fun stop() { engine?.stop() }
    fun shutdown() { try { engine?.stop(); engine?.shutdown() } catch (e: Exception) {} engine = null; ready = false }
}
