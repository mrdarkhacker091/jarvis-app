package com.jarvis.assistant.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisState
import com.jarvis.assistant.Turn
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.notifications.CapturedNotification

@Composable
fun Dashboard(
    turns: List<Turn>,
    state: JarvisState,
    notifications: List<CapturedNotification>,
    prefs: Prefs,
    setupWarning: String?,
    onTalk: () -> Unit,
    onSend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(JarvisBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JARVIS",
                    color = JarvisCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(text = statusLine(state), color = JarvisDim, fontSize = 12.sp)
            }
        }

        if (setupWarning != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(JarvisRaised).padding(12.dp)
            ) {
                Text(text = setupWarning, color = JarvisRed, fontSize = 12.sp)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> HomeTab(turns, state, prefs, onTalk, onSend, onClear)
                1 -> ActivityTab(turns, notifications)
                else -> SettingsHintTab(onOpenSettings)
            }
        }

        NavigationBar(containerColor = JarvisSurface, contentColor = JarvisCyan) {
            NavigationBarItem(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 10.sp) },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Icon(Icons.Filled.Notifications, contentDescription = "Activity") },
                label = { Text("Activity", fontSize = 10.sp) },
                colors = navColors()
            )
            NavigationBarItem(
                selected = tab == 2,
                onClick = {
                    tab = 2
                    onOpenSettings()
                },
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                label = { Text("Settings", fontSize = 10.sp) },
                colors = navColors()
            )
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = JarvisCyan,
    selectedTextColor = JarvisCyan,
    unselectedIconColor = JarvisDim,
    unselectedTextColor = JarvisDim,
    indicatorColor = JarvisRaised
)

