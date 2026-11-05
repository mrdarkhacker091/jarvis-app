package com.jarvis.assistant.ai

import com.jarvis.assistant.data.Provider

/**
 * Pure ordering logic, separated so it can be unit-tested without Android.
 * A provider is usable only when enabled and fully configured.
 */
object ProviderOrder {
    fun orderedEnabled(list: List<Provider>): List<Provider> =
        list.filter { it.enabled && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
}