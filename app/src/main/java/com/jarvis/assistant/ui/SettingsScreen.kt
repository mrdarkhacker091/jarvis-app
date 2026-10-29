package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ai.LlmClient
import com.jarvis.assistant.data.Prefs

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
    onRestartService: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var phrase by remember { mutableStateOf(prefs.wakePhrase) }
    var wake by remember { mutableStateOf(prefs.wakeWordEnabled) }
    var handsFree by remember { mutableStateOf(prefs.handsFreeEnabled) }
    var announce by remember { mutableStateOf(prefs.announceNotifications) }
    var briefing by remember { mutableStateOf(prefs.briefingDetail) }
    var confirmReplies by remember { mutableStateOf(prefs.replyConfirmDefault) }
    var reminders by remember { mutableStateOf(prefs.oneHourReminders) }
    var reducedMotion by remember { mutableStateOf(prefs.reducedMotion) }
    var allowedPkgs by remember { mutableStateOf(prefs.allowedPackages.joinToString(", ")) }

    Column(
        modifier = Modifier.fillMaxSize().background(JarvisBg)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = JarvisDim)
            }
            Text("Settings", color = JarvisText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("MODEL")
        Field("Base URL", baseUrl) { baseUrl = it }
        Field("API key", apiKey) { apiKey = it }
        Field("Model", model) { model = it }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LlmClient.PRESETS.forEach { preset ->
                Button(
                    onClick = {
                        if (preset.second.isNotBlank()) {
                            baseUrl = preset.second
                            model = preset.third
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisRaised, contentColor = JarvisCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(preset.first, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = {
                prefs.baseUrl = baseUrl
                prefs.apiKey = apiKey
                prefs.model = model
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
        ) {
            Text("Save model settings")
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("VOICE")
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Wake word", color = JarvisText, modifier = Modifier.weight(1f))
            Switch(
                checked = wake,
                onCheckedChange = {
                    wake = it
                    prefs.wakeWordEnabled = it
                    onRestartService()
                }
            )
        }
        Field("Wake phrase", phrase) {
            phrase = it
            prefs.wakePhrase = it
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hands-free listening", color = JarvisText)
                Text(
                    "Service listens for the wake phrase without opening the app.",
                    color = JarvisDim,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = handsFree,
                onCheckedChange = {
                    handsFree = it
                    prefs.handsFreeEnabled = it
                    onRestartService()
                }
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Announce notifications", color = JarvisText)
                Text(
                    "Off by default. Wake briefings still work when this is off.",
                    color = JarvisDim,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = announce,
                onCheckedChange = {
                    announce = it
                    prefs.announceNotifications = it
                }
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("BRIEFING")
        BriefingDetailRow(briefing) {
            briefing = it
            prefs.briefingDetail = it
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("MESSAGES")
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Confirm before sending replies", color = JarvisText)
                Text("JARVIS asks first: Send X to Y?", color = JarvisDim, fontSize = 11.sp)
            }
            Switch(
                checked = confirmReplies,
                onCheckedChange = {
                    confirmReplies = it
                    prefs.replyConfirmDefault = it
                }
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("One-hour unanswered reminders", color = JarvisText)
                Text("Gentle nudge for conversations that look unanswered.", color = JarvisDim, fontSize = 11.sp)
            }
            Switch(
                checked = reminders,
                onCheckedChange = {
                    reminders = it
                    prefs.oneHourReminders = it
                }
            )
        }
        Field("Only listen to these apps (package names, comma separated, blank = all)", allowedPkgs) {
            allowedPkgs = it
            prefs.allowedPackages = it.split(",").map { pkg -> pkg.trim() }.filter { pkg -> pkg.isNotEmpty() }.toSet()
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("DISPLAY")
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reduce motion", color = JarvisText)
                Text("Pauses the animated core for a calmer screen.", color = JarvisDim, fontSize = 11.sp)
            }
            Switch(
                checked = reducedMotion,
                onCheckedChange = {
                    reducedMotion = it
                    prefs.reducedMotion = it
                }
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("PERMISSIONS")
        ActionRow("Notification access", onGrantNotificationAccess)
        ActionRow("Accessibility service", onGrantAccessibility)
        ActionRow("Write system settings", onGrantWriteSettings)
        ActionRow("Display over other apps", onGrantOverlay)
        ActionRow("Ignore battery optimisation", onBatteryExemption)

        Spacer(Modifier.height(22.dp))
        SectionLabel("DATA")
        ActionRow("Clear remembered facts", onClearMemory)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BriefingDetailRow(current: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(Prefs.DETAIL_CONCISE, Prefs.DETAIL_NORMAL, Prefs.DETAIL_DETAILED)
        options.forEach { option ->
            Button(
                onClick = { onPick(option) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (current == option) JarvisCyan else JarvisRaised,
                    contentColor = if (current == option) Color.Black else JarvisCyan
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(option.replaceFirstChar { ch -> ch.uppercase() }, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = JarvisCyan,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = JarvisDim) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = JarvisSurface,
            unfocusedContainerColor = JarvisSurface,
            focusedTextColor = JarvisText,
            unfocusedTextColor = JarvisText,
            cursorColor = JarvisCyan,
            focusedIndicatorColor = JarvisCyan,
            unfocusedIndicatorColor = JarvisRaised
        )
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(JarvisSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = JarvisText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = JarvisRaised, contentColor = JarvisCyan),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Open", fontSize = 11.sp)
        }
    }
}
