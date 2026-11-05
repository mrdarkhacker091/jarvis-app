package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.SpeechRecognizer
import com.jarvis.assistant.JarvisBus
import com.jarvis.assistant.JarvisState
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.JarvisBrain
import com.jarvis.assistant.ai.LocalCommandRouter
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.notifications.BriefingBuilder
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisForegroundService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private var tts: TtsManager? = null
    private var recognizer: SpeechRecognizerManager? = null
    private var speaking = false
    private var processing = false
    private var briefingGiven = false
    private var audio: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ensureChannel()
        goForeground(getString(R.string.service_running))
        tts = TtsManager(this).also { e ->
            e.onStart = { JarvisBus.setState(JarvisState.SPEAKING); speaking = true }
            e.onDone = {
                main.post {
                    speaking = false
                    JarvisBus.setState(JarvisState.IDLE)
                    // Return to wake listening only after speech fully ends, and
                    // never while the user is mid-command (no feedback loop).
                    if (prefs.wakeWordEnabled) startWakeLoop()
                }
            }
        }
        recognizer = SpeechRecognizerManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN_ONCE -> listenOnce()
            ACTION_PROCESS -> intent.getStringExtra(EXTRA_TEXT)?.let { if (it.isNotBlank()) handleText(it) }
            ACTION_PAUSE -> { JarvisBus.setState(JarvisState.PAUSED); recognizer?.cancel(); tts?.stop(); goForeground(getString(R.string.service_muted)) }
            ACTION_RESUME -> { JarvisBus.setState(JarvisState.IDLE); goForeground(getString(R.string.service_running)); startWakeLoop() }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> { if (prefs.wakeWordEnabled) startWakeLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recognizer?.destroy(); tts?.shutdown(); scope.cancel(); super.onDestroy()
    }

    private fun listenOnce() {
        if (speaking) { tts?.stop(); speaking = false }
        JarvisBus.setState(JarvisState.LISTENING)
        listenForCommand(manual = true)
    }

    private fun startWakeLoop() {
        if (processing || speaking) return
        JarvisBus.setState(JarvisState.LISTENING)
        JarvisBus.setSubtitle("Listening for \"${prefs.wakePhrase}\"")
        listenForWake()
    }

    private fun listenForWake() {
        val rec = recognizer ?: return
        rec.start(object : SpeechRecognizerManager.Listener {
            override fun onReady() { }
            override fun onPartial(text: String) { /* never act on partial */ }
            override fun onFinal(text: String) {
                val phrase = prefs.wakePhrase
                val low = text.lowercase()
                if (phrase.isBlank() || low.contains(phrase)) {
                    val rest = low.substringAfter(phrase).removePrefix(",").removePrefix(" ").trim()
                    JarvisBus.setState(JarvisState.WAKE_DETECTED)
                    if (rest.isNotBlank()) handleText(rest) else onWakeOnly()
                } else {
                    // Not the wake phrase — ignore and keep listening.
                    startWakeLoop()
                }
            }
            override fun onError(code: Int) {
                JarvisBus.setState(JarvisState.IDLE)
                if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                    JarvisBus.add("jarvis", "I need microphone permission to hear you.")
                main.postDelayed({ if (prefs.wakeWordEnabled && !speaking && !processing) startWakeLoop() }, 1200)
            }
        })
    }

    private fun onWakeOnly() {
        if (prefs.briefingOnWake) {
            val briefing = BriefingBuilder.build(this, prefs.briefingDetail)
            briefingGiven = true
            speak(briefing)
        } else {
            speak("Yes?")
        }
    }

    private fun listenForCommand(manual: Boolean) {
        val rec = recognizer ?: return
        // Command capture may transiently take audio focus so the mic hears the
        // user clearly; passive wake listening never requests focus, so music
        // is not interrupted while idle.
        requestTransientFocus()
        rec.start(object : SpeechRecognizerManager.Listener {
            override fun onReady() { JarvisBus.setState(JarvisState.LISTENING) }
            override fun onPartial(text: String) { JarvisBus.setSubtitle(text) }
            override fun onFinal(text: String) { handleText(text) }
            override fun onError(code: Int) {
                abandonFocus()
                JarvisBus.setState(JarvisState.IDLE)
                if (code == SpeechRecognizer.ERROR_NO_MATCH) speak("I did not catch that. Could you say it again?")
                if (manual && prefs.wakeWordEnabled) startWakeLoop()
            }
        })
    }

    private fun handleText(text: String) {
        processing = true
        JarvisBus.add("user", text)
        JarvisBus.setState(JarvisState.THINKING)
        JarvisBus.setSubtitle("Processing")
        scope.launch {
            // Local commands (time, battery, open app) never call an API.
            val local = LocalCommandRouter.tryLocal(this@JarvisForegroundService, text)
            if (local != null) {
                finishTurn(local)
                return@launch
            }
            val reply = withContext(Dispatchers.IO) {
                JarvisBrain.respond(this@JarvisForegroundService, text)
            }
            finishTurn(reply)
        }
    }

    private fun finishTurn(reply: String) {
        processing = false
        abandonFocus()
        JarvisBus.add("jarvis", reply)
        speak(reply)
        if (!briefingGiven) briefingGiven = false
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        JarvisBus.setState(JarvisState.SPEAKING)
        speaking = true
        // Pause wake listening while speaking so JARVIS does not hear itself.
        recognizer?.cancel()
        tts?.speak(text)
    }

    private fun requestTransientFocus() {
        val am = audio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION") am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        val am = audio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL) == null)
                mgr.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel_service), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun goForeground(text: String) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL).setContentTitle(getString(R.string.app_name))
                .setContentText(text).setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentIntent(open).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name)).setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_jarvis).setContentIntent(open).setOngoing(true).build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        const val CHANNEL = "jarvis_service"
        const val NOTIF_ID = 901
        const val ACTION_LISTEN_ONCE = "com.jarvis.action.LISTEN_ONCE"
        const val ACTION_PROCESS = "com.jarvis.action.PROCESS"
        const val ACTION_PAUSE = "com.jarvis.action.PAUSE"
        const val ACTION_RESUME = "com.jarvis.action.RESUME"
        const val ACTION_STOP = "com.jarvis.action.STOP"
        const val EXTRA_TEXT = "text"

        fun start(ctx: Context) {
            val i = Intent(ctx, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}