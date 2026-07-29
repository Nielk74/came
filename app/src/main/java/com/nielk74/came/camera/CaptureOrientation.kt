package com.nielk74.came.camera

import android.view.Surface

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
