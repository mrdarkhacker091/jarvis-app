package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.jarvis.assistant.ai.Briefing
import com.jarvis.assistant.ai.JarvisBrain
import com.jarvis.assistant.data.Prefs
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
    private var listeningForCommand = false
    private var speaking = false
    private var paused = false
    private var busy = false
    private var audioManager: AudioManager? = null
    private var previousMusicVolume = -1

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        audioManager = try {
            getSystemService(Context.AUDIO_SERVICE) as AudioManager
        } catch (e: Exception) {
            null
        }
        ensureChannel()
        goForeground(getString(R.string.app_name) + " is running")
        tts = TtsManager(this).also { engine ->
            engine.onStart = {
                JarvisBus.setState(JarvisState.SPEAKING)
            }
            engine.onDone = {
                main.post {
                    speaking = false
                    abandonAudioFocus()
                    JarvisBus.setState(JarvisState.IDLE)
                    if (prefs.wakeWordEnabled && prefs.handsFreeEnabled && !paused) {
                        startWakeLoop()
                    }
                }
            }
        }
        tts?.setRate(prefs.ttsRate)
        recognizer = SpeechRecognizerManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN_ONCE -> listenOnce()
            ACTION_PROCESS -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    handleText(text)
                }
            }
            ACTION_STOP -> {
                paused = true
                recognizer?.cancel()
                tts?.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                recognizer?.cancel()
                tts?.stop()
                speaking = false
                JarvisBus.setState(JarvisState.IDLE)
                goForeground(getString(R.string.app_name) + " is paused")
                return START_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                goForeground(getString(R.string.app_name) + " is running")
                if (prefs.wakeWordEnabled && prefs.handsFreeEnabled) {
                    startWakeLoop()
                }
                return START_STICKY
            }
            else -> {
                paused = false
                if (prefs.wakeWordEnabled && prefs.handsFreeEnabled) {
                    startWakeLoop()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // Best effort during teardown.
        }
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Best effort during teardown.
        }
        abandonAudioFocus()
        scope.cancel()
        super.onDestroy()
    }

    fun startHandsFree() {
        paused = false
        if (prefs.wakeWordEnabled && prefs.handsFreeEnabled) {
            startWakeLoop()
        }
    }

    fun stopHandsFree() {
        recognizer?.cancel()
        JarvisBus.setState(JarvisState.IDLE)
    }

    private fun listenOnce() {
        // TTS-during-listen mute: never let the recognizer hear our own voice.
        if (speaking) {
            tts?.stop()
            speaking = false
            abandonAudioFocus()
        }
        listeningForCommand = true
        JarvisBus.setState(JarvisState.LISTENING)
        listenForOneTurn()
    }

    private fun startWakeLoop() {
        if (busy || speaking || paused) {
            return
        }
        if (!prefs.wakeWordEnabled || !prefs.handsFreeEnabled) {
            return
        }
        listeningForCommand = false
        JarvisBus.setState(JarvisState.LISTENING)
        listenForWake()
    }

    private fun listenForOneTurn() {
        val rec = recognizer ?: return
        rec.start(object : SpeechRecognizerManager.Listener {
            override fun onReady() {
                JarvisBus.setState(JarvisState.LISTENING)
            }

            override fun onPartial(text: String) {
                // Command capture waits for the FINAL result only; partials are ignored.
            }

            override fun onFinal(text: String) {
                val cleaned = stripWake(text)
                if (cleaned.isBlank()) {
                    JarvisBus.setState(JarvisState.IDLE)
                    if (prefs.wakeWordEnabled && prefs.handsFreeEnabled && !paused) {
                        startWakeLoop()
                    }
                } else {
                    handleText(cleaned)
                }
            }

            override fun onError(code: Int) {
                JarvisBus.setState(JarvisState.IDLE)
                if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    JarvisBus.add("jarvis", "I need microphone permission to hear you.")
                }
            }
        })
    }

    private fun listenForWake() {
        val rec = recognizer ?: return
        rec.start(object : SpeechRecognizerManager.Listener {
            override fun onReady() {
                JarvisBus.setState(JarvisState.LISTENING)
            }

            override fun onPartial(text: String) {
                if (speaking || busy) {
                    return
                }
                if (containsWake(text) && !listeningForCommand) {
                    val rest = stripWake(text)
                    if (rest.isNotBlank() && rest.split(" ").size > 1) {
                        rec.cancel()
                        listeningForCommand = false
                        handleText(rest)
                    }
                }
            }

            override fun onFinal(text: String) {
                if (busy || speaking) {
                    return
                }
                val phrase = prefs.wakePhrase
                val lower = text.lowercase()
                if (lower.contains(phrase)) {
                    val rest = stripWake(text)
                    if (rest.isBlank()) {
                        // Bare wake: greet with the real briefing, once per window.
                        listeningForCommand = false
                        handleWakeBriefing()
                    } else {
                        listeningForCommand = false
                        handleText(rest)
                    }
                } else {
                    main.postDelayed({
                        if (!busy && !speaking && !paused &&
                            prefs.wakeWordEnabled && prefs.handsFreeEnabled
                        ) {
                            listenForWake()
                        }
                    }, 500)
                }
            }

            override fun onError(code: Int) {
                if (busy) {
                    return
                }
                val retry = when (code) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> -1L
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> 900L
                    else -> 1500L
                }
                if (retry > 0 && prefs.wakeWordEnabled && prefs.handsFreeEnabled && !paused) {
                    main.postDelayed({
                        if (!busy && !speaking && !paused) {
                            listenForWake()
                        }
                    }, retry)
                } else {
                    JarvisBus.setState(JarvisState.IDLE)
                }
            }
        })
    }

    private fun handleWakeBriefing() {
        if (busy) {
            return
        }
        busy = true
        recognizer?.cancel()
        JarvisBus.add("user", prefs.wakePhrase)
        JarvisBus.setState(JarvisState.THINKING)
        scope.launch {
            val reply = withContext(Dispatchers.IO) {
                try {
                    Briefing.buildWakeBriefing(this@JarvisForegroundService)
                } catch (e: Exception) {
                    "Hello. I could not read your notifications just now. Tell me if you need something else."
                }
            }
            busy = false
            JarvisBus.markBriefingGiven()
            JarvisBus.add("jarvis", reply)
            speak(reply)
        }
    }

    private fun containsWake(text: String): Boolean {
        return try {
            text.lowercase().contains(prefs.wakePhrase)
        } catch (e: Exception) {
            false
        }
    }

    private fun stripWake(text: String): String {
        val lower = text.lowercase()
        val phrase = prefs.wakePhrase
        val idx = lower.indexOf(phrase)
        val out = if (idx >= 0) {
            text.substring(idx + phrase.length)
        } else {
            text
        }
        return out.trim().trim(',', '.', '!', '?', ':', '-', ' ').trim()
    }

    private fun handleText(text: String) {
        if (busy) {
            return
        }
        busy = true
        listeningForCommand = false
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // Best effort before answering.
        }
        JarvisBus.add("user", text)
        JarvisBus.setState(JarvisState.THINKING)
        scope.launch {
            val reply = withContext(Dispatchers.IO) {
                try {
                    JarvisBrain.respond(this@JarvisForegroundService, text)
                } catch (e: Exception) {
                    "Something went wrong on my side."
                }
            }
            busy = false
            JarvisBus.add("jarvis", reply)
            speak(reply)
        }
    }

    private fun speak(text: String) {
        // Mute the mic path while we talk so the recognizer never captures TTS.
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // Best effort.
        }
        requestAudioFocus()
        speaking = true
        JarvisBus.setState(JarvisState.SPEAKING)
        try {
            tts?.speak(text) ?: run {
                speaking = false
                abandonAudioFocus()
                JarvisBus.setState(JarvisState.IDLE)
            }
        } catch (e: Exception) {
            speaking = false
            abandonAudioFocus()
            JarvisBus.setState(JarvisState.IDLE)
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            previousMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focus = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.requestAudioFocus(focus)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            // Duck our own listen path: pause recognition while TTS plays.
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                // Best effort.
            }
        } catch (e: Exception) {
            // Audio focus is best-effort; TTS still works without it.
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focus = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.abandonAudioFocusRequest(focus)
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            // Best effort.
        } finally {
            previousMusicVolume = -1
        }
    }

    private fun goForeground(text: String) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val listen = PendingIntent.getService(
            this, 1, Intent(this, JarvisForegroundService::class.java).setAction(ACTION_LISTEN_ONCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, JarvisForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("JARVIS")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_jarvis),
                        "Talk",
                        listen
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_jarvis),
                        "Stop",
                        stop
                    ).build()
                )
                .build()
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
                .setContentTitle("JARVIS")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL) != null) {
            return
        }
        val ch = NotificationChannel(CHANNEL, getString(R.string.notification_channel_service), NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        mgr.createNotificationChannel(ch)
    }

    companion object {
        const val ACTION_LISTEN_ONCE = "com.jarvis.assistant.action.LISTEN_ONCE"
        const val ACTION_PROCESS = "com.jarvis.assistant.action.PROCESS"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP"
        const val ACTION_PAUSE = "com.jarvis.assistant.action.PAUSE"
        const val ACTION_RESUME = "com.jarvis.assistant.action.RESUME"
        const val EXTRA_TEXT = "extra_text"
        private const val CHANNEL = "jarvis_service"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            val i = Intent(ctx, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun listenOnce(ctx: Context) {
            val i = Intent(ctx, JarvisForegroundService::class.java).setAction(ACTION_LISTEN_ONCE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun process(ctx: Context, text: String) {
            val i = Intent(ctx, JarvisForegroundService::class.java)
                .setAction(ACTION_PROCESS).putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun pause(ctx: Context) {
            val i = Intent(ctx, JarvisForegroundService::class.java).setAction(ACTION_PAUSE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun resume(ctx: Context) {
            val i = Intent(ctx, JarvisForegroundService::class.java).setAction(ACTION_RESUME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, JarvisForegroundService::class.java))
            } catch (e: Exception) {
                // Best effort.
            }
        }
    }
}
