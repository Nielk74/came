package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectiveColorTest {
    @Test
    fun foliageGreenRotatesTowardCyanAndKeepsItsLuminance() {
        val leaf = rgb(90, 130, 60)
        val pixels = intArrayOf(leaf)
        SelectiveColor.applyFoliage(pixels, FoliageTone(cyanShift = .6f), layerMix = 1f)

        assertTrue("foliage should have been touched", pixels[0] != leaf)
        assertTrue(
            "green should move toward cyan, i.e. gain blue relative to red",
            blueMinusRed(pixels[0]) > blueMinusRed(leaf),
        )
        assertEquals(linearLuma(leaf), linearLuma(pixels[0]), .006f)
    }

    @Test
    fun foliageSaturationBoostAddsChromaWithoutChangingExposure() {
        val leaf = rgb(90, 130, 60)
        val plain = intArrayOf(leaf)
        val boosted = intArrayOf(leaf)
        SelectiveColor.applyFoliage(plain, FoliageTone(.6f, saturationBoost = 0f), 1f)
        SelectiveColor.applyFoliage(boosted, FoliageTone(.6f, saturationBoost = .3f), 1f)

        assertTrue(chroma(boosted[0]) > chroma(plain[0]))
        assertEquals(linearLuma(leaf), linearLuma(boosted[0]), .006f)
    }

    @Test
    fun foliageLeavesSkinNeutralsAndBlueObjectsAlone() {
        val untouched = intArrayOf(
            rgb(224, 172, 142), // light skin
            rgb(124, 86, 66), // deep skin
            rgb(128, 128, 128), // neutral grey
            rgb(64, 96, 200), // blue object
            rgb(210, 60, 50), // red object
            rgb(8, 10, 7), // near-black
        )
        val before = untouched.copyOf()
        SelectiveColor.applyFoliage(untouched, FoliageTone(1f, 1f), layerMix = 1f)

        assertTrue(before.contentEquals(untouched))
    }

    @Test
    fun disabledOrZeroMixIsANoOp() {
        val leaf = rgb(90, 130, 60)
        val none = intArrayOf(leaf)
        val zeroMix = intArrayOf(leaf)
        SelectiveColor.applyFoliage(none, FoliageTone.None, 1f)
        SelectiveColor.applyFoliage(zeroMix, FoliageTone(.6f, .2f), layerMix = 0f)

        assertEquals(leaf, none[0])
        assertEquals(leaf, zeroMix[0])
    }

    @Test
    fun skyShiftOnlyReachesBlueWithinTheDetectedSkyRegion() {
        val width = 16
        val height = 16
        val sky = rgb(96, 148, 210)
        val pixels = IntArray(width * height) { rgb(120, 118, 116) }
        // Sky occupies the top four rows; an identically coloured object floats in the middle.
        for (index in 0 until width * 4) pixels[index] = sky
        val islandStart = 9 * width + 6
        for (row in 0 until 3) {
            for (column in 0 until 4) pixels[islandStart + row * width + column] = sky
        }
        val before = pixels.copyOf()

        val region = requireNotNull(SkyRegion.detect(pixels, width, height))
        SelectiveColor.applySky(pixels, width, height, region, SkyTone(.25f, .24f), layerMix = 1f)

        assertTrue("connected sky should shift", pixels[width * 2 + 8] != before[width * 2 + 8])
        assertTrue(
            "sky should move toward cyan, i.e. gain green relative to red",
            greenMinusRed(pixels[width * 2 + 8]) > greenMinusRed(sky),
        )
        assertEquals(linearLuma(sky), linearLuma(pixels[width * 2 + 8]), .006f)
        assertEquals(
            "an isolated blue object must not be treated as sky",
            before[islandStart + width + 1],
            pixels[islandStart + width + 1],
        )
    }

    @Test
    fun skyShiftFollowsASkyRegionAllTheWayDownTheFrame() {
        val width = 12
        val height = 24
        val sky = rgb(96, 148, 210)
        val pixels = IntArray(width * height) { rgb(120, 118, 116) }
        // A narrow blue column running from the top edge all the way to the bottom.
        for (y in 0 until height) {
            pixels[y * width + 5] = sky
            pixels[y * width + 6] = sky
        }
        val before = pixels.copyOf()

        val region = requireNotNull(SkyRegion.detect(pixels, width, height))
        SelectiveColor.applySky(pixels, width, height, region, SkyTone(.25f), layerMix = 1f)

        val bottom = (height - 1) * width + 5
        assertTrue("the region should carry to the last row", pixels[bottom] != before[bottom])
    }

    @Test
    fun skyLeavesNeutralsAndWarmPixelsAlone() {
        val width = 8
        val height = 8
        val pixels = IntArray(width * height) { index ->
            if (index % 2 == 0) rgb(128, 128, 128) else rgb(214, 168, 140)
        }
        val before = pixels.copyOf()

        // Given the whole frame to work with, the hue window alone has to hold these back.
        SelectiveColor.applySky(
            pixels,
            width,
            height,
            SkyRegion.WholeFrame,
            SkyTone(.45f, .5f),
            layerMix = 1f,
        )

        assertTrue(before.contentEquals(pixels))
    }

    @Test
    fun skyUnderAnObstructionShiftsLikeTheSkyAboveIt() {
        val width = 24
        val height = 24
        val sky = rgb(96, 148, 210)
        val pixels = IntArray(width * height) { sky }
        // A dark wire across the whole frame: sky above and below it must render the same.
        for (x in 0 until width) pixels[10 * width + x] = rgb(40, 36, 34)

        val region = requireNotNull(SkyRegion.detect(pixels, width, height))
        SelectiveColor.applySky(pixels, width, height, region, SkyTone(.25f, .24f), layerMix = 1f)

        assertEquals(
            "a wire must not divide the sky into a shifted half and an unshifted half",
            pixels[6 * width + 12],
            pixels[16 * width + 12],
        )
        assertTrue("sky should have shifted at all", pixels[6 * width + 12] != sky)
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        -0x1000000 or (red shl 16) or (green shl 8) or blue

    private fun red(color: Int) = (color ushr 16 and 0xff) / 255f
    private fun green(color: Int) = (color ushr 8 and 0xff) / 255f
    private fun blue(color: Int) = (color and 0xff) / 255f

    private fun linearLuma(color: Int) = ColorMath.linearLuma(red(color), green(color), blue(color))
    private fun blueMinusRed(color: Int) = blue(color) - red(color)
    private fun greenMinusRed(color: Int) = green(color) - red(color)

    private fun chroma(color: Int): Float {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        return maxOf(r, g, b) - minOf(r, g, b)
    }
}
