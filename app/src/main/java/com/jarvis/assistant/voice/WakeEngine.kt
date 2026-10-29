package com.jarvis.assistant.voice

/**
 * Wake-word engine contract. Android has no guaranteed always-on
 * third-party wake-word API, so the current implementation stays on
 * SpeechRecognizer behind this interface: it listens for the phrase in
 * partial/final results, sleeps between attempts, and backs off on
 * errors. A future on-device keyword model (e.g. Porcupine / Vosk)
 * can implement this interface without touching callers.
 *
 * Documented honestly in README: background wake-word reliability is
 * limited by Android (microphone background rules, Doze, OEM kills,
 * SpeechRecognizer timeouts and network dependence).
 */
interface WakeEngine {
    fun startListening(listener: Listener)
    fun stopListening()
    fun destroy()

    fun setWakePhrase(phrase: String)

    interface Listener {
        fun onWakeHeard(utterance: String, trailingCommand: String)
        fun onError(code: Int)
    }
}
