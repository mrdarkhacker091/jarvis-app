package com.jarvis.assistant.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupTest {
    @Test fun sameRecordMatches() {
        assertTrue(NotificationDedup.sameRecord("com.whatsapp", "Murphy", "hi", "com.whatsapp", "Murphy", "hi"))
    }
    @Test fun differentTextIsNotSame() {
        assertFalse(NotificationDedup.sameRecord("com.whatsapp", "Murphy", "hi", "com.whatsapp", "Murphy", "hey"))
    }
    @Test fun staleAfterCutoff() {
        val now = 1_000_000_000L
        assertTrue(NotificationDedup.isStale(now - 25L * 60 * 60 * 1000, now))
        assertFalse(NotificationDedup.isStale(now - 60 * 1000, now))
    }
}