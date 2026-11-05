package com.jarvis.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNameAliasTest {
    @Test fun directAlias() =
        assertEquals(listOf("whatsapp", "com.whatsapp"), AppNameAliases.aliases("whatsapp"))

    @Test fun partialQueryMatchesContained() =
        assertEquals(listOf("telegram", "org.telegram.messenger"), AppNameAliases.aliases("open telegram"))

    @Test fun unknownIsEmpty() = assertTrue(AppNameAliases.aliases("nothinglikeit").isEmpty())

    @Test fun blankIsEmpty() = assertTrue(AppNameAliases.aliases("").isEmpty())
}