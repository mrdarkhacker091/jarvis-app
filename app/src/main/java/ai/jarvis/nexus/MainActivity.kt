package ai.jarvis.nexus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.launch

private val CYAN = Color(0xFF00F3FF)
private val INK = Color(0xFF000508)
private val PANEL = Color(0xFF03121A)

private data class Turn(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listen = intent?.getBooleanExtra("jarvis_listen", false) ?: false
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = CYAN, background = INK, surface = PANEL)) {
                Surface(color = INK, modifier = Modifier.fillMaxSize()) {
                    JarvisScreen(startListening = listen)
                }
            }
        }
    }
}

class SecureStore(context: Context) {
    private val prefs = runCatching {
        EncryptedSharedPreferences.create(
            "jarvis_secure",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("jarvis_fallback", Context.MODE_PRIVATE) }

    fun token(): String = prefs.getString("api_key", "").orEmpty()
    fun saveToken(value: String) {
        prefs.edit().putString("api_key", value).apply()
    }
}

@Composable
fun JarvisScreen(startListening: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SecureStore(context) }

    val turns = remember { mutableStateListOf(Turn("assistant", "Hello. I'm Jarvis, your personal AI assistant. Ask me anything, or use the device buttons below.")) }
    val log = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    var appQuery by remember { mutableStateOf("WhatsApp") }
    var busy by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("CHAT") }
    var status by remember { mutableStateOf("Ready") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf(store.token()) }
    var tokenDraft by remember { mutableStateOf(store.token()) }
    var spoken by remember { mutableStateOf(true) }
    var deviceInfo by remember { mutableStateOf(DeviceTools.deviceInfo(context)) }

    val sendRef = remember { mutableStateOf<(String) -> Unit>({}) }
    val voice = remember {
        VoiceIo(
            context,
            onResult = { text -> sendRef.value(text) },
            onState = { message -> status = message },
        )
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val denied = granted.filterValues { !it }.keys
        status = if (denied.isEmpty()) "Permissions granted." else "Declined: ${denied.joinToString { it.substringAfterLast('.') }}"
        deviceInfo = DeviceTools.deviceInfo(context)
    }

    fun askForPermissions() {
        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy) return
        if (token.isBlank()) {
            section = "SETTINGS"
            status = "Add your Jarvis API key first (Settings tab)."
            return
        }
        turns.add(Turn("user", message))
        input = ""
        busy = true
        status = "Contacting Jarvis core…"
        scope.launch {
            try {
                val result = JarvisApi.chat(token, message, conversationId)
                conversationId = result.conversationId
                turns.add(Turn("assistant", result.reply))
                status = "Answered"
                if (spoken) voice.say(result.reply)
            } catch (error: Exception) {
                turns.add(Turn("error", error.message ?: "The Jarvis core did not answer."))
                status = "Failed — your message is still in the box above."
            } finally {
                busy = false
            }
        }
    }

    sendRef.value = { text -> send(text) }

    LaunchedEffect(Unit) {
        voice.init()
        if (startListening) {
            askForPermissions()
            voice.listen()
        }
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("JARVIS AI", color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        Text("NEXUS CORE · CREATED BY MR DARK HACKER", color = Color(0xFF7FD8E8), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(status, color = Color(0xFF7FD8E8), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("CHAT", "DEVICE", "ACTIONS", "SETTINGS").forEach { name ->
                Text(
                    name,
                    color = if (section == name) INK else CYAN,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(if (section == name) CYAN else PANEL)
                        .border(1.dp, CYAN.copy(alpha = 0.5f))
                        .clickable { section = name }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }

        when (section) {
            "CHAT" -> {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(turns) { turn ->
                        val tint = when (turn.role) {
                            "user" -> Color(0xFF9BE7FF)
                            "error" -> Color(0xFFFF9E64)
                            else -> Color(0xFFCFFFFA)
                        }
                        Text(
                            if (turn.role == "user") "you" else if (turn.role == "error") "system" else "jarvis",
                            color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                        )
                        Text(turn.text, color = tint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Jarvis…", color = Color(0xFF6FA9B8), fontSize = 13.sp) },
                        maxLines = 3,
                    )
                    Button(onClick = { voice.listen() }, colors = ButtonDefaults.buttonColors(containerColor = PANEL)) { Text("MIC", fontSize = 10.sp) }
                    Button(onClick = { send(input) }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = CYAN)) {
                        Text(if (busy) "…" else "SEND", color = INK, fontSize = 10.sp)
                    }
                }
            }

            "DEVICE" -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("DEVICE INFORMATION", color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    deviceInfo.forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(label, color = Color(0xFF7FD8E8), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(value, color = Color(0xFFE6FBFF), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Values are read straight from Android APIs. Fields the platform does not expose are marked \"not reported\".",
                        color = Color(0xFF6FA9B8), fontSize = 11.sp,
                    )
                    Button(
                        onClick = { deviceInfo = DeviceTools.deviceInfo(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = PANEL),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("REFRESH", fontSize = 10.sp) }
                    Button(
                        onClick = {
                            val report = deviceInfo.joinToString("\n") { "${it.first}: ${it.second}" }
                            send("Here is my phone information. Tell me anything that looks wrong.\n$report")
                            section = "CHAT"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CYAN),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("SEND TO JARVIS", color = INK, fontSize = 10.sp) }
                }
            }

            "ACTIONS" -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        label = { Text("App name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("OPEN THAT APP") {
                        val result = DeviceTools.openApp(context, appQuery)
                        log.add(0, result.message)
                    }
                    ActionButton("OPEN SYSTEM SETTINGS") { log.add(0, DeviceTools.openSetting(context, Settings.ACTION_SETTINGS).message) }
                    ActionButton("WI-FI SETTINGS") { log.add(0, DeviceTools.openSetting(context, Settings.ACTION_WIFI_SETTINGS).message) }
                    ActionButton("OPEN CAMERA") {
                        log.add(0, DeviceTools.openApp(context, "camera").message)
                    }
                    ActionButton("FLASHLIGHT ON") { log.add(0, DeviceTools.setFlashlight(context, true).message) }
                    ActionButton("FLASHLIGHT OFF") { log.add(0, DeviceTools.setFlashlight(context, false).message) }
                    ActionButton("BRIGHTNESS 70%") { log.add(0, DeviceTools.setBrightness(context, 70).message) }
                    ActionButton("5 MINUTE TIMER") { log.add(0, DeviceTools.startTimer(context, 5).message) }
                    ActionButton("TRACK MY LOCATION") { log.add(0, DeviceTools.location(context).message) }
                    ActionButton("BATTERY STATUS") {
                        val battery = DeviceTools.battery(context)
                        log.add(0, "Battery ${battery.first ?: "unknown"} · charging ${battery.second ?: "unknown"}")
                    }
                    ActionButton("SCAN DEVICE (SCOPED)") {
                        val report = DeviceTools.securityScan(context)
                        log.addAll(0, report.reversed())
                        send("Here is my device security scan:\n${report.joinToString("\n")}\nExplain the findings and tell me what else I should check.")
                        section = "CHAT"
                    }
                    ActionButton("GRANT PERMISSIONS") { askForPermissions() }
                    ActionButton("DEFAULT ASSISTANT HELP") {
                        log.add(0, "Open Settings → Apps → Default apps → Digital assistant and pick Jarvis AI. Availability depends on your Android version and Infinix system policy.")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("ACTION LOG", color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    log.take(12).forEach { entry ->
                        Text("· $entry", color = Color(0xFFBFEFFA), fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    if (log.isEmpty()) Text("Nothing run yet.", color = Color(0xFF6FA9B8), fontSize = 12.sp)
                }
            }

            else -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("CONNECTION", color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text("Jarvis API key", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Create an API key for this app in the Base44 dashboard → API, then paste it here. It is stored in encrypted preferences on this device and never logged.",
                        color = Color(0xFF6FA9B8), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = {
                            store.saveToken(tokenDraft.trim())
                            token = tokenDraft.trim()
                            status = if (token.isBlank()) "Key cleared." else "Key saved."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CYAN),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("SAVE KEY", color = INK, fontSize = 10.sp) }

                    Spacer(Modifier.height(14.dp))
                    Text("SPEECH", color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf(true, false).forEach { value ->
                            Button(
                                onClick = { spoken = value },
                                colors = ButtonDefaults.buttonColors(containerColor = if (spoken == value) CYAN else PANEL),
                            ) { Text(if (value) "SPEAK REPLIES" else "SILENT", color = if (spoken == value) INK else CYAN, fontSize = 10.sp) }
                        }
                    }
                    Text(
                        "Voice input is push to talk: the microphone opens only while you hold the MIC button. A background wake word is not implemented, because Android battery and microphone rules make always-on listening unreliable — the assistant role, this app and the notification shortcut are the supported paths.",
                        color = Color(0xFF6FA9B8), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = PANEL),
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
    ) { Text(label, color = CYAN, fontSize = 10.sp) }
}