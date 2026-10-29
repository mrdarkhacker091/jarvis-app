package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.JarvisBus
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LlmClient(private val baseUrl: String, private val apiKey: String, private val model: String) {
    data class ToolCall(val id: String, val name: String, val args: JSONObject)
    data class Reply(val text: String?, val toolCalls: List<ToolCall>)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun complete(messages: JSONArray, tools: JSONArray?): Reply {
        val body = JSONObject()
        body.put("model", model)
        body.put("messages", messages)
        body.put("temperature", 0.6)
        if (tools != null && tools.length() > 0) {
            body.put("tools", tools)
            body.put("tool_choice", "auto")
        }
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/mrdarkhacker091/jarvis-agent")
            .addHeader("X-Title", "JARVIS")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${raw.take(280)}")
            }
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices") ?: return Reply(null, emptyList())
            if (choices.length() == 0) return Reply(null, emptyList())
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: return Reply(null, emptyList())
            val text = message.optString("content", "").ifBlank { null }
            val calls = mutableListOf<ToolCall>()
            val tcs = message.optJSONArray("tool_calls")
            if (tcs != null) {
                for (i in 0 until tcs.length()) {
                    val tc = tcs.optJSONObject(i) ?: continue
                    val fn = tc.optJSONObject("function") ?: continue
                    val argsRaw = fn.optString("arguments", "{}")
                    val args = try {
                        JSONObject(argsRaw)
                    } catch (e: Exception) {
                        JSONObject()
                    }
                    calls.add(ToolCall(id = tc.optString("id", "call_$i"), name = fn.optString("name", ""), args = args))
                }
            }
            return Reply(text, calls)
        }
    }

    companion object {
        val PRESETS: List<Triple<String, String, String>> = listOf(
            Triple("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-2.5-flash"),
            Triple("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile")
        )
    }
}
