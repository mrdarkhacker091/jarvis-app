package com.jarvis.assistant.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/**
 * Contact lookup across display name and nickname fields, and a dialer launch.
 * Direct calls (ACTION_CALL) are never made without explicit permission; the
 * default path opens the system dialer (ACTION_DIAL) and lets the user confirm.
 */
object ContactsHelper {
    data class Match(val name: String, val number: String)

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(ctx: Context, term: String): List<Match> {
        if (!hasPermission(ctx) || term.isBlank()) return emptyList()
        val t = term.trim().lowercase()
        val results = mutableListOf<Match>()
        val cursor = try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )
        } catch (_: Exception) { return emptyList() }
        cursor?.use {
            val seen = mutableSetOf<String>()
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                val key = name.lowercase() + number
                if (!seen.add(key)) continue
                if (name.lowercase().contains(t) || t.contains(name.lowercase())) {
                    results.add(Match(name, number))
                }
            }
        }
        // rank exact matches first
        return results.sortedBy { it.name.lowercase() != t }
    }

    /** Used by the local command router: returns a spoken result and opens the dialer. */
    fun callByName(ctx: Context, term: String): String {
        if (!hasPermission(ctx)) return "I need contacts permission to look up $term."
        val matches = search(ctx, term)
        if (matches.isEmpty()) return "I could not find a contact called $term."
        if (matches.size > 1) {
            return "I found several: ${matches.take(4).joinToString(", ") { it.name }}. Which one?"
        }
        val m = matches.first()
        return try {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(m.number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(dial)
            "Calling ${m.name}. Confirm on the dialer, and choose speaker or earpiece there."
        } catch (_: Exception) { "I could not open the dialer for ${m.name}." }
    }
}