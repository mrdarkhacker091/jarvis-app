package ai.jarvis.nexus

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Makes Jarvis eligible to be chosen as the device's digital assistant.
 * Android only uses this after the user explicitly selects Jarvis in
 * Settings → Apps → Default apps → Digital assistant, and the manufacturer
 * may not expose that choice at all.
 */
class JarvisAssistantService : VoiceInteractionService()

class JarvisSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JarvisSession(this)
}

class JarvisSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        val intent = android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("jarvis_listen", true)
        context.startActivity(intent)
        hide()
    }
}