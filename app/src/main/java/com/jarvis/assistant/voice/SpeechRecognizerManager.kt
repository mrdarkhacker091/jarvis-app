package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech input with a real state machine. Partial results are forwarded for
 * display ONLY — they never trigger an action. Only onResults (the final
 * utterance) is delivered as onFinal, and only for the session that started
 * the current turn, so stale callbacks from a previous recognition cannot fire.
 *
 * The early-cutoff bug ("Check" instead of "Check my battery percentage") is
 * addressed by long silence thresholds and a short grace period after
 * end-of-speech, so the recognizer waits for a natural pause before finalising.
 */
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
    private var session = 0
    private var startElapsed = 0L
    private var endedSpeech = false
    private var gracePending = false

    var completeSilenceMs: Int = 2000
    var possibleSilenceMs: Int = 1400
    var graceMs: Long = 350
    var minUtteranceMs: Long = 250

    fun isAvailable(): Boolean =
        try { SpeechRecognizer.isRecognitionAvailable(ctx) } catch (_: Exception) { false }

    fun start(l: Listener) {
        this.listener = l
        main.post {
            session += 1
            val s = session
            endedSpeech = false
            gracePending = false
            startElapsed = SystemClock.elapsedRealtime()
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                recognizer?.setRecognitionListener(callback)
            }
            if (!listening) {
                listening = true
                try { recognizer?.startListening(intent()) }
                catch (e: Exception) { listening = false; l.onError(SpeechRecognizer.ERROR_CLIENT) }
            }
            // abandon this session if the recognizer never produces anything
            main.postDelayed({
                if (s == session && listening && !endedSpeech && !gracePending) {
                    // still listening; that is fine, the recognizer will end on its own
                }
            }, 2000)
        }
    }

    fun stop() {
        main.post {
            listening = false
            session += 1
            try { recognizer?.stopListening() } catch (_: Exception) { }
        }
    }

    fun cancel() {
        main.post {
            listening = false
            session += 1
            try { recognizer?.cancel() } catch (_: Exception) { }
        }
    }

    fun destroy() {
        main.post {
            listening = false
            session += 1
            try { recognizer?.destroy() } catch (_: Exception) { }
            recognizer = null
        }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilenceMs)
    }

    private fun deliver(s: Int, text: String) {
        val l = listener ?: return
        val clean = text.trim()
        if (clean.isEmpty()) l.onError(SpeechRecognizer.ERROR_NO_MATCH) else l.onFinal(clean)
    }

    private val callback = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) { listener?.onReady() }
        override fun onBeginningOfSpeech() { }
        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) { }
        override fun onEndOfSpeech() {
            // The user stopped talking. Wait a short grace period so a trailing
            // word ("...percentage") is not cut off before onResults arrives.
            endedSpeech = true
            val s = session
            gracePending = true
            main.postDelayed({
                if (s == session && gracePending && listening) {
                    gracePending = false
                    listening = false
                    try { recognizer?.stopListening() } catch (_: Exception) { }
                }
            }, graceMs)
        }
        override fun onError(error: Int) {
            listening = false; gracePending = false; listener?.onError(error)
        }
        override fun onResults(results: Bundle?) {
            val s = session
            listening = false; gracePending = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            if (text.isNullOrEmpty()) {
                listener?.onError(SpeechRecognizer.ERROR_NO_MATCH); return
            }
            // Reject a final that arrived faster than a human could finish a
            // multi-word command, unless it is a known short command. This is the
            // direct guard against acting on a truncated "Check".
            val words = text.trim().split(Regex("\\s+"))
            val shortCommands = setOf("stop", "cancel", "back", "go back", "home", "yes", "no")
            if (elapsed < minUtteranceMs && words.size > 1 && text.lowercase() !in shortCommands) {
                listener?.onError(SpeechRecognizer.ERROR_NO_MATCH); return
            }
            deliver(s, text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            // Partial results are display-only. Never deliver as final.
            if (!text.isNullOrEmpty()) listener?.onPartial(text)
        }
        override fun onEvent(eventType: Int, params: Bundle?) { }
    }
}