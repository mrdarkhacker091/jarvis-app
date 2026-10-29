package com.jarvis.assistant.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.JarvisState
import com.jarvis.assistant.Turn

@Composable
fun HomeScreen(
    turns: List<Turn>,
    state: JarvisState,
    onTalk: () -> Unit,
    onSend: (String) -> Unit,
    onSettings: () -> Unit,
    onClear: () -> Unit,
    setupWarning: String?
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(JarvisBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "JARVIS", color = JarvisCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(text = statusLine(state), color = JarvisDim, fontSize = 12.sp)
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = JarvisDim) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = JarvisDim) }
        }

        if (setupWarning != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp)).background(JarvisRaised).padding(12.dp)) {
                Text(text = setupWarning, color = JarvisRed, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 60.dp)) {
                        Text(text = "Standing by.", color = JarvisDim, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(text = "Tap the mic and talk, or type below.", color = JarvisDim, fontSize = 13.sp)
                    }
                }
            }
            items(turns) { turn -> TurnBubble(turn) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Ask JARVIS", color = JarvisDim) }, singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { val t = input.trim(); if (t.isNotEmpty()) { onSend(t); input = "" } }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = JarvisSurface, unfocusedContainerColor = JarvisSurface,
                    focusedTextColor = JarvisText, unfocusedTextColor = JarvisText,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = JarvisCyan)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { val t = input.trim(); if (t.isNotEmpty()) { onSend(t); input = "" } }) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = JarvisCyan)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 4.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape)
                    .background(if (state == JarvisState.LISTENING) JarvisCyan else JarvisRaised),
                contentAlignment = Alignment.Center
            ) {
                if (state == JarvisState.THINKING) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = JarvisCyan, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onTalk) {
                        Icon(Icons.Filled.Mic, contentDescription = "Talk",
                            tint = if (state == JarvisState.LISTENING) Color.Black else JarvisCyan,
                            modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnBubble(turn: Turn) {
    val isUser = turn.from == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (isUser) JarvisRaised else Color(0xFF0A1218))
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = turn.text, color = if (isUser) JarvisText else JarvisCyan, fontSize = 14.sp)
        }
    }
}

private fun statusLine(state: JarvisState): String = when (state) {
    JarvisState.IDLE -> "idle"
    JarvisState.LISTENING -> "listening"
    JarvisState.THINKING -> "thinking"
    JarvisState.SPEAKING -> "speaking"
    JarvisState.ERROR -> "error"
}
