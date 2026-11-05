package com.jarvis.assistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.notifications.NotificationStore

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val key = intent.getStringExtra(EXTRA_KEY)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(NotificationChannel(CHANNEL,
                    context.getString(com.jarvis.assistant.R.string.notification_channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH))
            }
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif: android.app.Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL)
                .setContentTitle("JARVIS reminder").setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_jarvis).setContentIntent(open).setAutoCancel(true).build()
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(context)
                .setContentTitle("JARVIS reminder").setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_jarvis).setContentIntent(open).setAutoCancel(true).build()
        }
        if (key != null) NotificationStore.setReminderState(context, key, "fired")
        mgr.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }
    companion object {
        const val EXTRA_TEXT = "reminder_text"
        const val EXTRA_KEY = "reminder_key"
        private const val CHANNEL = "jarvis_reminders"
    }
}