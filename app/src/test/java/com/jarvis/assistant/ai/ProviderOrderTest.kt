package com.jarvis.assistant.ai

import com.jarvis.assistant.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOrderTest {
    private val full = listOf(
        Provider("agnes", "Agnes", true, "https://x", "m", "k"),
        Provider("gemini", "Gemini", true, "https://g", "m", "k", nativeGemini = true),
        Provider("groq", "Groq", false, "https://q", "m", "k"),
        Provider("openrouter", "OpenRouter", true, "", "m", "k"),
        Provider("agentrouter", "Agent Router", true, "https://a", "m", "")
    )

    @Test fun onlyEnabledAndConfiguredRemain() {
        val ordered = ProviderOrder.orderedEnabled(full)
        assertEquals(listOf("agnes", "gemini"), ordered.map { it.id })
    }

    @Test fun emptyWhenNothingConfigured() {
        assertTrue(ProviderOrder.orderedEnabled(emptyList()).isEmpty())
    }
}