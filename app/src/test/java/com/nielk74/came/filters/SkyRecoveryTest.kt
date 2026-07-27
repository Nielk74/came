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

        recover(frame.pixels, frame.width, frame.height)

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

        recover(frame.pixels, frame.width, frame.height)

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

        recover(washed.pixels, washed.width, washed.height)
        recover(deep.pixels, deep.width, deep.height)

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

        recover(frame.pixels, frame.width, frame.height)

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

        recover(frame.pixels, frame.width, frame.height)

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

        recover(frame.pixels, frame.width, frame.height)

        assertTrue("structure means it is not sky", before.contentEquals(frame.pixels))
    }

    @Test
    fun aNightSceneAndTinyFramesAreHandledWithoutChangeOrCrash() {
        val night = Frame()
        night.pixels.fill(rgb(18, 20, 26))
        val before = night.pixels.copyOf()
        recover(night.pixels, night.width, night.height)
        assertTrue(before.contentEquals(night.pixels))

        recover(IntArray(4) { rgb(240, 244, 250) }, 2, 2)
        recover(IntArray(1) { rgb(240, 244, 250) }, 1, 1)
    }

    @Test
    fun skyBelowABranchIsRecoveredLikeTheSkyAboveIt() {
        // A dark branch spanning the whole frame used to truncate the block region where it crossed,
        // which left the sky under it pale against the recovered sky over it.
        val frame = Frame()
        frame.fillSky(rgb(232, 236, 243))
        val branch = 20
        for (y in branch until branch + 2) {
            for (x in 0 until frame.width) frame.pixels[y * frame.width + x] = rgb(38, 34, 30)
        }
        val before = frame.pixels.copyOf()

        recover(frame.pixels, frame.width, frame.height)

        val above = frame.pixels[(branch - 4) * frame.width + 60]
        val below = frame.pixels[(branch + 6) * frame.width + 60]
        assertTrue("sky above the branch should be recovered", luma(above) < luma(before[0]) - .02f)
        assertEquals("both sides of the branch must render the same", above, below)
        assertEquals(
            "the branch itself keeps its own brightness",
            before[branch * frame.width + 60],
            frame.pixels[branch * frame.width + 60],
        )
    }

    @Test
    fun aGradientSkyIsRecoveredByHowMuchColourEachBandHasLost() {
        // Deep blue overhead easing to a pale horizon: the correction has to follow the colour
        // rather than a single per-frame amount, and has to arrive without a step anywhere.
        val frame = Frame()
        for (y in 0 until frame.horizon) {
            val mix = y.toFloat() / frame.horizon
            val color = rgb(
                (96 + 136 * mix).toInt(),
                (146 + 90 * mix).toInt(),
                (214 + 29 * mix).toInt(),
            )
            for (x in 0 until frame.width) frame.pixels[y * frame.width + x] = color
        }
        val before = frame.pixels.copyOf()

        recover(frame.pixels, frame.width, frame.height)

        fun drop(y: Int) = luma(before[y * frame.width + 60]) - luma(frame.pixels[y * frame.width + 60])
        assertTrue("the deep top of the sky needs little", drop(1) < .01f)
        assertTrue("the washed-out band above the horizon needs the most", drop(frame.horizon - 2) > .02f)
        // No row may pick up a large share of the total correction on its own: a region that stopped
        // part way down the sky would show up here as one row carrying nearly all of it.
        var previous = 0f
        for (y in 0 until frame.horizon) {
            val step = drop(y) - previous
            assertTrue("recovery jumped by $step at row $y", step < .02f)
            previous = drop(y)
        }
    }

    @Test
    fun aBrightCoolWallWellBelowTheSkyIsLeftAlone() {
        val frame = Frame()
        frame.fillSky(rgb(232, 236, 243))
        // The same pale, faintly cool colour as the sky, but at the bottom of the frame.
        for (y in frame.height - 12 until frame.height) {
            for (x in 0 until frame.width) frame.pixels[y * frame.width + x] = rgb(232, 236, 243)
        }
        val before = frame.pixels.copyOf()

        recover(frame.pixels, frame.width, frame.height)

        val wall = (frame.height - 4) * frame.width + 60
        assertEquals("a surface below the skyline is not sky", before[wall], frame.pixels[wall])
    }

    @Test
    fun cloudKeepsItsWhileTheSkyAroundItTurnsBlue() {
        val frame = Frame()
        frame.fillSky(rgb(232, 236, 243))
        // Cloud is lit by the whole sky and comes back neutral; sky keeps a trace of blue even when
        // the exposure has all but washed it out, and that difference is the only thing separating
        // the two here.
        for (y in 8 until 18) {
            for (x in 30 until 80) frame.pixels[y * frame.width + x] = rgb(247, 247, 247)
        }
        val before = frame.pixels.copyOf()

        recover(frame.pixels, frame.width, frame.height)

        val cloud = 12 * frame.width + 55
        val sky = 12 * frame.width + 10
        assertEquals("a cloud must stay white", before[cloud], frame.pixels[cloud])
        assertTrue("the sky around it comes down", luma(frame.pixels[sky]) < luma(before[sky]) - .03f)
        assertTrue(
            "and turns blue rather than grey",
            coolness(frame.pixels[sky]) > coolness(before[sky]) + .02f,
        )
    }

    @Test
    fun aRecoveredSkyMovesTowardCyanRatherThanStraightBlue() {
        val frame = Frame()
        frame.fillSky(rgb(236, 240, 246))
        val before = frame.skySample(frame.pixels)

        recover(frame.pixels, frame.width, frame.height)

        val after = frame.skySample(frame.pixels)
        val red = after ushr 16 and 0xff
        val green = after ushr 8 and 0xff
        val blue = after and 0xff
        assertTrue("blue should lead: $red,$green,$blue", blue > green && green > red)
        assertTrue(
            "green should gain on the red-to-blue axis, which is the cyan lean: $red,$green,$blue",
            cyanLean(after) > cyanLean(before),
        )
    }

    /** The pipeline's own path: detect the region, then recover against it. */
    private fun recover(pixels: IntArray, width: Int, height: Int) {
        val region = SkyRegion.detect(pixels, width, height) ?: return
        SkyRecovery.apply(pixels, width, height, region)
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

        /** Where green sits between red and blue: above zero is the cyan side of pure blue. */
        fun cyanLean(color: Int): Float {
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            return green - (red + blue) * .5f
        }

        fun drop(before: Int, after: Int, shift: Int) =
            (before ushr shift and 0xff) - (after ushr shift and 0xff)
    }
}
