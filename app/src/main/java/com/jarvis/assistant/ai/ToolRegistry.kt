package com.jarvis.assistant.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.notifications.JarvisNotificationListener
import com.jarvis.assistant.notifications.NotificationStore
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
        tools.put(tool("get_battery", "Read battery level and charging state.", params()))
        tools.put(tool("open_app", "Launch an installed app by name.", params("name" to "string")))
        tools.put(tool("call_contact", "Search contacts and open the dialer for a name.", params("name" to "string")))
        tools.put(tool("read_notifications", "Read recently captured notification previews with app, sender and text.", params("limit" to "integer")))
        tools.put(tool("reply_to_message", "Reply to a matching notification using its own reply action.", params("sender" to "string", "text" to "string")))
        tools.put(tool("web_search", "Open a real web search in the browser.", params("query" to "string")))
        tools.put(tool("device_action", "System navigation: back, home, recents, notifications, quick_settings. Requires accessibility.", params("action" to "string")))
        tools.put(tool("screen_click", "Tap a visible control by label. Requires accessibility.", params("label" to "string")))
        tools.put(tool("screen_type", "Type text into the focused text field. Requires accessibility.", params("text" to "string")))
        tools.put(tool("screen_scroll", "Scroll the screen up or down. Requires accessibility.", params("direction" to "string")))
        tools.put(tool("read_screen", "Read visible non-protected text on the current screen. Requires accessibility.", params()))
        tools.put(tool("toggle_flashlight", "Turn the torch on or off.", params("on" to "boolean")))
        tools.put(tool("set_volume", "Set media volume as a percentage of maximum.", params("percent" to "integer")))
        tools.put(tool("set_alarm", "Set a system alarm clock for an hour and minute.", params("hour" to "integer", "minute" to "integer", "label" to "string")))
        tools.put(tool("remember", "Store a durable fact about the user that was explicitly stated.", params("fact" to "string")))
        tools.put(tool("recall", "List stored facts about the user.", params()))
        return tools
    }

    fun execute(ctx: Context, name: String, args: JSONObject): String {
        return try { dispatch(ctx, name, args) } catch (e: Exception) { "Tool $name failed: ${e.message ?: e.javaClass.simpleName}" }
    }

    private fun dispatch(ctx: Context, name: String, args: JSONObject): String = when (name) {
        "get_time" -> SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        "get_battery" -> LocalCommandRouter.tryLocal(ctx, "battery percentage") ?: "I could not read the battery."
        "open_app" -> LocalCommandRouter.tryLocal(ctx, "open " + args.optString("name")) ?: "No app name given."
        "call_contact" -> LocalCommandRouter.tryLocal(ctx, "call " + args.optString("name")) ?: "No name given."
        "read_notifications" -> readNotifications(ctx, args.optInt("limit", 8))
        "reply_to_message" -> JarvisNotificationListener.instance?.sendReply(args.optString("sender"), args.optString("text")) ?: "Notification access is not enabled."
        "web_search" -> WebSearchTool.search(ctx, args.optString("query"))
        "device_action" -> accessibility()?.performNamedAction(args.optString("action")) ?: "Accessibility is not enabled."
        "screen_click" -> accessibility()?.click(args.optString("label")) ?: "Accessibility is not enabled."
        "screen_type" -> accessibility()?.typeText(args.optString("text")) ?: "Accessibility is not enabled."
        "screen_scroll" -> accessibility()?.scroll(args.optString("direction")) ?: "Accessibility is not enabled."
        "read_screen" -> accessibility()?.readScreen() ?: "Accessibility is not enabled."
        "toggle_flashlight" -> toggleFlashlight(ctx, args.optBoolean("on"))
        "set_volume" -> setVolume(ctx, args.optInt("percent", 50))
        "set_alarm" -> setAlarm(ctx, args.optInt("hour"), args.optInt("minute"), args.optString("label"))
        "remember" -> { Memory.remember(ctx, args.optString("fact")); "Remembered." }
        "recall" -> Memory.list(ctx).joinToString("; ").ifBlank { "Nothing stored yet." }
        else -> "I do not have a tool called $name."
    }

    private fun readNotifications(ctx: Context, limit: Int): String {
        val items = NotificationStore.recent(ctx, limit)
        if (items.isEmpty()) return "No notifications are available. Make sure notification access is enabled."
        return items.joinToString(". ") {
            val who = it.senderSpoken.ifBlank { it.app }
            "$it.app from $who: ${it.text.take(80)}"
        }
    }

    private fun toggleFlashlight(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return "No camera service."
        return try {
            val id = cm.cameraIdList.firstOrNull { cam ->
                cm.getCameraCharacteristics(cam).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight available."
            cm.setTorchMode(id, on); if (on) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) { "I could not toggle the flashlight: ${e.message}." }
    }

    private fun setVolume(ctx: Context, percent: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "No audio service."
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * (percent.coerceIn(0, 100) / 100.0)).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "Media volume set to $percent percent."
    }

    private fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "I need a valid hour and minute."
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { ctx.startActivity(intent); "Alarm set for $hour:$minute." }
        catch (_: Exception) { "I could not open the alarm screen." }
    }

    private fun accessibility() = JarvisAccessibilityService.instance

    private fun tool(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", description).put("parameters", parameters))

    private fun params(vararg props: Pair<String, String>): JSONObject {
        val o = JSONObject().put("type", "object")
        val names = JSONArray()
        val schema = JSONObject()
        props.forEach { (k, v) -> schema.put(k, JSONObject().put("type", v)); names.put(k) }
        o.put("properties", schema)
        o.put("required", names)
        return o
    }
}