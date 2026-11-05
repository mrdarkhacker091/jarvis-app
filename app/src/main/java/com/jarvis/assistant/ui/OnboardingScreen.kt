package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onGrantContacts: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(JarvisBg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("SETUP", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 4.sp)
        Text("Grant these so JARVIS can hear you and act. Nothing is granted silently — Android asks you each time.",
            color = JarvisDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        Step("1. Microphone — for voice commands", "Already requested on launch; allow it in the system dialog.")
        Step("2. Notification access — for the wake briefing", onGrantNotificationAccess)
        Step("3. Accessibility — for on-screen tap/type/scroll", onGrantAccessibility)
        Step("4. Contacts — for \"call by name\"", onGrantContacts)
        Spacer(Modifier.height(16.dp))
        Text("Then open AI Providers in Settings and enter your endpoint, model and key for each provider. Agnes is the default but needs a valid endpoint.",
            color = JarvisDim, fontSize = 11.sp)
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) {
            Text("DONE")
        }
    }
}

@Composable
private fun Step(title: String, action: () -> Unit) {
    Text(title, color = JarvisText, fontSize = 13.sp)
    Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = JarvisSurface, contentColor = JarvisCyan), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Open settings", fontSize = 11.sp)
    }
}

@Composable
private fun Step(title: String, note: String) {
    Text(title, color = JarvisText, fontSize = 13.sp)
    Text(note, color = JarvisDim, fontSize = 10.sp)
}