package com.jarvis.assistant.notifications

/**
 * Cleans decorative emoji and symbol runs from a notification sender title so
 * JARVIS says "Murphy" instead of narrating "🤪🤪 Murphy †°°°^¥😏". The original
 * title is always kept for display; this is only for spoken output.
 */
object NameNormalizer {
    private val NAME = Regex("[\\p{L}][\\p{L}\\p{M}'’\\-. ]*[\\p{L}\\p{M}]|[\\p{L}]")

    fun normalizeForSpeech(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val m = NAME.find(title) ?: return ""
        return m.value.trim().replace(Regex("\\s+"), " ")
    }

    /** Fallback when normalization is empty or ambiguous. */
    fun safeName(raw: String?, appLabel: String): String {
        val n = normalizeForSpeech(raw)
        return if (n.isBlank()) "a $appLabel contact" else n
    }
}