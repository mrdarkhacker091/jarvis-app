package com.jarvis.assistant.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.notifications.JarvisNotificationListener
import com.jarvis.assistant.notifications.NotificationStore
import com.jarvis.assistant.util.ReminderReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ToolRegistry {

    fun schema(): JSONArray {
        val tools = JSONArray()
        tools.put(tool("get_time", "Get the current local date and time.", params()))
        tools.put(tool("set_alarm", "Set a system alarm clock for a specific hour and minute.", params("hour" to "integer", "minute" to "integer", "label" to "string")))
        tools.put(tool("set_timer", "Start a countdown timer.", params("minutes" to "integer", "label" to "string")))
        tools.put(tool("open_app", "Launch an installed app by name or package id.", params("name" to "string")))
        tools.put(tool("toggle_flashlight", "Turn the torch on or off.", params("on" to "boolean")))
        tools.put(tool("set_volume", "Set media volume as a percentage of maximum.", params("percent" to "integer")))
        tools.put(tool("set_brightness", "Set screen brightness as a percentage. Requires the write-settings permission.", params("percent" to "integer")))
        tools.put(tool("get_battery", "Read battery level and charging state.", params()))
        tools.put(tool("read_notifications", "Read recently captured notifications with app, sender and text.", params("limit" to "integer")))
        tools.put(tool("reply_to_message", "Reply to the most recent notification from a matching app or sender using the notification's own reply action.", params("sender" to "string", "text" to "string")))
        tools.put(tool("web_search", "Open a web search in the browser for the user.", params("query" to "string")))
        tools.put(tool("device_action", "Perform a system navigation action: back, home, recents, notifications, quick_settings.", params("action" to "string")))
        tools.put(tool("quick_setting", "Toggle a quick settings tile by its visible label, for example Bluetooth, Wi-Fi, Hotspot, Airplane mode. Requires the accessibility service.", params("label" to "string")))
        tools.put(tool("remember", "Store a durable fact about the user that was explicitly stated.", params("fact" to "string")))
        tools.put(tool("recall", "List stored facts about the user.", params()))
        tools.put(tool("add_note", "Save a short note locally.", params("text" to "string")))
        return tools
    }

    private fun tool(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters))

    private fun params(vararg props: Pair<String, String>): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        props.forEach { pair ->
            properties.put(pair.first, JSONObject().put("type", pair.second).put("description", pair.first))
            required.put(pair.first)
        }
        return JSONObject().put("type", "object").put("properties", properties).put("required", required)
    }

    fun execute(ctx: Context, name: String, args: JSONObject): String {
        return try {
            when (name) {
                "get_time" -> getTime()
                "set_alarm" -> setAlarm(ctx, args.optInt("hour", 7), args.optInt("minute", 0), args.optString("label", "Alarm"))
                "set_timer" -> setTimer(ctx, args.optInt("minutes", 5), args.optString("label", "Timer"))
                "open_app" -> openApp(ctx, args.optString("name", ""))
                "toggle_flashlight" -> flashlight(ctx, args.optBoolean("on", true))
                "set_volume" -> setVolume(ctx, args.optInt("percent", 50))
                "set_brightness" -> setBrightness(ctx, args.optInt("percent", 50))
                "get_battery" -> getBattery(ctx)
                "read_notifications" -> readNotifications(ctx, args.optInt("limit", 5))
                "reply_to_message" -> replyToMessage(ctx, args.optString("sender", ""), args.optString("text", ""))
                "web_search" -> webSearch(ctx, args.optString("query", ""))
                "device_action" -> deviceAction(args.optString("action", "home"))
                "quick_setting" -> quickSetting(args.optString("label", ""))
                "remember" -> { Memory.remember(ctx, args.optString("fact", "")); "Stored." }
                "recall" -> Memory.toPromptString(ctx)
                "add_note" -> { Memory.remember(ctx, "Note: " + args.optString("text", "")); "Note saved." }
                else -> "Unknown tool: $name"
            }
        } catch (e: SecurityException) {
            "Permission missing for $name: ${e.message ?: "denied"}"
        } catch (e: Exception) {
            "Failed to run $name: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun getTime(): String = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())

    private fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        return "Alarm set for %02d:%02d.".format(hour, minute)
    }

    private fun setTimer(ctx: Context, minutes: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        return "Timer started for $minutes minutes."
    }

    fun resolveLaunchableApps(ctx: Context, query: String): List<Triple<String, String, android.content.Intent>> {
        val pm = ctx.packageManager
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return emptyList()
        }
        val direct = try {
            pm.getLaunchIntentForPackage(query.trim())
        } catch (e: Exception) {
            null
        }
        if (direct != null) {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(query.trim(), 0)).toString()
            } catch (e: Exception) {
                query.trim()
            }
            return listOf(Triple(query.trim(), label, direct))
        }
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val aliases = q.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val scored = ArrayList<Pair<android.content.pm.ApplicationInfo, Int>>()
        for (info in packages) {
            val label = try {
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                continue
            }
            val low = label.lowercase()
            val score = when {
                low == q -> 0
                low == aliases -> 1
                low.startsWith(q) -> 2
                low.contains(q) -> 3
                q.split(" ").all { w -> w.isNotEmpty() && low.contains(w) } -> 4
                else -> -1
            }
            if (score >= 0) {
                scored.add(info to score)
            }
        }
        scored.sortBy { it.second }
        val out = ArrayList<Triple<String, String, android.content.Intent>>()
        for ((info, _) in scored.take(6)) {
            val launch = try {
                pm.getLaunchIntentForPackage(info.packageName)
            } catch (e: Exception) {
                null
            } ?: continue
            val label = try {
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                info.packageName
            }
            out.add(Triple(info.packageName, label, launch))
        }
        return out
    }

    private fun openApp(ctx: Context, name: String): String {
        if (name.isBlank()) {
            return "I need an app name."
        }
        val matches = resolveLaunchableApps(ctx, name)
        if (matches.isEmpty()) {
            return "I could not find an app called $name on this phone."
        }
        val exact = matches.firstOrNull { it.second.lowercase() == name.trim().lowercase() }
        if (exact != null) {
            return try {
                exact.third.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(exact.third)
                "Opened ${exact.second}."
            } catch (e: SecurityException) {
                "Android blocked opening ${exact.second}."
            } catch (e: Exception) {
                "${exact.second} could not be launched: ${e.message ?: "unknown error"}."
            }
        }
        if (matches.size == 1) {
            val only = matches[0]
            return try {
                only.third.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(only.third)
                "Opened ${only.second}."
            } catch (e: SecurityException) {
                "Android blocked opening ${only.second}."
            } catch (e: Exception) {
                "${only.second} could not be launched: ${e.message ?: "unknown error"}."
            }
        }
        val names = matches.take(3).joinToString(", ") { it.second }
        return "I found more than one match: $names. Which one should I open?"
    }

    private fun flashlight(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cameraId ->
            val chars = cm.getCameraCharacteristics(cameraId)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return "This device reports no rear flash."
        cm.setTorchMode(id, on)
        return if (on) "Torch on." else "Torch off."
    }

    private fun setVolume(ctx: Context, percent: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0, 100) / 100.0).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "Media volume set to $percent percent."
    }

    private fun setBrightness(ctx: Context, percent: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            return "I do not have write-settings permission. Grant it in the setup screen first."
        }
        val value = (255 * percent.coerceIn(1, 100) / 100.0).toInt()
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return "Brightness set to $percent percent."
    }

    private fun getBattery(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "Battery $level percent, ${if (charging) "charging" else "not charging"}."
    }

    private fun readNotifications(ctx: Context, limit: Int): String {
        val items = NotificationStore.recent(ctx, limit.coerceIn(1, 20))
        if (items.isEmpty()) {
            return "No notifications captured yet. Notification access may not be granted."
        }
        return items.joinToString("\n") { n ->
            val who = n.spokenSender.ifBlank { n.sender.orEmpty() }
            val replyBit = if (n.canReply) {
                " [reply supported]"
            } else {
                ""
            }
            if (who.isNotBlank()) {
                "[${n.app}] $who: ${n.text}$replyBit"
            } else {
                "[${n.app}] ${n.text}$replyBit"
            }
        }
    }

    private fun replyToMessage(ctx: Context, sender: String, text: String): String {
        if (text.isBlank()) return "I need the reply text."
        return JarvisNotificationListener.reply(ctx, sender, text)
    }

    private fun webSearch(ctx: Context, query: String): String {
        if (query.isBlank()) return "I need something to search for."
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return "Searching the web for $query."
    }

    private fun deviceAction(action: String): String {
        val service = JarvisAccessibilityService.instance
            ?: return "The accessibility service is not running, so I cannot navigate for you."
        return service.performNamedAction(action)
    }

    private fun quickSetting(label: String): String {
        val service = JarvisAccessibilityService.instance
            ?: return "The accessibility service is not running, so I cannot toggle settings tiles."
        if (label.isBlank()) return "Which setting should I toggle?"
        return service.toggleQuickSetting(label)
    }

    fun scheduleReminder(ctx: Context, text: String, atMillis: Long): String {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "Reminder set for ${fmt.format(Date(atMillis))}."
    }

    fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
