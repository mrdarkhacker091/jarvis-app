package com.jarvis.assistant.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.notifications.NotificationStore
import java.util.Calendar

object Briefing {

    fun buildWakeBriefing(ctx: Context): String {
        val prefs = try {
            Prefs.get(ctx)
        } catch (e: Exception) {
            null
        }
        val detail = prefs?.briefingDetail ?: Prefs.DETAIL_NORMAL
        val greeting = greeting()
        val notes = NotificationStore.all(ctx.applicationContext)
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L
        val fresh = notes.filter { n -> (now - n.at) <= dayMs }
        val scope = if (fresh.isNotEmpty()) {
            fresh
        } else {
            notes
        }

        val msgPart = messageSummary(scope, detail)
        val batteryPart = batterySummary(ctx)
        val netPart = connectivitySummary(ctx)
        val storagePart = if (detail == Prefs.DETAIL_DETAILED) {
            storageSummary(ctx)
        } else {
            lowStorageWarning(ctx)
        }
        val problemPart = problemSummary(ctx)
        val reminderPart = reminderSummary(ctx, prefs)

        val sb = StringBuilder()
        sb.append(greeting).append(" ")
        sb.append(msgPart)
        if (detail != Prefs.DETAIL_CONCISE) {
            if (batteryPart.isNotEmpty()) {
                sb.append(" ").append(batteryPart)
            }
            if (netPart.isNotEmpty()) {
                sb.append(" ").append(netPart)
            }
        } else {
            if (batteryPart.isNotEmpty()) {
                sb.append(" ").append(batteryPart)
            }
        }
        if (storagePart.isNotEmpty() && detail == Prefs.DETAIL_DETAILED) {
            sb.append(" ").append(storagePart)
        } else if (storagePart.isNotEmpty() && storagePart.startsWith("Storage is low")) {
            sb.append(" ").append(storagePart)
        }
        if (problemPart.isNotEmpty()) {
            sb.append(" ").append(problemPart)
        }
        if (reminderPart.isNotEmpty()) {
            sb.append(" ").append(reminderPart)
        }
        sb.append(" Tell me if you need something else.")
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun greeting(): String {
        val hour = try {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        } catch (e: Exception) {
            12
        }
        return when (hour) {
            in 5..11 -> "Good morning."
            in 12..17 -> "Good afternoon."
            in 18..22 -> "Good evening."
            else -> "Hello."
        }
    }

    private fun messageSummary(
        items: List<com.jarvis.assistant.notifications.CapturedNotification>,
        detail: String
    ): String {
        if (items.isEmpty()) {
            return "You have no new notifications that I can see."
        }
        // Only records that can be tied to an app/conversation.
        val usable = items.filter { n ->
            n.app.isNotBlank() && (n.sender != null || n.text.isNotBlank())
        }
        if (usable.isEmpty()) {
            return "You have no new notifications that I can see."
        }
        val byApp = usable.groupBy { n -> n.app.ifBlank { n.pkg } }
        val total = usable.size
        if (detail == Prefs.DETAIL_CONCISE) {
            val top = byApp.entries.sortedByDescending { it.value.size }.take(2)
            val parts = top.map { e -> "${e.value.size} from ${e.key}" }
            return "I see $total recent notifications: ${parts.joinToString(", ")}."
        }
        val perApp = byApp.entries.sortedByDescending { it.value.size }.take(4).map { e ->
            val senders = e.value
                .map { n -> n.spokenSender.ifBlank { n.sender.orEmpty() } }
                .filter { s -> s.isNotBlank() }
                .distinct()
            val senderBit = when {
                senders.isEmpty() -> ""
                detail == Prefs.DETAIL_DETAILED -> " from ${senders.take(3).joinToString(", ")}"
                else -> " from ${senders.take(2).joinToString(", ")}"
            }
            "${e.value.size} from ${e.key}$senderBit"
        }
        var out = if (total == 1) {
            "You have 1 notification: ${perApp.joinToString("; ")}."
        } else {
            "You have $total notifications: ${perApp.joinToString("; ")}."
        }
        if (detail == Prefs.DETAIL_DETAILED) {
            val latest = usable.maxByOrNull { n -> n.at }
            if (latest != null && latest.text.isNotBlank()) {
                val who = latest.spokenSender.ifBlank { latest.sender.orEmpty() }
                val preview = latest.text.trim().replace(Regex("\\s+"), " ").take(90)
                if (who.isNotBlank() && preview.isNotBlank()) {
                    out += " Latest is from $who: $preview."
                }
            }
        }
        return out
    }

    private fun batterySummary(ctx: Context): String {
        return try {
            val bm = ctx.applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = try {
                bm.isCharging
            } catch (e: Exception) {
                false
            }
            if (level < 0) {
                ""
            } else if (charging) {
                "Battery is $level percent and charging."
            } else {
                "Battery is $level percent."
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun connectivitySummary(ctx: Context): String {
        return try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork ?: return "No network connection."
            val caps = cm.getNetworkCapabilities(active) ?: return "No network connection."
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wired network"
                else -> "a network"
            }
            "Connected over $transport."
        } catch (e: Exception) {
            ""
        }
    }

    private fun storageSummary(ctx: Context): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val free = stat.availableBytes
            val gb = free / 1024.0 / 1024.0 / 1024.0
            "Free storage is about ${"%.1f".format(gb)} gigabytes."
        } catch (e: Exception) {
            ""
        }
    }

    private fun lowStorageWarning(ctx: Context): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val free = stat.availableBytes
            val twoGb = 2L * 1024L * 1024L * 1024L
            if (free < twoGb) {
                val gb = free / 1024.0 / 1024.0 / 1024.0
                "Storage is low, about ${"%.1f".format(gb)} gigabytes free."
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun problemSummary(ctx: Context): String {
        val appCtx = ctx.applicationContext
        val problems = ArrayList<String>()
        try {
            val mic = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!mic) {
                problems.add("microphone permission is off")
            }
        } catch (e: Exception) {
            // Ignore: do not invent a problem we could not check.
        }
        try {
            val ok = NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
            if (!ok) {
                problems.add("notification access is off, so I can only see new messages once you grant it")
            }
        } catch (e: Exception) {
            // Ignore: do not invent a problem we could not check.
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(appCtx)) {
                    problems.add("write-settings permission is off, so brightness control will not work")
                }
            }
        } catch (e: Exception) {
            // Ignore: do not invent a problem we could not check.
        }
        if (problems.isEmpty()) {
            return ""
        }
        return "One thing: ${problems.joinToString("; ")}."
    }

    private fun reminderSummary(ctx: Context, prefs: Prefs?): String {
        if (prefs != null && !prefs.oneHourReminders) {
            return ""
        }
        return try {
            val candidates = NotificationStore.pendingReminderCandidates(ctx.applicationContext)
            if (candidates.isEmpty()) {
                ""
            } else {
                val names = candidates.take(2).map { n ->
                    val who = n.spokenSender.ifBlank { n.sender.orEmpty() }
                    if (who.isNotBlank()) {
                        "$who on ${n.app}"
                    } else {
                        "a contact on ${n.app}"
                    }
                }
                "You may still have an unanswered message from ${names.joinToString(" and ")}."
            }
        } catch (e: Exception) {
            ""
        }
    }
}
