package com.nielk74.came.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CompositionZoomTest {
    @Test
    fun oneTimesKeepsTheFullFrameAndTheOriginalPixelBuffer() {
        val pixels = IntArray(24) { it }

        assertEquals(
            CenterCropWindow(left = 0, top = 0, width = 6, height = 4),
            centeredCropWindow(6, 4, CompositionZoom.Identity),
        )
        assertSame(
            "1x must not allocate a replacement pixel buffer",
            pixels,
            centeredCropPixels(pixels, 6, 4, CompositionZoom.Identity),
        )
    }

    @Test
    fun twoTimesKeepsTheCenteredDimensionsAndPixels() {
        // 6 x 4, with each pixel carrying its row-major index.
        val pixels = IntArray(24) { it }

        assertEquals(
            CenterCropWindow(left = 1, top = 1, width = 3, height = 2),
            centeredCropWindow(6, 4, CompositionZoom.of(2f)),
        )
        assertArrayEquals(
            intArrayOf(
                7, 8, 9,
                13, 14, 15,
            ),
            centeredCropPixels(pixels, 6, 4, CompositionZoom.of(2f)),
        )
    }

    @Test
    fun zoomIsClampedBetweenOneAndFourTimes() {
        assertEquals(1f, CompositionZoom.of(-10f).factor)
        assertEquals(1f, CompositionZoom.of(Float.NaN).factor)
        assertEquals(1f, CompositionZoom.of(Float.NEGATIVE_INFINITY).factor)
        assertEquals(4f, CompositionZoom.of(20f).factor)
        assertEquals(4f, CompositionZoom.of(Float.POSITIVE_INFINITY).factor)
        assertEquals(4f, CompositionZoom.Identity.scaledBy(100f).factor)
    }

    @Test
    fun invalidIncrementalPinchDoesNotDestroyTheCurrentZoom() {
        val zoom = CompositionZoom.of(2.5f)

        assertEquals(zoom, zoom.scaledBy(Float.NaN))
        assertEquals(zoom, zoom.scaledBy(0f))
        assertEquals(zoom, zoom.scaledBy(-1f))
    }

    @Test
    fun maximumZoomStillKeepsAtLeastOnePixelPerDimension() {
        assertEquals(
            CenterCropWindow(left = 1, top = 1, width = 1, height = 1),
            centeredCropWindow(3, 3, CompositionZoom.of(4f)),
        )
    }
}
