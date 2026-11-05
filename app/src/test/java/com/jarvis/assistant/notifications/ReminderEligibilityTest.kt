package com.jarvis.assistant.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEligibilityTest {
    private val hour = 60L * 60 * 1000

    @Test fun eligibleAfterOneHour() = assertTrue(ReminderEligibility.isEligible("none", hour + 1000, true, false))
    @Test fun notEligibleBeforeOneHour() = assertFalse(ReminderEligibility.isEligible("none", 30 * 60 * 1000, true, false))
    @Test fun notEligibleIfDisabled() = assertFalse(ReminderEligibility.isEligible("none", hour + 1000, false, false))
    @Test fun notEligibleIfReplied() = assertFalse(ReminderEligibility.isEligible("none", hour + 1000, true, true))
    @Test fun notEligibleIfAlreadyFired() = assertFalse(ReminderEligibility.isEligible("fired", hour + 1000, true, false))
    @Test fun notEligibleIfHandled() = assertFalse(ReminderEligibility.isEligible("handled", hour + 1000, true, false))
    @Test fun notEligibleIfMuted() = assertFalse(ReminderEligibility.isEligible("muted", hour + 1000, true, false))
}