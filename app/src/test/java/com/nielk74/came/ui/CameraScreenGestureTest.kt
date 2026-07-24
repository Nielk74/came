package com.nielk74.came.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraScreenGestureTest {
    @Test
    fun untouchedViewfinderTapCaptures() {
        assertTrue(
            shouldCaptureViewfinderTap(
                consumedByControl = false,
                verticalDistance = 0f,
                horizontalDistance = 0f,
                touchSlop = 12f,
            ),
        )
    }

    @Test
    fun gearTapConsumedByControlDoesNotCapture() {
        assertFalse(
            shouldCaptureViewfinderTap(
                consumedByControl = true,
                verticalDistance = 0f,
                horizontalDistance = 0f,
                touchSlop = 12f,
            ),
        )
    }
}
