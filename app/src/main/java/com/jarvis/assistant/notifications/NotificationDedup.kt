package com.jarvis.assistant.notifications

/** Pure dedup/staleness helpers, separated for unit testing. */
object NotificationDedup {
    fun sameRecord(pkgA: String, senderA: String?, textA: String,
                   pkgB: String, senderB: String?, textB: String): Boolean =
        pkgA == pkgB && senderA == senderB && textA == textB

    fun isStale(at: Long, now: Long, staleMs: Long = 24L * 60 * 60 * 1000): Boolean =
        now - at > staleMs
}