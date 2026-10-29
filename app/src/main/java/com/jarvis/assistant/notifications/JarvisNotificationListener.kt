package com.jarvis.assistant.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.assistant.R
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.util.NameNormalize

class JarvisNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) {
            return
        }
        val prefs = Prefs.get(this)
        if (!prefs.isPackageAllowed(n.packageName)) {
            return
        }
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) {
            return
        }
        val app = appLabel(n.packageName)
        val canReply = hasReplyAction(n)
        val key = n.key.orEmpty()
        val sender = title.ifBlank { null }
        val spoken = if (sender != null) {
            NameNormalize.spokenOrFallback(sender, app)
        } else {
            ""
        }
        // Intentionally silent: never speak on post. The user only hears
        // a summary on wake or explicit request.
        NotificationStore.add(
            this,
            CapturedNotification(
                id = n.id,
                pkg = n.packageName,
                app = app,
                sender = sender,
                spokenSender = spoken,
                text = body.ifBlank { title },
                at = n.postTime,
                canReply = canReply,
                key = key,
                conversationKey = NotificationStore.conversationKey(n.packageName, key, sender)
            )
        )
    }

    fun sendReply(senderHint: String, text: String): String {
        val active = try {
            activeNotifications ?: return "No notification access."
        } catch (e: Exception) {
            return "No notification access."
        }
        val hint = senderHint.trim().lowercase()
        val candidates = active.filter { n ->
            val e = n.notification?.extras ?: return@filter false
            val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val b = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            hint.isEmpty() || t.lowercase().contains(hint) ||
                b.lowercase().contains(hint) ||
                appLabel(n.packageName).lowercase().contains(hint)
        }.sortedByDescending { it.postTime }
        if (candidates.isEmpty()) {
            return "No current notification matches $senderHint."
        }
        for (sbn in candidates) {
            val actions = sbn.notification?.actions ?: continue
            for (action in actions) {
                val inputs = action.remoteInputs ?: continue
                if (inputs.isEmpty()) {
                    continue
                }
                val bundle = android.os.Bundle()
                for (ri in inputs) {
                    bundle.putCharSequence(ri.resultKey, text)
                }
                val i = Intent()
                RemoteInput.addResultsToIntent(inputs, i, bundle)
                return try {
                    action.actionIntent.send(this, 0, i)
                    // Record the reply we sent via the supported action.
                    try {
                        val extras = sbn.notification?.extras
                        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                        NotificationStore.markAnswered(
                            this,
                            NotificationStore.conversationKey(sbn.packageName, sbn.key.orEmpty(), title)
                        )
                    } catch (markError: Exception) {
                        // Marking answered is best-effort; the reply already went out.
                    }
                    "Reply sent."
                } catch (e: Exception) {
                    "Could not send reply."
                }
            }
        }
        return "That notification has no reply action."
    }

    private fun hasReplyAction(n: StatusBarNotification): Boolean {
        val actions = n.notification?.actions ?: return false
        return actions.any { it.remoteInputs != null }
    }

    private fun appLabel(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        @Volatile
        var instance: JarvisNotificationListener? = null

        fun reply(ctx: Context, senderHint: String, text: String): String {
            val svc = instance ?: return "Notification access not connected."
            return svc.sendReply(senderHint, text)
        }

        fun markConversationAnswered(ctx: Context, conversationKey: String) {
            NotificationStore.markAnswered(ctx, conversationKey)
        }
    }
}
