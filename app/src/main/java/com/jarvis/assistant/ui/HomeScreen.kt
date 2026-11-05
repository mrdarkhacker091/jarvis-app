package com.jarvis.assistant.ui

import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.JarvisState
import com.jarvis.assistant.Turn
import com.jarvis.assistant.data.Prefs

@Composable
fun HomeScreen(
    turns: List<Turn>,
    state: JarvisState,
    subtitle: String,
    onTalk: () -> Unit,
    onSend: (String) -> Unit,
    onSettings: () -> Unit,
    onClear: () -> Unit,
    onToggleWake: (Boolean) -> Unit,
    setupWarning: String?,
    activity: Context
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var wake by remember { mutableStateOf(Prefs.get(ctx).wakeWordEnabled) }
    val transition = rememberInfiniteTransition(label = "core")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(animationSpec =
        androidx.compose.animation.core.tween(9000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "r")
    val pulse by transition.animateFloat(0.3f, 1f, infiniteRepeatable(animationSpec =
        androidx.compose.animation.core.tween(2400, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "p")
    val battery = remember { batteryLevel(ctx) }
    val storageFree = remember { storageFreeGigabytes(ctx) }
    val active = state != JarvisState.IDLE

    Column(modifier = Modifier.fillMaxSize().background(JarvisBg).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = wake, onCheckedChange = { wake = it; onToggleWake(it) })
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = JarvisCyan) }
        }
        Text(stateLabel(state), color = if (active) JarvisCyan else JarvisDim, fontSize = 12.sp, letterSpacing = 2.sp)
        if (subtitle.isNotBlank()) Text(subtitle, color = JarvisDim, fontSize = 11.sp, maxLines = 2)
        if (setupWarning != null) Text(setupWarning, color = JarvisRed, fontSize = 11.sp)

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(color = JarvisCyan.copy(alpha = 0.10f), radius = size.minDimension / 2, center = c)
                for (i in 0 until 3) {
                    val r = size.minDimension / 2 * (0.35f + i * 0.22f)
                    drawCircle(color = JarvisCyan.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(width = 3f))
                }
                rotate(rotation) {
                    drawArc(color = JarvisCyan, startAngle = 0f, sweepAngle = 80f, useCenter = false,
                        topLeft = Offset(c.x - size.minDimension / 3, c.y - size.minDimension / 3),
                        size = androidx.compose.ui.geometry.Size(size.minDimension * 2 / 3, size.minDimension * 2 / 3),
                        style = Stroke(width = 5f))
                }
                drawCircle(color = JarvisCyan.copy(alpha = pulse), radius = size.minDimension / 7, center = c)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TelemetryChip("Battery", "$battery%")
            TelemetryChip("Storage", "${storageFree}GB free")
            TelemetryChip("State", state.name)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTalk, modifier = Modifier.size(56.dp).background(JarvisCyan.copy(alpha = if (active) 0.3f else 0.15f), CircleShape)) {
                Icon(Icons.Default.Mic, contentDescription = "Talk", tint = JarvisCyan)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type or say a command", color = JarvisDim, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { onSend(input); input = "" } }),
                colors = TextFieldDefaults.colors(focusedContainerColor = JarvisSurface, unfocusedContainerColor = JarvisSurface,
                    focusedTextColor = JarvisText, unfocusedTextColor = JarvisText, focusedIndicatorColor = JarvisCyan),
                singleLine = true
            )
            IconButton(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = JarvisCyan)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            item { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent", color = JarvisDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Text("Clear", color = JarvisDim, fontSize = 11.sp, modifier = Modifier.clickable { onClear() })
            } }
            items(turns.takeLast(40)) { t -> Text("${if (t.from == "user") "> " else "J "} ${t.text}",
                color = if (t.from == "user") JarvisText else JarvisCyan, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable
private fun TelemetryChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = JarvisDim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = JarvisCyan, fontSize = 12.sp)
    }
}

private fun stateLabel(s: JarvisState): String = when (s) {
    JarvisState.IDLE -> "READY"
    JarvisState.LISTENING -> "LISTENING"
    JarvisState.WAKE_DETECTED -> "WAKE DETECTED"
    JarvisState.THINKING -> "THINKING"
    JarvisState.EXECUTING -> "EXECUTING"
    JarvisState.SPEAKING -> "SPEAKING"
    JarvisState.PAUSED -> "PAUSED"
    JarvisState.ERROR -> "ERROR"
}

private fun batteryLevel(ctx: Context): Int {
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun storageFreeGigabytes(ctx: Context): Long {
    return try {
        val stat = StatFs(ctx.filesDir.absolutePath)
        stat.availableBytes / (1024L * 1024L * 1024L)
    } catch (_: Exception) { 0 }
}