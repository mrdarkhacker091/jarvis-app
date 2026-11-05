package com.jarvis.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.services.JarvisForegroundService
import com.jarvis.assistant.ui.AppScanScreen
import com.jarvis.assistant.ui.HomeScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.OnboardingScreen
import com.jarvis.assistant.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val runtimePerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        askRuntimePermissions()
        setContent {
            JarvisTheme {
                var screen by remember { mutableStateOf("home") }
                val refresh = remember { mutableStateOf(0) }
                val warning = buildWarning()
                when (screen) {
                    "settings" -> SettingsScreen(
                        prefs = Prefs.get(this),
                        onBack = { screen = "home"; refresh.value++ },
                        onGrantNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onGrantWriteSettings = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onGrantOverlay = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onBatteryExemption = { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onClearMemory = { Memory.clear(this); refresh.value++ },
                        onEraseKeys = { Prefs.get(this).eraseKeys(); refresh.value++ },
                        onRestartService = { JarvisForegroundService.start(this) },
                        onShowScan = { screen = "scan" }
                    )
                    "scan" -> AppScanScreen(this, onBack = { screen = "home" })
                    "onboarding" -> OnboardingScreen(onDone = { screen = "home" },
                        onGrantNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onGrantAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onGrantContacts = { requestContacts() })
                    else -> HomeScreen(
                        turns = JarvisBus.turns.value,
                        state = JarvisBus.state.value,
                        subtitle = JarvisBus.subtitle.value,
                        onTalk = { startService(Intent(this, JarvisForegroundService::class.java).setAction(JarvisForegroundService.ACTION_LISTEN_ONCE)) },
                        onSend = { text -> startService(Intent(this, JarvisForegroundService::class.java).setAction(JarvisForegroundService.ACTION_PROCESS).putExtra(JarvisForegroundService.EXTRA_TEXT, text)) },
                        onSettings = { screen = "settings" },
                        onClear = { JarvisBus.clear() },
                        onToggleWake = { enabled -> Prefs.get(this).wakeWordEnabled = enabled; if (enabled) JarvisForegroundService.start(this) },
                        setupWarning = warning,
                        activity = this
                    )
                }
            }
        }
    }

    private fun askRuntimePermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) needed.add(Manifest.permission.READ_CONTACTS)
        runtimePerms.launch(needed.toTypedArray())
    }

    private fun requestContacts() {
        runtimePerms.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasAccessibility(): Boolean = JarvisAccessibilityService.instance != null

    private fun buildWarning(): String? {
        val missing = mutableListOf<String>()
        if (!hasMic()) missing.add("microphone")
        if (!hasNotificationAccess()) missing.add("notification access")
        if (!hasAccessibility()) missing.add("accessibility")
        return if (missing.isEmpty()) null else "Missing: " + missing.joinToString(", ") + ". See Settings → Permissions."
    }
}