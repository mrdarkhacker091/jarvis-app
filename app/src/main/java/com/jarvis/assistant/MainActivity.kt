package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.notifications.NotificationStore
import com.jarvis.assistant.services.JarvisForegroundService
import com.jarvis.assistant.ui.Dashboard
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val runtimePerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        NotificationStore.pruneExpired(this)
        askForRuntimePermissions()
        setContent {
            JarvisTheme {
                val turns by JarvisBus.turns.collectAsState()
                val state by JarvisBus.state.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current
                var resumeTick by remember { mutableStateOf(0) }
                var notifSnapshot by remember { mutableStateOf(NotificationStore.recent(this@MainActivity, 30)) }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            resumeTick += 1
                            try {
                                notifSnapshot = NotificationStore.recent(this@MainActivity, 30)
                            } catch (e: Exception) {
                                // Best effort: keep the last snapshot.
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val warning = buildString {
                    val missing = mutableListOf<String>()
                    if (!hasMic()) {
                        missing.add("microphone")
                    }
                    if (!hasNotificationAccess()) {
                        missing.add("notification access")
                    }
                    if (!hasAccessibility()) {
                        missing.add("accessibility")
                    }
                    if (missing.isEmpty()) {
                        append("")
                    } else {
                        append("Missing: ")
                        append(missing.joinToString(", "))
                        append(". Grant them in settings.")
                    }
                }.ifBlank { null }

                @Suppress("UNUSED_EXPRESSION")
                resumeTick

                if (showSettings) {
                    SettingsScreen(
                        prefs = Prefs.get(this@MainActivity),
                        onBack = {
                            showSettings = false
                            try {
                                notifSnapshot = NotificationStore.recent(this@MainActivity, 30)
                            } catch (e: Exception) {
                                // Keep the last snapshot.
                            }
                        },
                        onGrantNotificationAccess = { openNotificationAccess() },
                        onGrantAccessibility = { openAccessibility() },
                        onGrantWriteSettings = { openWriteSettings() },
                        onGrantOverlay = { openOverlay() },
                        onBatteryExemption = { openBattery() },
                        onClearMemory = { Memory.clear(this@MainActivity); JarvisBus.clear() },
                        onRestartService = { restartService() }
                    )
                } else {
                    Dashboard(
                        turns = turns,
                        state = state,
                        notifications = notifSnapshot,
                        prefs = Prefs.get(this@MainActivity),
                        setupWarning = warning,
                        onTalk = { JarvisForegroundService.listenOnce(this@MainActivity) },
                        onSend = { text -> JarvisForegroundService.process(this@MainActivity, text) },
                        onOpenSettings = { showSettings = true },
                        onClear = { JarvisBus.clear() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            NotificationStore.pruneExpired(this)
        } catch (e: Exception) {
            // Best effort on resume.
        }
        try {
            JarvisForegroundService.start(this)
        } catch (e: Exception) {
            // Service start is best-effort here; the user can retry from settings.
        }
    }

    private fun askForRuntimePermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        wanted.add(Manifest.permission.CAMERA)
        try {
            runtimePerms.launch(wanted.toTypedArray())
        } catch (e: Exception) {
            // Permission prompt failed; the settings screen explains manual grants.
        }
    }

    private fun hasMic(): Boolean {
        return try {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun hasNotificationAccess(): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun hasAccessibility(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            enabled.split(':').any { it.contains(packageName) }
        } catch (e: Exception) {
            false
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            // No-op: the settings screen stays visible.
        }
    }

    private fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            // No-op: the settings screen stays visible.
        }
    }

    private fun openWriteSettings() {
        try {
            startActivity(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            )
        } catch (e: Exception) {
            // No-op.
        }
    }

    private fun openOverlay() {
        try {
            startActivity(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            )
        } catch (e: Exception) {
            // No-op.
        }
    }

    private fun openBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            // No-op.
        }
    }

    private fun restartService() {
        try {
            JarvisForegroundService.stop(this)
            JarvisForegroundService.start(this)
        } catch (e: Exception) {
            // Best effort.
        }
    }
}
