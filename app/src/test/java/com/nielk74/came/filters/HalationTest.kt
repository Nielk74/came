package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HalationTest {
    @Test
    fun spillSurroundsAHighlightInsteadOfWashingOverIt() {
        val scene = Scene(size = 128, squareHalfWidth = 8)
        val pixels = scene.render()
        val before = pixels.copyOf()

        FilmProcessor.applyHalation(pixels, scene.size, scene.size, PROFILE)

        val centre = scene.index(0, 0)
        assertEquals(
            "the highlight core must not turn red",
            before[centre],
            pixels[centre],
        )
        val justOutside = scene.index(scene.squareHalfWidth + 2, 0)
        assertTrue(
            "the surrounding fringe should gain light",
            redGain(before[justOutside], pixels[justOutside]) > 0f,
        )
    }

    @Test
    fun theFringeIsRedDominant() {
        val scene = Scene(size = 128, squareHalfWidth = 8)
        val pixels = scene.render()
        val before = pixels.copyOf()

        FilmProcessor.applyHalation(pixels, scene.size, scene.size, PROFILE)

        val sample = scene.index(scene.squareHalfWidth + 3, 0)
        val red = redGain(before[sample], pixels[sample])
        val green = channelGain(before[sample], pixels[sample], shift = 8)
        val blue = channelGain(before[sample], pixels[sample], shift = 0)
        assertTrue("red spill $red should exceed green $green", red > green)
        assertTrue("green spill $green should exceed blue $blue", green >= blue)
    }

    @Test
    fun spillDecaysWithDistanceFromTheHighlight() {
        val scene = Scene(size = 128, squareHalfWidth = 8)
        val pixels = scene.render()
        val before = pixels.copyOf()

        FilmProcessor.applyHalation(pixels, scene.size, scene.size, PROFILE)

        val near = scene.index(scene.squareHalfWidth + 2, 0)
        val mid = scene.index(scene.squareHalfWidth + 8, 0)
        val far = scene.index(scene.squareHalfWidth + 40, 0)
        val nearGain = redGain(before[near], pixels[near])
        val midGain = redGain(before[mid], pixels[mid])
        val farGain = redGain(before[far], pixels[far])
        assertTrue("$nearGain should exceed $midGain", nearGain > midGain)
        assertTrue("$midGain should exceed $farGain", midGain > farGain)
    }

    @Test
    fun theHaloScalesWithOutputResolution() {
        // Authored radii are relative to a 1600px long edge, so the same framing at twice the
        // resolution must produce a halo roughly twice as wide in pixels rather than an
        // identical-in-pixels, half-as-large-in-frame one.
        val small = Scene(size = 128, squareHalfWidth = 8)
        val large = Scene(size = 256, squareHalfWidth = 16)
        val smallReach = small.haloReach()
        val largeReach = large.haloReach()

        assertTrue("halo should be measurable at both sizes", smallReach > 0 && largeReach > 0)
        val ratio = largeReach.toFloat() / smallReach
        assertTrue("halo reach ratio was $ratio, expected roughly 2", ratio in 1.5f..2.6f)
    }

    @Test
    fun aDarkSceneWithNoHighlightIsUntouched() {
        val size = 64
        val pixels = IntArray(size * size) { rgb(40, 40, 44) }
        val before = pixels.copyOf()

        FilmProcessor.applyHalation(pixels, size, size, PROFILE)

        assertTrue(before.contentEquals(pixels))
    }

    /** A bright square centred in a dark frame — the classic halation test target. */
    private class Scene(val size: Int, val squareHalfWidth: Int) {
        fun render(): IntArray {
            val pixels = IntArray(size * size) { rgb(26, 26, 30) }
            val centre = size / 2
            for (y in centre - squareHalfWidth until centre + squareHalfWidth) {
                for (x in centre - squareHalfWidth until centre + squareHalfWidth) {
                    pixels[y * size + x] = rgb(238, 236, 230)
                }
            }
            return pixels
        }

        /** Index of the pixel [offsetX]/[offsetY] away from the square's centre. */
        fun index(offsetX: Int, offsetY: Int): Int =
            (size / 2 + offsetY) * size + (size / 2 + offsetX)

        /** How far past the square's edge, in pixels, the red spill stays visible. */
        fun haloReach(): Int {
            val pixels = render()
            val before = pixels.copyOf()
            FilmProcessor.applyHalation(pixels, size, size, PROFILE)
            var reach = 0
            var offset = squareHalfWidth + 1
            while (size / 2 + offset < size) {
                val at = index(offset, 0)
                if (redGain(before[at], pixels[at]) < HALO_VISIBLE_GAIN) break
                reach = offset - squareHalfWidth
                offset++
            }
            return reach
        }
    }

    private companion object {
        val PROFILE = HalationProfile(
            threshold = .55f,
            radius = 200,
            strength = 1f,
            tintR = 1f,
            tintG = .12f,
            tintB = .05f,
        )

        /** Above one 8-bit step, so the reach measurement ignores rounding noise. */
        const val HALO_VISIBLE_GAIN = 1.5f / 255f

        fun rgb(red: Int, green: Int, blue: Int): Int =
            -0x1000000 or (red shl 16) or (green shl 8) or blue

        fun channelGain(before: Int, after: Int, shift: Int): Float =
            ((after ushr shift and 0xff) - (before ushr shift and 0xff)) / 255f

        fun redGain(before: Int, after: Int): Float = channelGain(before, after, shift = 16)
    }
}
