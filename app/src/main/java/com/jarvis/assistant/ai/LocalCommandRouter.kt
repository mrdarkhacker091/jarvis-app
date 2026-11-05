package com.jarvis.assistant.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.Settings
import com.jarvis.assistant.contacts.ContactsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic local commands that never call an LLM. If the utterance maps
 * to a local intent, it is handled here and the AI is never contacted.
 */
object LocalCommandRouter {

    fun tryLocal(ctx: Context, text: String): String? {
        val t = text.lowercase().trim()
        return when {
            t in setOf("what time is it", "what's the time", "time", "what is the time", "tell me the time") -> timeReply()
            t.contains("battery") && (t.contains("percent") || t.contains("level") || t.contains("how much") || t.contains("charge")) -> batteryReply(ctx)
            t.startsWith("open ") -> openApp(ctx, t.removePrefix("open ").trim())
            t.startsWith("call ") -> ContactsHelper.callByName(ctx, t.removePrefix("call ").trim())
            else -> null
        }
    }

    private fun timeReply(): String {
        val now = SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        return "It is $now."
    }

    private fun batteryReply(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = try {
            val ifilter = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = ifilter?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) { false }
        return if (level < 0) "I could not read the battery level."
        else "Battery is at $level percent" + if (charging) ", and charging." else "."
    }

    private fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val matches = AppResolver.resolve(pm, name)
        if (matches.isEmpty()) return "I could not find an app called $name."
        if (matches.size > 1) return "I found more than one: ${matches.joinToString(", ") { it.label }}. Which one?"
        val target = matches.first()
        return try {
            val launch = pm.getLaunchIntentForPackage(target.pkg)
                ?: return "${target.label} is installed but has no launch screen."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
            "Opening ${target.label}."
        } catch (e: Exception) { "I could not open ${target.label}." }
    }
}

data class AppMatch(val label: String, val pkg: String)