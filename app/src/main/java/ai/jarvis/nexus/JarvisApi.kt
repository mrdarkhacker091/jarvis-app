package ai.jarvis.nexus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.content.Context

object JarvisApi {

    private const val BASE = "https://jarvis-wandering-nexus-core.base44.app"
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 5 Chat models
    private val chatModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.0-pro")
    // 2 Voice models
    private val voiceModels = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite")

    private var currentChatApiIdx = 0
    private var currentChatModelIdx = 0
    private var currentVoiceApiIdx = 0
    private var currentVoiceModelIdx = 0

    suspend fun callGeminiChat(context: Context, text: String): String? = withContext(Dispatchers.IO) {
        val keys = listOf(Prefs.geminiKey1(context), Prefs.geminiKey2(context), Prefs.geminiKey3(context))
        
        repeat(15) { // 3 keys * 5 models
            val key = keys[currentChatApiIdx]
            val model = chatModels[currentChatModelIdx]
            
            if (!key.isNullOrBlank()) {
                try {
                    val payload = JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
                    
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(json))
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = JSONObject(response.body?.string() ?: "")
                            return@withContext body.getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        }
                    }
                } catch (_: Exception) {}
            }
            // Rotate
            currentChatModelIdx = (currentChatModelIdx + 1) % 5
            if (currentChatModelIdx == 0) currentChatApiIdx = (currentChatApiIdx + 1) % 3
        }
        null
    }

    suspend fun callGeminiVoice(context: Context, text: String): String? = withContext(Dispatchers.IO) {
        val keys = listOf(Prefs.geminiKey1(context), Prefs.geminiKey2(context), Prefs.geminiKey3(context))
        
        repeat(6) { // 3 keys * 2 models
            val key = keys[currentVoiceApiIdx]
            val model = voiceModels[currentVoiceModelIdx]
            
            if (!key.isNullOrBlank()) {
                try {
                    val payload = JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
                    
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(json))
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = JSONObject(response.body?.string() ?: "")
                            // Assume text response, real audio needs audio/pcm handling
                            return@withContext body.getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        }
                    }
                } catch (_: Exception) {}
            }
            currentVoiceModelIdx = (currentVoiceModelIdx + 1) % 2
            if (currentVoiceModelIdx == 0) currentVoiceApiIdx = (currentVoiceApiIdx + 1) % 3
        }
        null
    }
}
