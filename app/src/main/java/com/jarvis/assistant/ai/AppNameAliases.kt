package com.jarvis.assistant.ai

/** Pure spoken-name -> installed-app alias lookup, separated for unit testing. */
object AppNameAliases {
    val MAP: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("whatsapp", "com.whatsapp"),
        "chrome" to listOf("chrome", "com.android.chrome", "com.chrome"),
        "telegram" to listOf("telegram", "org.telegram.messenger"),
        "settings" to listOf("settings", "com.android.settings"),
        "camera" to listOf("camera", "com.android.camera"),
        "phone" to listOf("phone", "dialer", "com.android.dialer"),
        "messages" to listOf("messages", "messaging", "com.google.android.apps.messaging"),
        "youtube" to listOf("youtube", "com.google.android.youtube"),
        "gmail" to listOf("gmail", "com.google.android.gm"),
        "maps" to listOf("maps", "com.google.android.apps.maps")
    )

    fun aliases(query: String): List<String> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()
        return MAP[q] ?: MAP.entries.firstOrNull { q.contains(it.key) }?.value ?: emptyList()
    }
}