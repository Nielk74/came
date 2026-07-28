package com.nielk74.came.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFormattingTest {
    @Test
    fun compositionZoomUsesCompactStableTenths() {
        assertEquals("1", formatCompositionZoom(1f))
        assertEquals("1.5", formatCompositionZoom(1.47f))
        assertEquals("4", formatCompositionZoom(Float.POSITIVE_INFINITY))
    }
}
