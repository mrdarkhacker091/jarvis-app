package com.jarvis.assistant.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ai.LlmClient
import com.jarvis.assistant.ai.providers.GeminiProvider
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.data.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    onGrantOverlay: () -> Unit,
    onBatteryExemption: () -> Unit,
    onClearMemory: () -> Unit,
    onEraseKeys: () -> Unit,
    onRestartService: () -> Unit,
    onShowScan: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(prefs.providers().toMutableList()) }
    var phrase by remember { mutableStateOf(prefs.wakePhrase) }
    var wake by remember { mutableStateOf(prefs.wakeWordEnabled) }
    var briefing by remember { mutableStateOf(prefs.briefingOnWake) }
    var detail by remember { mutableStateOf(prefs.briefingDetail) }
    var reminders by remember { mutableStateOf(prefs.remindersEnabled) }
    var pitch by remember { mutableStateOf(prefs.ttsPitch.toString()) }
    var rate by remember { mutableStateOf(prefs.ttsRate.toString()) }
    var anim by remember { mutableStateOf(prefs.animationIntensity.toString()) }
    var saved by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(JarvisBg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisCyan) }
            Text("SETTINGS", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
        }

        Text("AI PROVIDERS", color = JarvisDim, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
        providers.forEachIndexed { i, p ->
            ProviderCard(p, onChange = { updated ->
                providers = providers.toMutableList().also { it[i] = updated }
            }, onTest = { completion ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val msgs = JSONArray().put(org.json.JSONObject().put("role", "user").put("content", "Say OK."))
                            if (p.nativeGemini) GeminiProvider.complete(p, msgs, null)
                            else LlmClient(p.baseUrl, p.apiKey, p.model).complete(msgs, null)
                            "OK"
                        } catch (e: Exception) { e.message?.take(80) ?: "failed" }
                    }
                    completion(result)
                }
            })
            Spacer(Modifier.height(6.dp))
        }

        Button(onClick = {
            prefs.saveProviders(providers)
            prefs.wakePhrase = phrase; prefs.wakeWordEnabled = wake
            prefs.briefingOnWake = briefing; prefs.briefingDetail = detail; prefs.remindersEnabled = reminders
            prefs.ttsPitch = pitch.toFloatOrNull() ?: 0.62f; prefs.ttsRate = rate.toFloatOrNull() ?: 0.95f
            prefs.animationIntensity = anim.toFloatOrNull() ?: 1f
            saved = true; onRestartService()
        }, colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) {
            Text("SAVE")
        }
        if (saved) Text("Saved. Provider keys are stored in Android Keystore-encrypted storage.", color = JarvisCyan, fontSize = 10.sp)

        Section("VOICE & WAKE")
        FieldRow("Wake phrase", phrase) { phrase = it }
        ToggleRow("Wake listening", wake) { wake = it }
        ToggleRow("Briefing on wake", briefing) { briefing = it }
        FieldRow("Briefing detail (concise/normal/detailed)", detail) { detail = it }
        ToggleRow("One-hour reminders", reminders) { reminders = it }
        FieldRow("TTS pitch (0.5 deep - 1.0)", pitch) { pitch = it }
        FieldRow("TTS rate", rate) { rate = it }
        FieldRow("Animation intensity (0-1)", anim) { anim = it }

        Section("PERMISSIONS")
        ActionRow("Grant notification access", onGrantNotificationAccess)
        ActionRow("Grant accessibility", onGrantAccessibility)
        ActionRow("Grant write settings", onGrantWriteSettings)
        ActionRow("Grant overlay", onGrantOverlay)
        ActionRow("Battery exemption", onBatteryExemption)

        Section("DATA & APP")
        ActionRow("App / security scan", onShowScan)
        ActionRow("Clear conversation memory", onClearMemory)
        ActionRow("Erase all provider keys", onEraseKeys)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProviderCard(p: Provider, onChange: (Provider) -> Unit, onTest: ((String) -> Unit) -> Unit) {
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().background(JarvisSurface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = p.enabled, onCheckedChange = { onChange(p.copy(enabled = it)) })
        }
        OutTextField("Base URL", p.baseUrl) { onChange(p.copy(baseUrl = it)) }
        OutTextField("Model", p.model) { onChange(p.copy(model = it)) }
        OutTextField("API key", p.apiKey, password = !showKey) { onChange(p.copy(apiKey = it)) }
        Row {
            Button(onClick = { showKey = !showKey }, colors = ButtonDefaults.buttonColors(containerColor = JarvisRaised, contentColor = JarvisText)) { Text(if (showKey) "Hide" else "Show") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onTest { status = it } }, colors = ButtonDefaults.buttonColors(containerColor = JarvisRaised, contentColor = JarvisText)) { Text("Test") }
            Spacer(Modifier.width(8.dp))
            if (p.apiKey.isBlank() || p.baseUrl.isBlank())
                Text("Configure endpoint/key/model", color = JarvisRed, fontSize = 10.sp)
        }
        if (status.isNotBlank()) Text(status, color = JarvisDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = JarvisDim, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun FieldRow(label: String, value: String, onChange: (String) -> Unit) {
    OutTextField(label, value, onChange)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = JarvisText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = JarvisRaised, contentColor = JarvisText), modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun OutTextField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, color = JarvisDim, fontSize = 10.sp) },
        singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(focusedContainerColor = JarvisSurface, unfocusedContainerColor = JarvisSurface,
            focusedTextColor = JarvisText, unfocusedTextColor = JarvisText, focusedIndicatorColor = JarvisCyan),
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
}