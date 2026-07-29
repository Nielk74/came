package com.nielk74.came.orientation

import android.view.OrientationEventListener
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceOrientationTest {
    @Test
    fun uprightAndReversePortraitMapToTheirSurfaceRotations() {
        assertEquals(Surface.ROTATION_0, surfaceRotationForDeviceOrientation(0))
        assertEquals(Surface.ROTATION_0, surfaceRotationForDeviceOrientation(44))
        assertEquals(Surface.ROTATION_180, surfaceRotationForDeviceOrientation(135))
        assertEquals(Surface.ROTATION_180, surfaceRotationForDeviceOrientation(224))
        assertEquals(Surface.ROTATION_0, surfaceRotationForDeviceOrientation(315))
        assertEquals(Surface.ROTATION_0, surfaceRotationForDeviceOrientation(359))
    }

    @Test
    fun bothLandscapePosesMapToTheMatchingSurfaceRotations() {
        assertEquals(Surface.ROTATION_270, surfaceRotationForDeviceOrientation(45))
        assertEquals(Surface.ROTATION_270, surfaceRotationForDeviceOrientation(134))
        assertEquals(Surface.ROTATION_90, surfaceRotationForDeviceOrientation(225))
        assertEquals(Surface.ROTATION_90, surfaceRotationForDeviceOrientation(314))
    }

    @Test
    fun unknownAndInvalidSensorReadingsAreIgnored() {
        assertNull(surfaceRotationForDeviceOrientation(OrientationEventListener.ORIENTATION_UNKNOWN))
        assertNull(surfaceRotationForDeviceOrientation(360))
    }

    @Test
    fun surfaceRotationsConvertToUiArtworkDegrees() {
        assertEquals(0f, rotationDegreesForSurfaceRotation(Surface.ROTATION_0))
        assertEquals(90f, rotationDegreesForSurfaceRotation(Surface.ROTATION_90))
        assertEquals(180f, rotationDegreesForSurfaceRotation(Surface.ROTATION_180))
        assertEquals(270f, rotationDegreesForSurfaceRotation(Surface.ROTATION_270))
    }
}
