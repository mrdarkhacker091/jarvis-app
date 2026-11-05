package com.jarvis.assistant.ai

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Resolves installed, launchable apps by spoken name with sensible aliases.
 * Case-insensitive, matches labels and package names, ranks exact over partial.
 */
object AppResolver {
    fun resolve(pm: PackageManager, spoken: String): List<AppMatch> {
        val q = spoken.lowercase().trim()
        if (q.isBlank()) return emptyList()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val all = try { pm.queryIntentActivities(intent, 0) } catch (_: Exception) { return emptyList() }
        val candidates = mutableListOf<AppMatch>()
        val seen = mutableSetOf<String>()
        for (ri in all) {
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            candidates.add(AppMatch(label, pkg))
        }
        val alias = AppNameAliases.aliases(q)
        if (alias.isNotEmpty()) {
            val am = candidates.filter { c -> alias.any { c.label.lowercase().contains(it) || c.pkg.lowercase().contains(it) } }
            if (am.isNotEmpty()) return am
        }
        val exact = candidates.filter { it.label.lowercase() == q || it.pkg.lowercase().contains(q) }
        if (exact.isNotEmpty()) return exact
        val partial = candidates.filter { it.label.lowercase().contains(q) || q.contains(it.label.lowercase()) }
        return partial
    }
}