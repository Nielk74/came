package com.nielk74.came.orientation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun rememberDeviceSurfaceRotation(): State<Int> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = remember(context, lifecycleOwner) {
        DeviceOrientationDataSource(
            context = context.applicationContext,
            lifecycle = lifecycleOwner.lifecycle,
        )
    }
    DisposableEffect(source) {
        onDispose(source::close)
    }
    return source.surfaceRotation.collectAsStateWithLifecycle()
}
