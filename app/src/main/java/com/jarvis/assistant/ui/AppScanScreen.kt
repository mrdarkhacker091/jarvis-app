package com.jarvis.assistant.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.security.AppScanner
import com.jarvis.assistant.security.AppScanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppScanScreen(ctx: Context, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<AppScanItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { AppScanner.scan(ctx) }
        loading = false
    }
    Column(modifier = Modifier.fillMaxSize().background(JarvisBg).padding(16.dp)) {
        Text("APP / SECURITY SCAN", color = JarvisCyan, fontSize = 16.sp, letterSpacing = 3.sp)
        Text("Heuristic review only. This is not an antivirus and cannot read other apps' private data.",
            color = JarvisDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        if (loading) Text("Scanning installed apps…", color = JarvisDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(items) { a ->
                val risk = a.flaggedPermissions.size + (if (a.hasAccessibility) 5 else 0) + (if (a.hasDeviceAdmin) 3 else 0)
                Text("${a.label}  (${if (a.isSystem) "system" else "user"})",
                    color = JarvisText, fontSize = 12.sp)
                Text("  package: ${a.pkg}  risk: $risk  source: ${a.installSource ?: "?"}",
                    color = JarvisDim, fontSize = 10.sp)
                if (a.hasAccessibility || a.hasDeviceAdmin)
                    Text("  uses accessibility/device-admin", color = JarvisRed, fontSize = 10.sp)
            }
        }
    }
}