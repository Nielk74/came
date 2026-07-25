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

/** Shared dark-room palette for the menu, the library, and the overlays over the viewfinder. */
object CamePalette {
    val Black = Color(0xFF070707)
    val Panel = Color(0xFF111111)
    val PanelSelected = Color(0xFF171313)
    val Separator = Color(0xFF2B2B2B)
    /** Unselected segmented controls: a step above [Panel] so they read as pressable. */
    val Control = Color(0xFF242424)
    /** Cards that float over the viewfinder rather than sitting in a menu. */
    val Overlay = Color(0xFF0C0C0C)
    val Muted = Color(0xFF929292)
    val Accent = Color(0xFFE31B23)
}

@Composable
fun CameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CameColors,
        content = content,
    )
}
