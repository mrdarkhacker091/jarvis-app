package com.jarvis.assistant.data

import android.content.Context
import org.json.JSONArray

object Memory {
    private const val SP = "jarvis_memory"; private const val KEY = "facts"; private const val MAX = 40
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
    fun remember(ctx: Context, fact: String) {
        val clean = fact.trim().replace("\n", " "); if (clean.isEmpty()) return
        val all = list(ctx).toMutableList(); if (all.any { it.equals(clean, ignoreCase = true) }) return
        all.add(clean); while (all.size > MAX) all.removeAt(0); save(ctx, all)
    }
    fun list(ctx: Context): List<String> = try {
        val arr = JSONArray(sp(ctx).getString(KEY, "[]") ?: "[]"); (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
    fun clear(ctx: Context) { sp(ctx).edit().remove(KEY).apply() }
    fun toPromptString(ctx: Context): String = list(ctx).joinToString("\n") { "- $it" }.ifEmpty { "No stored facts yet." }
    private fun save(ctx: Context, facts: List<String>) {
        val arr = JSONArray(); facts.forEach { arr.put(it) }; sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
