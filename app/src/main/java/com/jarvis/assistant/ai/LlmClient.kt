package com.jarvis.assistant.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat-completions client. Used by Agnes, Groq, OpenRouter
 * and Agent Router when their endpoints speak the OpenAI schema. Gemini is
 * handled natively by GeminiProvider.
 */
class LlmClient(private val baseUrl: String, private val apiKey: String, private val model: String) {
    data class ToolCall(val id: String, val name: String, val args: JSONObject)
    data class Reply(val text: String?, val toolCalls: List<ToolCall>)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
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
            if (!resp.isSuccessful) throw HttpError(resp.code, "HTTP ${resp.code}: ${raw.take(220)}")
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) return Reply(null, emptyList())
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return Reply(null, emptyList())
            val text = message.optString("content", "").ifBlank { null }
            val calls = mutableListOf<ToolCall>()
            val tcs = message.optJSONArray("tool_calls")
            if (tcs != null) for (i in 0 until tcs.length()) {
                val tc = tcs.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val args = try { JSONObject(fn.optString("arguments", "{}")) } catch (_: Exception) { JSONObject() }
                calls.add(ToolCall(tc.optString("id", "call_$i"), fn.optString("name", ""), args))
            }
            return Reply(text, calls)
        }
    }

    class HttpError(val code: Int, message: String) : Exception(message)
}