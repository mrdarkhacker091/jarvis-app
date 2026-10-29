package com.jarvis.assistant.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.services.JarvisForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        if (!Prefs.get(context).wakeWordEnabled) return
        try {
            JarvisForegroundService.start(context)
        } catch (e: Exception) {
            // Service start after boot is best-effort; user can start it manually.
        }
    }
}
