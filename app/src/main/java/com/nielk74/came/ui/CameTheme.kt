package com.nielk74.came.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CameColors = darkColorScheme(
    primary = Color(0xFFF4F1E8),
    onPrimary = Color(0xFF101010),
    background = Color(0xFF101010),
    onBackground = Color(0xFFF4F1E8),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFF4F1E8),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFC9C6BD),
    outline = Color(0xFF5D5B55),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CameColors,
        content = content,
    )
}
