package com.jarvis.assistant.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.util.ReminderReceiver
import java.security.MessageDigest

/**
 * Captures notification previews for the wake briefing and explicit "read my
 * notifications". It NEVER speaks a new notification automatically — the
 * announceNotifications setting is read but spoken announcements are only made
 * on explicit request or wake (see BriefingBuilder).
 */
class JarvisNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        val prefs = Prefs.get(this)
        if (!prefs.isPackageAllowed(n.packageName)) return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        val app = appLabel(n.packageName)
        val canReply = hasReplyAction(n)
        val spoken = NameNormalizer.normalizeForSpeech(title)
        val key = hash(n.packageName + title + body)
        NotificationStore.add(this, CapturedNotification(
            key, n.packageName, app, title.ifBlank { null }, spoken,
            body.ifBlank { title }, n.postTime, canReply
        ))
        if (prefs.remindersEnabled) scheduleOneHourReminder(key, spoken.ifBlank { app })
    }

    private fun scheduleOneHourReminder(key: String, who: String) {
        val triggerAt = System.currentTimeMillis() + 60 * 60 * 1000
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TEXT, "You may still have an unanswered message from $who.")
            putExtra(ReminderReceiver.EXTRA_KEY, key)
        }
        val pi = PendingIntent.getBroadcast(this, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            NotificationStore.setReminderState(this, key, "scheduled")
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: Exception) { }
    }

    fun sendReply(senderHint: String, text: String): String {
        val active = try { activeNotifications ?: return "No notification access." }
        catch (_: Exception) { return "No notification access." }
        val hint = senderHint.trim().lowercase()
        val candidates = active.filter { n ->
            val e = n.notification?.extras ?: return@filter false
            val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val b = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            hint.isEmpty() || t.lowercase().contains(hint) || b.lowercase().contains(hint) || appLabel(n.packageName).lowercase().contains(hint)
        }.sortedByDescending { it.postTime }
        if (candidates.isEmpty()) return "No current notification matches $senderHint."
        for (sbn in candidates) {
            for (action in sbn.notification?.actions ?: continue) {
                val inputs = action.remoteInputs ?: continue
                if (inputs.isEmpty()) continue
                val bundle = android.os.Bundle()
                inputs.forEach { ri -> bundle.putCharSequence(ri.resultKey, text) }
                val i = Intent(); RemoteInput.addResultsToIntent(inputs, i, bundle)
                return try { action.actionIntent.send(this, 0, i); "Reply sent." }
                catch (_: Exception) { "Could not send reply." }
            }
        }
        return "That notification has no reply action."
    }

    private fun hasReplyAction(n: StatusBarNotification) =
        n.notification?.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true

    private fun appLabel(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun hash(s: String): String = try {
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
    } catch (_: Exception) { s.hashCode().toString() }

    companion object { @Volatile var instance: JarvisNotificationListener? = null }
}