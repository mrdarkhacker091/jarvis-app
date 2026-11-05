package com.jarvis.assistant.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CapturedNotification(
    val key: String, val pkg: String, val app: String,
    val senderRaw: String?, val senderSpoken: String,
    val text: String, val at: Long, val canReply: Boolean,
    var reminderState: String = "none" // none | scheduled | fired | handled | muted
)

object NotificationStore {
    private const val SP = "jarvis_notifications"
    private const val KEY = "items"
    private const val MAX = 80
    private const val STALE_MS = 24L * 60 * 60 * 1000

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun add(ctx: Context, n: CapturedNotification) {
        val all = all(ctx).toMutableList()
        // Deduplicate: a refreshed notification with the same pkg+sender+text
        // updates the timestamp instead of stacking.
        val existing = all.indexOfFirst { it.pkg == n.pkg && it.senderRaw == n.senderRaw && it.text == n.text }
        if (existing >= 0) {
            val old = all[existing]
            all[existing] = old.copy(at = n.at, reminderState = old.reminderState)
        } else {
            all.add(n)
        }
        // Expire stale records.
        val cutoff = System.currentTimeMillis() - STALE_MS
        val kept = all.filter { it.at >= cutoff }
        val trimmed = if (kept.size > MAX) kept.takeLast(MAX) else kept
        persist(ctx, trimmed)
    }

    fun all(ctx: Context): List<CapturedNotification> = try {
        val arr = JSONArray(sp(ctx).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CapturedNotification(
                o.optString("key"), o.optString("pkg"), o.optString("app"),
                o.optString("senderRaw").ifBlank { null }, o.optString("senderSpoken"),
                o.optString("text"), o.optLong("at"), o.optBoolean("canReply"),
                o.optString("reminderState", "none")
            )
        }
    } catch (_: Exception) { emptyList() }

    fun recent(ctx: Context, limit: Int) =
        all(ctx).sortedByDescending { it.at }.take(limit.coerceAtLeast(1))

    fun unread(ctx: Context): List<CapturedNotification> = recent(ctx, 20)

    fun setReminderState(ctx: Context, key: String, state: String) {
        val all = all(ctx).map { if (it.key == key) it.copy(reminderState = state) else it }
        persist(ctx, all)
    }

    fun clear(ctx: Context) { sp(ctx).edit().remove(KEY).apply() }

    private fun persist(ctx: Context, items: List<CapturedNotification>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject()
                .put("key", it.key).put("pkg", it.pkg).put("app", it.app)
                .put("senderRaw", it.senderRaw ?: "").put("senderSpoken", it.senderSpoken)
                .put("text", it.text).put("at", it.at).put("canReply", it.canReply)
                .put("reminderState", it.reminderState))
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}