package com.jarvis.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class Provider(
    val id: String,
    val name: String,
    var enabled: Boolean,
    var baseUrl: String,
    var model: String,
    var apiKey: String,
    val nativeGemini: Boolean = false
)

class Prefs private constructor(
    private val sp: SharedPreferences,
    private val secret: SharedPreferences
) {

    var baseUrl: String
        get() = sp.getString(KEY_BASE, "") ?: ""
        set(v) = sp.edit().putString(KEY_BASE, v.trim()).apply()
    var apiKey: String
        get() = secret.getString(KEY_KEY, "") ?: ""
        set(v) = secret.edit().putString(KEY_KEY, v.trim()).apply()
    var model: String
        get() = sp.getString(KEY_MODEL, "") ?: ""
        set(v) = sp.edit().putString(KEY_MODEL, v.trim()).apply()

    var wakeWordEnabled: Boolean
        get() = sp.getBoolean(KEY_WAKE, false)
        set(v) = sp.edit().putBoolean(KEY_WAKE, v).apply()
    var wakePhrase: String
        get() = sp.getString(KEY_PHRASE, "jarvis") ?: "jarvis"
        set(v) = sp.edit().putString(KEY_PHRASE, v.trim().lowercase()).apply()
    var ttsRate: Float
        get() = sp.getFloat(KEY_RATE, 0.95f)
        set(v) = sp.edit().putFloat(KEY_RATE, v).apply()
    var ttsPitch: Float
        get() = sp.getFloat(KEY_PITCH, 0.62f)
        set(v) = sp.edit().putFloat(KEY_PITCH, v).apply()
    var ttsVoice: String
        get() = sp.getString(KEY_VOICE, "") ?: ""
        set(v) = sp.edit().putString(KEY_VOICE, v).apply()
    var announceNotifications: Boolean
        get() = sp.getBoolean(KEY_ANNOUNCE, false)
        set(v) = sp.edit().putBoolean(KEY_ANNOUNCE, v).apply()
    var briefingOnWake: Boolean
        get() = sp.getBoolean(KEY_BRIEF, true)
        set(v) = sp.edit().putBoolean(KEY_BRIEF, v).apply()
    var briefingDetail: String
        get() = sp.getString(KEY_DETAIL, "normal") ?: "normal"
        set(v) = sp.edit().putString(KEY_DETAIL, v).apply()
    var quietStart: Int
        get() = sp.getInt(KEY_QS, 23)
        set(v) = sp.edit().putInt(KEY_QS, v).apply()
    var quietEnd: Int
        get() = sp.getInt(KEY_QE, 7)
        set(v) = sp.edit().putInt(KEY_QE, v).apply()
    var allowedPackages: Set<String>
        get() = sp.getStringSet(KEY_PKGS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_PKGS, v).apply()
    var remindersEnabled: Boolean
        get() = sp.getBoolean(KEY_REM, true)
        set(v) = sp.edit().putBoolean(KEY_REM, v).apply()
    var animationIntensity: Float
        get() = sp.getFloat(KEY_ANIM, 1f)
        set(v) = sp.edit().putFloat(KEY_ANIM, v).apply()
    var confirmDestructive: Boolean
        get() = sp.getBoolean(KEY_CONF, true)
        set(v) = sp.edit().putBoolean(KEY_CONF, v).apply()

    fun providers(): List<Provider> {
        val raw = secret.getString(KEY_PROVIDERS, null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                return (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Provider(
                        o.optString("id"), o.optString("name"), o.optBoolean("enabled"),
                        o.optString("baseUrl"), o.optString("model"), o.optString("apiKey"),
                        o.optBoolean("nativeGemini")
                    )
                }
            } catch (_: Exception) { }
        }
        return defaults()
    }

    fun saveProviders(list: List<Provider>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject()
                .put("id", p.id).put("name", p.name).put("enabled", p.enabled)
                .put("baseUrl", p.baseUrl).put("model", p.model).put("apiKey", p.apiKey)
                .put("nativeGemini", p.nativeGemini))
        }
        secret.edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    fun providerById(id: String): Provider? = providers().firstOrNull { it.id == id }

    fun orderedEnabled(): List<Provider> = providers().filter { it.enabled && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }

    fun eraseKeys() {
        secret.edit().clear().apply()
    }

    fun inQuietHours(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Boolean {
        val s = quietStart; val e = quietEnd
        return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
    }

    fun isPackageAllowed(pkg: String): Boolean {
        val allowed = allowedPackages
        return allowed.isEmpty() || allowed.contains(pkg)
    }

    private fun defaults(): List<Provider> = listOf(
        Provider("agnes", "Agnes", true, "", "agnes-2.0-flash", ""),
        Provider("gemini", "Gemini", true, "https://generativelanguage.googleapis.com/v1beta", "models/gemini-3.6-flash", "", nativeGemini = true),
        Provider("groq", "Groq", true, "https://api.groq.com/openai/v1", "qwen/qwen3.8-27b", ""),
        Provider("openrouter", "OpenRouter", true, "https://openrouter.ai/api/v1", "anthropic/claude-opus-5.5", ""),
        Provider("agentrouter", "Agent Router", true, "", "claude-opus-5", "")
    )

    companion object {
        const val DEFAULT_BASE = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"

        private const val KEY_BASE = "baseUrl"
        private const val KEY_KEY = "apiKey"
        private const val KEY_MODEL = "model"
        private const val KEY_WAKE = "wakeWordEnabled"
        private const val KEY_PHRASE = "wakePhrase"
        private const val KEY_RATE = "ttsRate"
        private const val KEY_PITCH = "ttsPitch"
        private const val KEY_VOICE = "ttsVoice"
        private const val KEY_ANNOUNCE = "announceNotifications"
        private const val KEY_BRIEF = "briefingOnWake"
        private const val KEY_DETAIL = "briefingDetail"
        private const val KEY_QS = "quietStart"
        private const val KEY_QE = "quietEnd"
        private const val KEY_PKGS = "allowedPackages"
        private const val KEY_REM = "remindersEnabled"
        private const val KEY_ANIM = "animationIntensity"
        private const val KEY_CONF = "confirmDestructive"
        private const val KEY_PROVIDERS = "providers"

        @Volatile private var inst: Prefs? = null

        fun init(ctx: Context) {
            if (inst == null) {
                synchronized(Prefs::class.java) {
                    if (inst == null) {
                        val app = ctx.applicationContext
                        val secret = try {
                            val master = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                            EncryptedSharedPreferences.create(app, "jarvis_secrets", master,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                        } catch (_: Throwable) {
                            app.getSharedPreferences("jarvis_secrets_fallback", Context.MODE_PRIVATE)
                        }
                        inst = Prefs(app.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE), secret)
                    }
                }
            }
        }

        fun get(ctx: Context): Prefs {
            init(ctx); return inst!!
        }
    }
}