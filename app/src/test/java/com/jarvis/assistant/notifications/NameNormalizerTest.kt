package com.jarvis.assistant.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizerTest {
    @Test fun stripsEmojiAndDecorativeSymbols() =
        assertEquals("Murphy", NameNormalizer.normalizeForSpeech("🤪🤪 Murphy †°°°^¥😏"))

    @Test fun keepsAccentedNames() =
        assertEquals("Müller", NameNormalizer.normalizeForSpeech("Müller ❤️"))

    @Test fun keepsInternalPunctuation() =
        assertEquals("John O'Brien", NameNormalizer.normalizeForSpeech("John O'Brien"))

    @Test fun allEmojiIsEmpty() =
        assertEquals("", NameNormalizer.normalizeForSpeech("🤪🤪 😏"))

    @Test fun emptyTitleIsEmpty() =
        assertEquals("", NameNormalizer.normalizeForSpeech(""))

    @Test fun safeNameFallsBackToAppLabel() =
        assertEquals("a WhatsApp contact", NameNormalizer.safeName("🤪", "WhatsApp"))
}