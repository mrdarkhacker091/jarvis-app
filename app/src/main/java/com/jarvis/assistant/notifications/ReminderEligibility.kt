package com.jarvis.assistant.notifications

/** Pure eligibility for the one-hour unanswered-message reminder. */
object ReminderEligibility {
    private const val HOUR_MS = 60L * 60 * 1000

    fun isEligible(reminderState: String, ageMs: Long, enabled: Boolean, replied: Boolean): Boolean {
        if (!enabled || replied) return false
        if (reminderState == "fired" || reminderState == "handled" || reminderState == "muted") return false
        return ageMs >= HOUR_MS
    }
}