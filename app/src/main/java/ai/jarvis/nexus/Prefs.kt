package ai.jarvis.nexus

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "jarvis"
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun geminiKey1(c: Context) = sp(c).getString("gemini1", "") ?: ""
    fun geminiKey2(c: Context) = sp(c).getString("gemini2", "") ?: ""
    fun geminiKey3(c: Context) = sp(c).getString("gemini3", "") ?: ""
    
    fun setGeminiKey1(c: Context, v: String) = sp(c).edit().putString("gemini1", v.trim()).apply()
    fun setGeminiKey2(c: Context, v: String) = sp(c).edit().putString("gemini2", v.trim()).apply()
    fun setGeminiKey3(c: Context, v: String) = sp(c).edit().putString("gemini3", v.trim()).apply()
}