@Composable
private fun HomeTab(
    turns: List<Turn>,
    state: JarvisState,
    prefs: Prefs,
    onTalk: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val reducedMotion = remember { prefs.reducedMotion }

    Column(modifier = Modifier.fillMaxSize()) {
        // Animated AI core: pulsing rings, paused when reduced motion is on.
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            AiCore(state = state, animate = !reducedMotion)
        }
        Text(
            text = coreLabel(state),
            color = JarvisDim,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        Text(text = "Standing by.", color = JarvisDim, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Say the wake phrase, tap the mic, or type below.",
                            color = JarvisDim,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            items(turns.takeLast(30)) { turn -> TurnBubble(turn) }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "PHONE",
                    color = JarvisCyan,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            item { TelemetryCard(ctx) }
            item {
                Text(
                    text = "PERMISSIONS",
                    color = JarvisCyan,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            item { PermissionCard(ctx) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask JARVIS", color = JarvisDim) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        onSend(t)
                        input = ""
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = JarvisSurface,
                    unfocusedContainerColor = JarvisSurface,
                    focusedTextColor = JarvisText,
                    unfocusedTextColor = JarvisText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = JarvisCyan
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                val t = input.trim()
                if (t.isNotEmpty()) {
                    onSend(t)
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = JarvisCyan)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.History, contentDescription = "Clear", tint = JarvisDim)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape)
                    .background(if (state == JarvisState.LISTENING) JarvisCyan else JarvisRaised),
                contentAlignment = Alignment.Center
            ) {
                if (state == JarvisState.THINKING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = JarvisCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onTalk) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Talk",
                            tint = if (state == JarvisState.LISTENING) Color.Black else JarvisCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(turns: List<Turn>, notifications: List<CapturedNotification>) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "NOTIFICATIONS",
            color = JarvisCyan,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        if (notifications.isEmpty()) {
            InfoRow("No captured notifications yet. Grant notification access to see them here.")
        } else {
            notifications.take(30).forEach { n ->
                val who = n.spokenSender.ifBlank { n.sender.orEmpty() }
                val line = if (who.isNotBlank()) {
                    "${n.app} — $who: ${n.text}"
                } else {
                    "${n.app}: ${n.text}"
                }
                InfoRow(line)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "RECENT ACTIVITY",
            color = JarvisCyan,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        if (turns.isEmpty()) {
            InfoRow("No conversation yet.")
        } else {
            turns.takeLast(20).reversed().forEach { t ->
                InfoRow("${if (t.from == "user") "You" else "JARVIS"}: ${t.text.take(160)}")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsHintTab(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Settings live on the next screen.", color = JarvisText, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Model keys, voice, briefing detail, reminders, permissions.",
            color = JarvisDim,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TurnBubble(turn: Turn) {
    val isUser = turn.from == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (isUser) JarvisRaised else Color(0xFF0A1218))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = turn.text,
                color = if (isUser) JarvisText else JarvisCyan,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AiCore(state: JarvisState, animate: Boolean) {
    if (!animate) {
        Canvas(modifier = Modifier.size(168.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = Color(0xFF0E2233), radius = size.minDimension / 2f)
            drawCircle(color = JarvisCyanStatic, radius = size.minDimension / 2.6f, style = Stroke(3f))
            drawCircle(color = JarvisCyanStatic, radius = size.minDimension / 3.4f, style = Stroke(2f))
            drawCircle(color = JarvisCyanStatic, radius = size.minDimension / 6f)
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "core")
    val p1 by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1"
    )
    val p2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2"
    )
    val glow = when (state) {
        JarvisState.LISTENING -> Color(0xFF00E5FF)
        JarvisState.THINKING -> Color(0xFF7C4DFF)
        JarvisState.SPEAKING -> Color(0xFF00E676)
        JarvisState.ERROR -> Color(0xFFFF5370)
        JarvisState.IDLE -> Color(0xFF00B8D4)
    }
    Canvas(modifier = Modifier.size(168.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension / 2f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0B1826), Color(0xFF05070A)),
                center = c,
                radius = base * 1.4f
            )
        )
        drawCircle(color = Color(0xFF0E2233), radius = base)
        drawCircle(color = glow, radius = base * 0.92f * p1, alpha = 0.55f, style = Stroke(3f))
        drawCircle(color = glow, radius = base * 0.74f * p2, alpha = 0.7f, style = Stroke(2f))
        drawCircle(color = glow, radius = base * 0.34f, alpha = 0.9f)
        drawCircle(color = Color.White, radius = base * 0.10f, alpha = 0.9f)
    }
}

private val JarvisCyanStatic = Color(0xFF00E5FF)

@Composable
private fun TelemetryCard(ctx: Context) {
    val battery = remember { readBattery(ctx) }
    val storage = remember { readStorage(ctx) }
    val net = remember { readNet(ctx) }
    Card {
        TelemetryRow("Battery", battery)
        TelemetryRow("Storage", storage)
        TelemetryRow("Network", net)
    }
}

@Composable
private fun PermissionCard(ctx: Context) {
    val rows = remember { readPermissions(ctx) }
    Card {
        rows.forEach { (label, ok) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (ok) Color(0xFF00E676) else JarvisRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = JarvisText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "Unavailable values are shown honestly; nothing here is estimated.",
            color = JarvisDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurface)
            .padding(14.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = JarvisDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = JarvisText,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoRow(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JarvisSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = JarvisText, fontSize = 13.sp)
    }
}

private fun readBattery(ctx: Context): String {
    return try {
        val bm = ctx.applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = try {
            bm.isCharging
        } catch (e: Exception) {
            false
        }
        if (level < 0) {
            "Unavailable"
        } else if (charging) {
            "$level% (charging)"
        } else {
            "$level%"
        }
    } catch (e: Exception) {
        "Unavailable"
    }
}

private fun readStorage(ctx: Context): String {
    return try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        val totalGb = stat.totalBytes / 1024.0 / 1024.0 / 1024.0
        "%.1f GB free of %.0f GB".format(freeGb, totalGb)
    } catch (e: Exception) {
        "Unavailable"
    }
}

private fun readNet(ctx: Context): String {
    return try {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(active) ?: return "Offline"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    } catch (e: Exception) {
        "Unavailable"
    }
}

private fun readPermissions(ctx: Context): List<Pair<String, Boolean>> {
    val appCtx = ctx.applicationContext
    val out = ArrayList<Pair<String, Boolean>>()
    try {
        val mic = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        out.add("Microphone" to mic)
    } catch (e: Exception) {
        out.add("Microphone" to false)
    }
    try {
        val ok = NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        out.add("Notification access" to ok)
    } catch (e: Exception) {
        out.add("Notification access" to false)
    }
    try {
        val enabled = Settings.Secure.getString(
            appCtx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        out.add("Accessibility" to enabled.contains(appCtx.packageName))
    } catch (e: Exception) {
        out.add("Accessibility" to false)
    }
    try {
        val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(appCtx)
        } else {
            true
        }
        out.add("Write settings" to canWrite)
    } catch (e: Exception) {
        out.add("Write settings" to false)
    }
    try {
        val pm = appCtx.packageManager
        val batt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pmi = pm.getPackageInfo(appCtx.packageName, PackageManager.GET_PERMISSIONS)
            true && pmi != null
        } else {
            true
        }
        out.add("Battery optimisation exemption (check in Settings)" to batt)
    } catch (e: Exception) {
        out.add("Battery optimisation exemption (check in Settings)" to false)
    }
    return out
}

private fun statusLine(state: JarvisState): String {
    return when (state) {
        JarvisState.IDLE -> "idle"
        JarvisState.LISTENING -> "listening"
        JarvisState.THINKING -> "thinking"
        JarvisState.SPEAKING -> "speaking"
        JarvisState.ERROR -> "error"
    }
}

private fun coreLabel(state: JarvisState): String {
    return when (state) {
        JarvisState.IDLE -> "Standing by"
        JarvisState.LISTENING -> "Listening…"
        JarvisState.THINKING -> "Thinking…"
        JarvisState.SPEAKING -> "Speaking…"
        JarvisState.ERROR -> "Something went wrong"
    }
}
