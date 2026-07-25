package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyRecoveryTest {
    @Test
    fun aWashedOutSkyLosesBrightnessAndGetsItsColourBack() {
        val frame = Frame()
        frame.fillSky(rgb(232, 236, 243))
        val before = frame.pixels.copyOf()

        SkyRecovery.apply(frame.pixels, frame.width, frame.height)

        val skyBefore = frame.skySample(before)
        val skyAfter = frame.skySample(frame.pixels)
        assertTrue(
            "sky should lose brightness, was $skyBefore now $skyAfter",
            luma(skyAfter) < luma(skyBefore) - .02f,
        )
        assertTrue(
            "red should come down further than blue, which is what returns the colour",
            drop(skyBefore, skyAfter, 16) > drop(skyBefore, skyAfter, 0),
        )
        assertTrue("sky should be less neutral", coolness(skyAfter) > coolness(skyBefore))
    }

    @Test
    fun theGroundBelowTheSkylineIsLeftAlone() {
        val frame = Frame()
        frame.fillSky(rgb(232, 236, 243))
        val before = frame.pixels.copyOf()

        SkyRecovery.apply(frame.pixels, frame.width, frame.height)

        // Well below the horizon, nothing may move: a band of darkened ground would be the
        // signature of a mask applied without a per-pixel gate.
        for (y in frame.horizon + 6 until frame.height) {
            for (x in 0 until frame.width) {
                val index = y * frame.width + x
                assertEquals(
                    "ground pixel at $x,$y changed",
                    before[index].toLong(),
                    frame.pixels[index].toLong(),
                )
            }
        }
    }

    @Test
    fun anAlreadyDeepBlueSkyIsBarelyTouched() {
        val washed = Frame().apply { fillSky(rgb(232, 236, 243)) }
        val deep = Frame().apply { fillSky(rgb(84, 132, 208)) }
        val washedBefore = washed.skySample(washed.pixels)
        val deepBefore = deep.skySample(deep.pixels)

        SkyRecovery.apply(washed.pixels, washed.width, washed.height)
        SkyRecovery.apply(deep.pixels, deep.width, deep.height)

        val washedDrop = luma(washedBefore) - luma(washed.skySample(washed.pixels))
        val deepDrop = luma(deepBefore) - luma(deep.skySample(deep.pixels))
        assertTrue(
            "a saturated sky has nothing to recover: washed $washedDrop vs deep $deepDrop",
            deepDrop < washedDrop * .5f,
        )
    }

    @Test
    fun aWarmSunsetSkyIsNotCooledOrDarkened() {
        val frame = Frame()
        frame.fillSky(rgb(246, 206, 152))
        val before = frame.pixels.copyOf()

        SkyRecovery.apply(frame.pixels, frame.width, frame.height)

        assertTrue("a warm sky is the subject, not a fault", before.contentEquals(frame.pixels))
    }

    @Test
    fun aBrightEnclosedSurfaceIsNotTreatedAsSky() {
        // A pale wall filling the frame below a dark band at the top: bright and flat, but no path
        // back to the top edge, which is what separates sky from every other bright flat surface.
        val frame = Frame()
        for (index in frame.pixels.indices) {
            frame.pixels[index] = if (index / frame.width < 6) rgb(28, 26, 30) else rgb(236, 238, 240)
        }
        val before = frame.pixels.copyOf()

        SkyRecovery.apply(frame.pixels, frame.width, frame.height)

        assertTrue("an enclosed bright surface must not be darkened", before.contentEquals(frame.pixels))
    }

    @Test
    fun aBrightButTexturedCeilingIsNotTreatedAsSky() {
        val frame = Frame()
        for (y in 0 until frame.horizon) {
            for (x in 0 until frame.width) {
                val ripple = if ((x / 3 + y / 3) % 2 == 0) 236 else 150
                frame.pixels[y * frame.width + x] = rgb(ripple - 30, ripple - 8, ripple)
            }
        }
        val before = frame.pixels.copyOf()

        SkyRecovery.apply(frame.pixels, frame.width, frame.height)

        assertTrue("structure means it is not sky", before.contentEquals(frame.pixels))
    }

    @Test
    fun aNightSceneAndTinyFramesAreHandledWithoutChangeOrCrash() {
        val night = Frame()
        night.pixels.fill(rgb(18, 20, 26))
        val before = night.pixels.copyOf()
        SkyRecovery.apply(night.pixels, night.width, night.height)
        assertTrue(before.contentEquals(night.pixels))

        val tiny = IntArray(4) { rgb(240, 244, 250) }
        SkyRecovery.apply(tiny, 2, 2)
        SkyRecovery.apply(IntArray(1) { rgb(240, 244, 250) }, 1, 1)
    }

    /** A frame with a flat sky above [horizon] and textured ground below it. */
    private class Frame {
        val width = 120
        val height = 90
        val horizon = 40
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (y < horizon) rgb(230, 234, 242) else rgb(60 + (x % 9) * 9, 88 + (x % 7) * 8, 44)
        }

        fun fillSky(color: Int) {
            for (y in 0 until horizon) {
                for (x in 0 until width) pixels[y * width + x] = color
            }
        }

        /** Mean sky colour, sampled clear of the horizon so the gradient is not averaged in. */
        fun skySample(source: IntArray): Int {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (y in 2 until horizon / 2) {
                for (x in 0 until width) {
                    val color = source[y * width + x]
                    red += color ushr 16 and 0xff
                    green += color ushr 8 and 0xff
                    blue += color and 0xff
                    count++
                }
            }
            return rgb(red / count, green / count, blue / count)
        }
    }

    private companion object {
        fun rgb(r: Int, g: Int, b: Int) = -0x1000000 or
            (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        fun luma(color: Int) = ColorMath.luma(
            (color ushr 16 and 0xff) / 255f,
            (color ushr 8 and 0xff) / 255f,
            (color and 0xff) / 255f,
        )

        fun coolness(color: Int) = ((color and 0xff) - (color ushr 16 and 0xff)) / 255f

        fun drop(before: Int, after: Int, shift: Int) =
            (before ushr shift and 0xff) - (after ushr shift and 0xff)
    }
}
