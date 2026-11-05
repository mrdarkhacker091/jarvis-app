package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.assistant.data.Prefs
import java.util.Locale

/**
 * Spoken output with a deep, calm masculine default voice (low pitch). The
 * engine speaks one utterance at a time (QUEUE_FLUSH), cancels cleanly, and
 * reports start/done so the foreground service can avoid re-listening to its
 * own speech (the TTS feedback loop).
 */
class TtsManager(ctx: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null
    private var pitch: Float = 0.62f
    private var rate: Float = 0.95f
    private var voiceName: String = ""
    var onStart: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    init {
        val app = ctx.applicationContext
        try { Prefs.init(app) } catch (_: Exception) { }
        val prefs = runCatching { Prefs.get(app) }.getOrNull()
        pitch = prefs?.ttsPitch ?: 0.62f
        rate = prefs?.ttsRate ?: 0.95f
        voiceName = prefs?.ttsVoice ?: ""
        engine = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine?.language = Locale.getDefault()
                applyVoice()
                engine?.setPitch(pitch)
                engine?.setSpeechRate(rate)
                queued?.let { speak(it) }; queued = null
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart?.invoke() }
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
    }

    fun configure(p: Float, r: Float, voice: String) {
        pitch = p; rate = r; voiceName = voice
        engine?.setPitch(p); engine?.setSpeechRate(r); applyVoice()
    }

    private fun applyVoice() {
        if (voiceName.isBlank()) {
            // Prefer a male English voice when the engine exposes one.
            val voices: Set<Voice>? = runCatching { engine?.voices }.getOrNull()
            val male = voices?.firstOrNull {
                !it.isNetworkConnectionRequired && (
                    it.name.contains("male", ignoreCase = true) ||
                        it.name.contains("en-us-x", ignoreCase = true) ||
                        it.name.contains("daniel", ignoreCase = true) ||
                        it.name.contains("en-gb", ignoreCase = true))
            }
            if (male != null) engine?.setVoice(male)
        } else {
            val match = runCatching { engine?.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
            if (match != null) engine?.setVoice(match)
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) { queued = clean; return }
        engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis-" + System.nanoTime())
    }

    fun isSpeaking(): Boolean = runCatching { engine?.isSpeaking }.getOrNull() == true

    fun stop() { engine?.stop() }

    fun shutdown() {
        try { engine?.stop(); engine?.shutdown() } catch (_: Exception) { }
        engine = null; ready = false
    }
}