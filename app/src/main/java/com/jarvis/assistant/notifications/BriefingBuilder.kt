package com.jarvis.assistant.notifications

import android.content.Context
import android.os.BatteryManager
import com.jarvis.assistant.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BriefingBuilder {
    fun build(ctx: Context, detail: String): String {
        val parts = mutableListOf<String>()
        parts.add("Good to hear you. It is " +
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + ".")
        parts.add(battery(ctx))
        val recent = NotificationStore.recent(ctx, if (detail == "detailed") 20 else 8)
        if (recent.isNotEmpty()) {
            val byApp = recent.groupBy { it.app }
            val lines = byApp.entries.take(if (detail == "concise") 3 else 6).joinToString("; ") { (app, list) ->
                val senders = list.map { it.senderSpoken.ifBlank { "a contact" } }.distinct().take(3).joinToString(", ")
                "$app${if (senders.isNotBlank()) ", from $senders" else ""}: ${list.size}"
            }
            parts.add("Messages waiting: $lines.")
        } else {
            parts.add("No pending notifications right now.")
        }
        if (detail != "concise") parts.add(storage(ctx))
        parts.add("Tell me if you need something else.")
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun battery(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level < 0) return ""
        val charging = try {
            val f = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val s = f?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) { false }
        return "Battery is at $level percent" + if (charging) ", charging." else "."
    }

    private fun storage(ctx: Context): String {
        return try {
            val stat = android.os.StatFs(ctx.filesDir.absolutePath)
            val free = stat.availableBytes / (1024L * 1024L * 1024L)
            "About $free gigabytes of storage free."
        } catch (_: Exception) { "" }
    }
}