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
        // Resume only if the user explicitly enabled wake listening. Android
        // may still block background microphone start; the foreground service
        // starts the visible notification and reports honestly if it cannot.
        if (!Prefs.get(context).wakeWordEnabled) return
        try { JarvisForegroundService.start(context) } catch (_: Exception) { }
    }
}