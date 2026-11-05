package com.jarvis.assistant.ai

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Web search tool. Without a configured search API backend this opens a real
 * browser search instead of fabricating results, and says so plainly.
 */
object WebSearchTool {
    fun search(ctx: Context, query: String): String {
        if (query.isBlank()) return "What should I search for?"
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            "Searching the web for $query."
        } catch (_: Exception) { "I could not open a web search for $query." }
    }
}