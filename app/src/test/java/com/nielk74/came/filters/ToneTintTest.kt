package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneTintTest {
    @Test
    fun lowMidsPickUpBlueAndMidsPickUpWarmth() {
        val lowMid = grey(90)
        val mid = grey(170)
        val pixels = intArrayOf(lowMid, mid)

        ToneTint.apply(pixels)

        assertTrue("low-mids should cool", coolness(pixels[0]) > coolness(lowMid) + .008f)
        assertTrue("mids should warm", coolness(pixels[1]) < coolness(mid) - .008f)
    }

    @Test
    fun highlightsAreLeftWhite() {
        val pixels = IntArray(16) { grey(240 + it) }
        val before = pixels.copyOf()

        ToneTint.apply(pixels)

        assertTrue("paper white must stay white", before.contentEquals(pixels))
    }

    @Test
    fun theTintIsSlightAndKeepsItsExposure() {
        for (level in 0..255) {
            val before = grey(level)
            val pixels = intArrayOf(before)
            ToneTint.apply(pixels)

            val moved = maxOf(
                channel(pixels[0], 16) - channel(before, 16),
                channel(pixels[0], 8) - channel(before, 8),
                channel(pixels[0], 0) - channel(before, 0),
            )
            assertTrue("level $level moved by $moved, which is a cast rather than a tint", moved <= 8)
            assertEquals(
                "level $level changed exposure",
                luma(before),
                luma(pixels[0]),
                .006f,
            )
        }
    }

    @Test
    fun theRampsAreSmoothAcrossTheWholeScale() {
        // A step in the tint would read as banding in a gradient, which is worse than no tint.
        var previous = 0
        for (level in 0..255) {
            val pixels = intArrayOf(grey(level))
            ToneTint.apply(pixels)
            val shift = channel(pixels[0], 0) - channel(pixels[0], 16)
            if (level > 0) {
                assertTrue("tint jumped by ${shift - previous} at level $level", shift - previous <= 2)
            }
            previous = shift
        }
    }

    private fun grey(level: Int): Int {
        val value = level.coerceIn(0, 255)
        return -0x1000000 or (value shl 16) or (value shl 8) or value
    }

    private fun channel(color: Int, shift: Int) = color ushr shift and 0xff

    private fun coolness(color: Int) = (channel(color, 0) - channel(color, 16)) / 255f

    private fun luma(color: Int) = ColorMath.luma(
        channel(color, 16) / 255f,
        channel(color, 8) / 255f,
        channel(color, 0) / 255f,
    )
}
