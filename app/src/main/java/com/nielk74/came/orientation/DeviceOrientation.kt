package com.nielk74.came.orientation

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snaps an OrientationEventListener reading to the CameraX target rotation for that device pose.
 *
 * The listener reports clockwise physical orientation, while Surface rotations describe the
 * counter-clockwise rotation from the device's natural orientation.
 */
internal fun surfaceRotationForDeviceOrientation(orientationDegrees: Int): Int? =
    when (orientationDegrees) {
        !in 0..359 -> null
        in 45 until 135 -> Surface.ROTATION_270
        in 135 until 225 -> Surface.ROTATION_180
        in 225 until 315 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

internal fun rotationDegreesForSurfaceRotation(surfaceRotation: Int): Float =
    when (surfaceRotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_180 -> 180f
        Surface.ROTATION_270 -> 270f
        else -> 0f
    }

/**
 * One lifecycle-bound physical-orientation source shared by camera output and rotating artwork.
 *
 * The activity remains portrait locked. This state therefore follows the phone itself rather than
 * the display, which lets selected image elements turn without rotating their surrounding text.
 */
internal class DeviceOrientationDataSource(
    context: Context,
    private val lifecycle: Lifecycle,
) : DefaultLifecycleObserver, AutoCloseable {
    private val mutableSurfaceRotation = MutableStateFlow(Surface.ROTATION_0)
    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = surfaceRotationForDeviceOrientation(orientation) ?: return
            mutableSurfaceRotation.value = rotation
        }
    }
    private var lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var listening = false
    private var closed = false

    val surfaceRotation: StateFlow<Int> = mutableSurfaceRotation.asStateFlow()

    init {
        lifecycle.addObserver(this)
        updateListener()
    }

    override fun onStart(owner: LifecycleOwner) {
        lifecycleStarted = true
        updateListener()
    }

    override fun onStop(owner: LifecycleOwner) {
        lifecycleStarted = false
        stopListener()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        stopListener()
        lifecycle.removeObserver(this)
    }

    private fun updateListener() {
        if (closed || !lifecycleStarted || listening || !listener.canDetectOrientation()) return
        listener.enable()
        listening = true
    }

    private fun stopListener() {
        if (!listening) return
        listener.disable()
        listening = false
    }
}
