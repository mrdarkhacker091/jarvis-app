package ai.jarvis.nexus

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceIo(context: Context) {
    private val appCtx = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appCtx) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    suspend fun speak(text: String) {
        // Try Gemini failover
        val geminiResponse = JarvisApi.callGemini(appCtx, text)
        if (!geminiResponse.isNullOrBlank()) {
            // In a real app, you'd feed this audio/text to a player
            // For now, TTS fallback if Gemini returns text or fails
            withContext(Dispatchers.Main) { tts.speak(geminiResponse, TextToSpeech.QUEUE_FLUSH, null, null) }
        } else {
            // Fallback to local TTS
            withContext(Dispatchers.Main) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
        }
    }
}
