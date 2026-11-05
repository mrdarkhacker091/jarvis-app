package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.ai.providers.GeminiProvider
import com.jarvis.assistant.data.Prefs
import org.json.JSONArray

/**
 * Priority-based failover across the configured providers. The first enabled,
 * healthy provider answers; on a retryable error (timeout, 5xx, 429) it moves
 * to the next. Auth errors (401/403) mark a provider unhealthy for this run
 * and move on. One final answer is returned, not several.
 */
object ProviderManager {
    data class Outcome(val reply: LlmClient.Reply, val provider: String)

    private val cooldown = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 30_000L

    fun complete(ctx: Context, messages: JSONArray, tools: JSONArray?): Outcome {
        val prefs = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        val chain = ProviderOrder.orderedEnabled(prefs.providers()).filter { (cooldown[it.id] ?: 0) <= now }
        if (chain.isEmpty()) throw IllegalStateException("No AI provider is configured. Open Settings → AI Providers and enter an endpoint, model and key.")

        var lastErr: String? = null
        for (p in chain) {
            try {
                val reply = if (p.nativeGemini) GeminiProvider.complete(p, messages, tools)
                else LlmClient(p.baseUrl, p.apiKey, p.model).complete(messages, tools)
                return Outcome(reply, p.name)
            } catch (e: LlmClient.HttpError) {
                lastErr = "${p.name}: ${e.message}"
                if (e.code == 401 || e.code == 403) cooldown[p.id] = now + COOLDOWN_MS
                if (e.code == 429 || e.code in 500..599) cooldown[p.id] = now + COOLDOWN_MS
            } catch (e: Exception) {
                lastErr = "${p.name}: ${e.message ?: e.javaClass.simpleName}"
                cooldown[p.id] = now + COOLDOWN_MS
            }
        }
        throw IllegalStateException("All providers failed. ${lastErr ?: ""}")
    }
}