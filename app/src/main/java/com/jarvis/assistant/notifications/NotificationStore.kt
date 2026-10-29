package com.jarvis.assistant.notifications

import android.content.Context
import com.jarvis.assistant.util.NameNormalize

data class CapturedNotification(
    val id: Int,
    val pkg: String,
    val app: String,
    val sender: String?,
    val spokenSender: String,
    val text: String,
    val at: Long,
    val canReply: Boolean,
    val key: String,
    val conversationKey: String
)

object NotificationStore {
    private const val SP = "jarvis_notifications"
    private const val KEY = "items"
    private const val MAX = 60
    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    private const val ANSWER_SP = "jarvis_answered"
    private const val ANSWER_KEY = "answered"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    private fun answerSp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(ANSWER_SP, Context.MODE_PRIVATE)

    fun add(ctx: Context, n: CapturedNotification) {
        val kept = all(ctx).toMutableList()
        // Dedupe on (pkg, key, text, title): refresh the timestamp instead of doubling up.
        val existing = kept.indexOfFirst { old ->
            old.pkg == n.pkg && old.key == n.key &&
                old.text == n.text && old.sender == n.sender
        }
        if (existing >= 0) {
            kept[existing] = n
        } else {
            kept.add(n)
        }
        prune(kept)
        while (kept.size > MAX) {
            kept.removeAt(0)
        }
        persist(ctx, kept)
    }

    fun all(ctx: Context): List<CapturedNotification> {
        return try {
            val raw = sp(ctx).getString(KEY, "[]") ?: "[]"
            val arr = org.json.JSONArray(raw)
            val out = ArrayList<CapturedNotification>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.optString("pkg")
                val app = o.optString("app")
                val sender = o.optString("sender").ifBlank { null }
                val storedSpoken = o.optString("spokenSender", "")
                val spoken = if (storedSpoken.isNotBlank()) {
                    storedSpoken
                } else if (sender != null) {
                    NameNormalize.spokenOrFallback(sender, app)
                } else {
                    ""
                }
                out.add(
                    CapturedNotification(
                        id = o.optInt("id"),
                        pkg = pkg,
                        app = app,
                        sender = sender,
                        spokenSender = spoken,
                        text = o.optString("text"),
                        at = o.optLong("at"),
                        canReply = o.optBoolean("canReply"),
                        key = o.optString("key", ""),
                        conversationKey = o.optString(
                            "conversationKey",
                            conversationKey(pkg, o.optString("key", ""), sender)
                        )
                    )
                )
            }
            prune(out)
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recent(ctx: Context, limit: Int) = all(ctx).takeLast(limit.coerceAtLeast(1)).reversed()

    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY).apply()
    }

    fun pruneExpired(ctx: Context) {
        val kept = all(ctx).toMutableList()
        val before = kept.size
        prune(kept)
        if (kept.size != before) {
            persist(ctx, kept)
        }
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
        val answered = answerSp(ctx).all.toMutableMap()
        var changed = false
        val it = answered.keys.iterator()
        while (it.hasNext()) {
            val k = it.next()
            val ts = (answered[k] as? Long) ?: 0L
            if (ts < cutoff) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            val ed = answerSp(ctx).edit()
            ed.clear()
            for ((k, v) in answered) {
                ed.putLong(k, v as Long)
            }
            ed.apply()
        }
    }

    private fun prune(items: MutableList<CapturedNotification>) {
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
        items.removeAll { it.at < cutoff }
    }

    fun conversationKey(pkg: String, key: String, sender: String?): String {
        val who = (sender ?: "").trim().lowercase()
        return if (key.isNotEmpty()) {
            "$pkg|$key|$who"
        } else {
            "$pkg|$who"
        }
    }

    fun countsByApp(ctx: Context): Map<String, Int> {
        return all(ctx).groupingBy { it.app.ifBlank { it.pkg } }.eachCount()
    }

    fun countsByConversation(ctx: Context): Map<String, Int> {
        return all(ctx).groupingBy { it.conversationKey }.eachCount()
    }

    fun sendersByApp(ctx: Context): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (n in all(ctx)) {
            val app = n.app.ifBlank { n.pkg }
            val name = n.spokenSender.ifBlank { n.sender.orEmpty() }
            if (name.isBlank()) {
                continue
            }
            val bucket = out.getOrPut(app) { ArrayList() }
            if (!bucket.any { it.equals(name, ignoreCase = true) }) {
                bucket.add(name)
            }
        }
        return out
    }

    fun markAnswered(ctx: Context, conversationKey: String) {
        if (conversationKey.isBlank()) {
            return
        }
        answerSp(ctx).edit().putLong(conversationKey, System.currentTimeMillis()).apply()
    }

    fun isAnswered(ctx: Context, conversationKey: String): Boolean {
        if (conversationKey.isBlank()) {
            return false
        }
        return answerSp(ctx).contains(conversationKey)
    }

    fun answeredKeys(ctx: Context): Set<String> {
        return try {
            answerSp(ctx).all.keys.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Conversations posted more than an hour ago that have not been
     * marked answered. A disappearing notification is NOT treated as a
     * reply, so anything not explicitly marked answered stays pending.
     */
    fun pendingReminderCandidates(ctx: Context, now: Long = System.currentTimeMillis()): List<CapturedNotification> {
        val oneHour = 60L * 60L * 1000L
        val answered = answeredKeys(ctx)
        val latest = LinkedHashMap<String, CapturedNotification>()
        for (n in all(ctx)) {
            val prev = latest[n.conversationKey]
            if (prev == null || n.at > prev.at) {
                latest[n.conversationKey] = n
            }
        }
        return latest.values
            .filter { n ->
                n.conversationKey.isNotBlank() &&
                    (now - n.at) >= oneHour &&
                    !answered.contains(n.conversationKey)
            }
            .sortedBy { it.at }
    }

    private fun persist(ctx: Context, items: List<CapturedNotification>) {
        val arr = org.json.JSONArray()
        for (it in items) {
            arr.put(
                org.json.JSONObject()
                    .put("id", it.id)
                    .put("pkg", it.pkg)
                    .put("app", it.app)
                    .put("sender", it.sender ?: "")
                    .put("spokenSender", it.spokenSender)
                    .put("text", it.text)
                    .put("at", it.at)
                    .put("canReply", it.canReply)
                    .put("key", it.key)
                    .put("conversationKey", it.conversationKey)
            )
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
