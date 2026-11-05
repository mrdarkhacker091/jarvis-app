package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JarvisCyan = Color(0xFF00E5FF)
val JarvisBg = Color(0xFF05070A)
val JarvisSurface = Color(0xFF0E1419)
val JarvisRaised = Color(0xFF16202A)
val JarvisDim = Color(0xFF8FA3AD)
val JarvisText = Color(0xFFE6F1F5)
val JarvisRed = Color(0xFFFF5370)

private val Colors = darkColorScheme(
    primary = JarvisCyan, onPrimary = Color.Black,
    secondary = JarvisCyan, onSecondary = Color.Black,
    background = JarvisBg, onBackground = JarvisText,
    surface = JarvisSurface, onSurface = JarvisText,
    surfaceVariant = JarvisRaised, onSurfaceVariant = JarvisDim,
    error = JarvisRed, onError = Color.Black
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}