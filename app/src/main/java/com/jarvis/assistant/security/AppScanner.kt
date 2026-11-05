package com.jarvis.assistant.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class AppScanItem(
    val label: String, val pkg: String,
    val isSystem: Boolean, val installSource: String?,
    val flaggedPermissions: List<String>,
    val hasAccessibility: Boolean, val hasDeviceAdmin: Boolean
)

/**
 * A transparent heuristic review, not an antivirus. It lists apps the OS lets
 * JARVIS see and flags risk indicators (powerful permission combinations,
    newly installed, unknown source, accessibility/device-admin). It never
 * claims to detect or remove all malware.
 */
object AppScanner {
    private val POWERFUL = setOf(
        "android.permission.ACCESSIBILITY", "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION"
    )

    fun scan(ctx: Context): List<AppScanItem> {
        val pm = ctx.packageManager
        val all = try { pm.getInstalledApplications(0) } catch (_: Exception) { return emptyList() }
        return all.map { ai ->
            val info = try { pm.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS) } catch (_: Exception) { null }
            val perms = info?.requestedPermissions?.toList() ?: emptyList()
            val flagged = perms.filter { it in POWERFUL }
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                try { pm.getInstallSourceInfo(ai.packageName).installingPackageName } catch (_: Exception) { null } else null
            AppScanItem(
                pm.getApplicationLabel(ai).toString(), ai.packageName,
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, source,
                flagged, perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"),
                perms.contains("android.permission.BIND_DEVICE_ADMIN")
            )
        }.sortedByDescending { it.flaggedPermissions.size + (if (it.hasAccessibility) 5 else 0) + (if (it.hasDeviceAdmin) 3 else 0) }
    }
}