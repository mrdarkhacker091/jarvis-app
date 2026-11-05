package com.jarvis.assistant.ai.providers

import com.jarvis.assistant.ai.LlmClient
import com.jarvis.assistant.data.Provider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Native Google Gemini generateContent adapter (not the OpenAI-compatible
 * route). Tools are mapped to Gemini functionDeclarations; the response parts
 * are parsed back into the shared LlmClient.Reply shape.
 */
object GeminiProvider {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun complete(p: Provider, messages: JSONArray, tools: JSONArray?): LlmClient.Reply {
        val system = StringBuilder()
        val contents = JSONArray()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role")
            val content = m.optString("content", "")
            when (role) {
                "system" -> system.append(content).append("\n")
                "user" -> contents.put(contentPart("user", content))
                "assistant" -> {
                    val tc = m.optJSONArray("tool_calls")
                    if (tc != null && tc.length() > 0) {
                        val parts = JSONArray()
                        if (content.isNotBlank()) parts.put(JSONObject().put("text", content))
                        for (j in 0 until tc.length()) {
                            val call = tc.getJSONObject(j).optJSONObject("function") ?: continue
                            val args = try { JSONObject(call.optString("arguments", "{}")) } catch (_: Exception) { JSONObject() }
                            parts.put(JSONObject().put("functionCall",
                                JSONObject().put("name", call.optString("name")).put("args", args)))
                        }
                        contents.put(JSONObject().put("role", "model").put("parts", parts))
                    } else {
                        contents.put(contentPart("model", content))
                    }
                }
                "tool" -> {
                    val name = m.optString("name", "tool")
                    contents.put(JSONObject().put("role", "function")
                        .put("parts", JSONArray().put(JSONObject().put("functionResponse",
                            JSONObject().put("name", name).put("response", JSONObject().put("name", name).put("content", content))))))
                }
            }
        }
        val body = JSONObject()
        if (system.isNotBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString()))))
        body.put("contents", contents)
        val cfg = JSONObject().put("temperature", 0.6).put("maxOutputTokens", 1024)
        body.put("generationConfig", cfg)
        if (tools != null && tools.length() > 0) {
            val decls = JSONArray()
            for (i in 0 until tools.length()) {
                val f = tools.getJSONObject(i).optJSONObject("function") ?: continue
                decls.put(JSONObject().put("name", f.optString("name")).put("description", f.optString("description")).put("parameters", f.opt("parameters") ?: JSONObject()))
            }
            body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
        val url = p.baseUrl.trimEnd('/') + "/models/" + p.model.removePrefix("models/") + ":generateContent"
        val request = Request.Builder().url(url)
            .addHeader("x-goog-api-key", p.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw LlmClient.HttpError(resp.code, "Gemini ${resp.code}: ${raw.take(220)}")
            val json = JSONObject(raw)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return LlmClient.Reply(null, emptyList())
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val text = StringBuilder()
            val calls = mutableListOf<LlmClient.ToolCall>()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) text.append(part.getString("text"))
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val name = fc.optString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    calls.add(LlmClient.ToolCall("call_$i", name, args))
                }
            }
            return LlmClient.Reply(text.toString().ifBlank { null }, calls)
        }
    }

    private fun contentPart(role: String, text: String): JSONObject =
        JSONObject().put("role", if (role == "user") "user" else "model")
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
}