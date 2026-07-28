package com.nielk74.came.level

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Compose bridge for the electronic level. It is intentionally disabled by default: callers opt in
 * with their persisted setting, and a disabled level consumes no sensor resources.
 */
@Composable
fun rememberElectronicLevelState(
    enabled: Boolean = false,
): State<ElectronicLevelState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = remember(context, lifecycleOwner) {
        ElectronicLevelDataSource(
            context = context.applicationContext,
            lifecycle = lifecycleOwner.lifecycle,
            initiallyEnabled = enabled,
        )
    }

    SideEffect {
        source.enabled = enabled
    }
    DisposableEffect(source) {
        onDispose(source::close)
    }

    return source.state.collectAsStateWithLifecycle()
}
