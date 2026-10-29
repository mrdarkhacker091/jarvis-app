package com.jarvis.assistant.util

/**
 * Normalizes notification sender titles for speech output.
 *
 * Keeps the original title for display; this helper only produces a
 * safe spoken form by stripping leading/trailing emoji and decorative
 * symbol runs and collapsing whitespace. Letters (including accented),
 * digits and meaningful internal punctuation are preserved.
 *
 * Pure function with no Android dependencies so it can be unit tested
 * on the JVM.
 */
object NameNormalize {

    private const val FALLBACK_GENERIC = "a contact"

    fun spokenName(raw: String?): String {
        if (raw == null) {
            return ""
        }
        var s = raw.trim()
        if (s.isEmpty()) {
            return ""
        }
        s = s.replace(Regex("\\s+"), " ")
        val cps = s.codePoints().toArray()
        var start = 0
        var end = cps.size
        // Strip leading decoration, but keep ONE balanced opening bracket
        // when it matches a closing bracket later (e.g. "(work) Mary").
        while (start < end && isDecorative(cps[start])) {
            val cp = cps[start]
            if (isOpener(cp) && hasMatchingCloser(cps, start, end)) {
                break
            }
            start++
        }
        // Strip trailing decoration, but keep ONE balanced closing bracket
        // when it matches an opening bracket inside (e.g. "Mary (work)").
        while (end > start && isDecorative(cps[end - 1])) {
            val cp = cps[end - 1]
            if (isCloser(cp) && hasMatchingOpener(cps, start, end - 1)) {
                break
            }
            end--
        }
        if (start >= end) {
            return ""
        }
        val out = String(cps, start, end - start).replace(Regex("\\s+"), " ").trim()
        if (out.isEmpty()) {
            return ""
        }
        // Guard against a result that is only punctuation/digits soup
        // with no letter at all (e.g. "-- .. --").
        var hasLetter = false
        val cpsOut = out.codePoints().toArray()
        for (cp in cpsOut) {
            if (Character.isLetter(cp)) {
                hasLetter = true
                break
            }
        }
        if (!hasLetter) {
            // Pure digit strings (short numbers) are still speakable.
            return if (out.all { ch -> ch.isDigit() || ch.isWhitespace() }) {
                out
            } else {
                ""
            }
        }
        return out
    }

    fun spokenOrFallback(raw: String?, appLabel: String?): String {
        val clean = spokenName(raw)
        if (clean.isNotEmpty()) {
            return clean
        }
        val app = appLabel?.trim().orEmpty()
        return if (app.isNotEmpty()) {
            "a $app contact"
        } else {
            FALLBACK_GENERIC
        }
    }

    private fun isNameChar(cp: Int): Boolean {
        return Character.isLetterOrDigit(cp)
    }

    private fun isDecorative(cp: Int): Boolean {
        if (isNameChar(cp)) {
            return false
        }
        // Whitespace and the common emoji/symbol ranges are decoration.
        if (Character.isWhitespace(cp)) {
            return true
        }
        val block = Character.UnicodeBlock.of(cp)
        if (block == Character.UnicodeBlock.EMOTICONS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.DINGBATS ||
            block == Character.UnicodeBlock.ENCLOSED_ALPHANUMERICS ||
            block == Character.UnicodeBlock.ENCLOSED_ALPHANUMERIC_SUPPLEMENT ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_ARROWS ||
            block == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS
        ) {
            return true
        }
        // Skip modifiers/variation selectors/combining marks at the edges.
        val type: Int = Character.getType(cp)
        if (type == Character.MODIFIER_SYMBOL.toInt() ||
            type == Character.OTHER_SYMBOL.toInt() ||
            type == Character.FORMAT.toInt() ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
        ) {
            return true
        }
        // Other punctuation at the very edge (daggers, stars, tildes...)
        // is decoration; internal punctuation is handled by keeping the
        // middle of the string intact.
        return true
    }

    private fun isOpener(cp: Int): Boolean {
        return cp == '('.code || cp == '['.code || cp == '{'.code
    }

    private fun isCloser(cp: Int): Boolean {
        return cp == ')'.code || cp == ']'.code || cp == '}'.code
    }

    private fun hasMatchingCloser(cps: IntArray, openerAt: Int, end: Int): Boolean {
        val want = when (cps[openerAt]) {
            '('.code -> ')'.code
            '['.code -> ']'.code
            '{'.code -> '}'.code
            else -> return false
        }
        for (i in openerAt + 1 until end) {
            if (cps[i] == want) {
                return true
            }
        }
        return false
    }

    private fun hasMatchingOpener(cps: IntArray, from: Int, closerAt: Int): Boolean {
        val closer = cps[closerAt]
        val want = when (closer) {
            ')'.code -> '('.code
            ']'.code -> '['.code
            '}'.code -> '{'.code
            else -> return false
        }
        for (i in from until closerAt) {
            if (cps[i] == want) {
                return true
            }
        }
        return false
    }
}
