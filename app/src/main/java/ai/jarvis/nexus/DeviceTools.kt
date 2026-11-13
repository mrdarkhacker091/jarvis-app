package ai.jarvis.nexus

import android.app.AlarmClock
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Every device capability lives here and is executed through an official Android API.
 * Nothing here reports success unless the platform call actually succeeded.
 */
object DeviceTools {

    data class Result(val ok: Boolean, val message: String)

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun deviceInfo(context: Context): List<Pair<String, String>> {
        val battery = battery(context)
        val storage = storage()
        val connectivity = connectivity(context)
        return listOf(
            "Manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Model" to Build.MODEL,
            "Device" to Build.DEVICE,
            "Android version" to Build.VERSION.RELEASE,
            "SDK level" to Build.VERSION.SDK_INT.toString(),
            "Security patch" to (Build.VERSION.SECURITY_PATCH ?: "not reported"),
            "App version" to "1.0.0",
            "Battery level" to (battery.first ?: "not reported"),
            "Charging" to (battery.second ?: "not reported"),
            "Storage free" to storage.first,
            "Storage total" to storage.second,
            "Network" to connectivity,
            "Location permission" to permissionLabel(context, android.Manifest.permission.ACCESS_FINE_LOCATION),
            "Microphone permission" to permissionLabel(context, android.Manifest.permission.RECORD_AUDIO),
            "Camera permission" to permissionLabel(context, android.Manifest.permission.CAMERA),
        )
    }

    private fun permissionLabel(context: Context, permission: String): String =
        if (has(context, permission)) "granted" else "not granted"

    fun battery(context: Context): Pair<String?, String?> {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null to null
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargingIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = chargingIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temperature = chargingIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = if (temperature > 0) String.format(Locale.US, "%.1f °C", temperature / 10f) else null
        return listOfNotNull(
            level.takeIf { it in 0..100 }?.let { "$it%" },
            (if (charging) "yes · $tempC" else "no · $tempC")
        ).let { it.firstOrNull() to it.getOrNull(1) }
    }

    fun storage(): Pair<String, String> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        val totalGb = stat.totalBytes / 1024.0 / 1024.0 / 1024.0
        return String.format(Locale.US, "%.1f GB", freeGb) to String.format(Locale.US, "%.1f GB", totalGb)
    }

    fun connectivity(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "unavailable"
        val network = manager.activeNetwork ?: return "offline"
        val caps = manager.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "connected"
        }
    }

    fun openApp(context: Context, query: String): Result {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return Result(false, "Which app should I open?")
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager
            .queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != context.packageName }
        val target = matches.firstOrNull { it.loadLabel(context.packageManager).toString().lowercase() == needle }
            ?: matches.firstOrNull { it.loadLabel(context.packageManager).toString().lowercase().contains(needle) }
            ?: matches.firstOrNull { it.activityInfo.packageName.lowercase().contains(needle) }
        if (target == null) {
            return Result(false, "I could not find an installed app matching \"$query\" on this device.")
        }
        return try {
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(target.activityInfo.packageName, target.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            Result(true, "Opened ${target.loadLabel(context.packageManager)}.")
        } catch (error: Exception) {
            Result(false, "Android refused to launch that app: ${error.message}")
        }
    }

    fun installedApps(context: Context): List<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.loadLabel(context.packageManager).toString() }
            .distinct()
            .sorted()
    }

    fun openSetting(context: Context, action: String): Result {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Result(true, "Opened the $action screen.")
        } catch (error: Exception) {
            Result(false, "This device has no handler for $action.")
        }
    }

    fun setFlashlight(context: Context, on: Boolean): Result {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Result(false, "No camera service on this device.")
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Result(false, "This device has no flash unit.")
        return try {
            manager.setTorchMode(id, on)
            Result(true, if (on) "Flashlight is on." else "Flashlight is off.")
        } catch (error: Exception) {
            Result(false, "Android refused the torch command: ${error.message}")
        }
    }

    fun setBrightness(context: Context, percent: Int): Result {
        if (!Settings.System.canWrite(context)) {
            return Result(false, "Writing system settings is not allowed yet. Grant \"Modify system settings\" for Jarvis in Settings and try again.")
        }
        return try {
            val value = (255 * (percent.coerceIn(5, 100) / 100.0)).toInt()
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            Result(true, "Screen brightness set to $percent%.")
        } catch (error: Exception) {
            Result(false, "Could not change brightness: ${error.message}")
        }
    }

    fun startTimer(context: Context, minutes: Int): Result {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes.coerceIn(1, 180) * 60)
            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Result(true, "Timer handed to the clock app — confirm it there.")
        } catch (error: Exception) {
            Result(false, "No clock app accepted the timer.")
        }
    }

    fun location(context: Context): Result {
        if (!has(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            return Result(false, "Location permission is not granted, so I cannot read your position.")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result(false, "No location service on this device.")
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return Result(false, "Location is switched off in system settings.")
        }
        val fix = runCatching {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull() ?: return Result(false, "No recent location fix is available yet. Try again in a moment.")
        val age = (System.currentTimeMillis() - fix.time) / 1000
        return Result(
            true,
            String.format(
                Locale.US,
                "Position %.4f, %.4f · accuracy ±%.0f m · fix age %ds",
                fix.latitude, fix.longitude, fix.accuracy, age
            )
        )
    }

    /**
     * Scoped malware check. Android does not let a normal app scan other apps' private data,
     * so this reports exactly what was inspected and what could not be inspected.
     */
    fun securityScan(context: Context): List<String> {
        val pm = context.packageManager
        val lines = mutableListOf<String>()

        val patch = Build.VERSION.SECURITY_PATCH ?: "not reported"
        lines += "Android security patch level: $patch"

        val risky = mutableListOf<String>()
        val requested = listOf(
            android.Manifest.permission.REQUEST_INSTALL_PACKAGES,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        val installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        installed.forEach { pkg ->
            if (pkg.packageName == context.packageName) return@forEach
            val dangerous = pkg.requestedPermissions.orEmpty().filter { it in requested }
            if (dangerous.size >= 3) {
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg.packageName, 0)).toString() }
                    .getOrDefault(pkg.packageName)
                risky += "$label — ${dangerous.size} high-impact permissions"
            }
        }
        lines += if (risky.isEmpty()) {
            "No app requested three or more high-impact permissions."
        } else {
            "Review these apps: " + risky.take(8).joinToString("; ")
        }

        val unknownSources = runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
        }.getOrDefault(false)
        lines += "Third-party installs (unknown sources) from legacy flag: ${if (unknownSources) "allowed" else "not flagged"}"

        lines += "Play Protect status: not readable by a normal app — check it in Play Store → Play Protect."
        lines += "Limitation: installed app data, memory and network traffic of other apps cannot be inspected by a normal Android app, so this is a permission and configuration review, not an antivirus scan."

        return lines
    }
}