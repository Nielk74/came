package com.nielk74.came.filters

import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards how much each stock actually changes a photograph.
 *
 * The looks once measured under 1 ΔE against real Pixel 8 frames — the negative and print curves
 * very nearly cancelled, so the stock said almost nothing on top of the scene development that
 * every capture already receives. These bounds keep that from creeping back, and the upper bound
 * keeps a future tuning pass from overcorrecting into a look no longer recognisable as the scene.
 *
 * Distances are mean CIE76 over a synthetic scene, measured against the developed image rather
 * than the original so the film is isolated from the develop stage.
 */
class FilmLookStrengthTest {
    @Test
    fun everyStockMovesTheDevelopedImageEnoughToRead() {
        val scene = Scene()
        val developed = scene.pixels().also { ScenePreprocessor.apply(it, scene.width, scene.height) }

        FilmCatalog.profiles.forEach { profile ->
            val rendered = scene.render(profile)
            val distance = meanDeltaE(developed, rendered)
            assertTrue(
                "${profile.displayName} only moved the image by %.2f deltaE".format(distance),
                distance >= MIN_LOOK_DELTA_E,
            )
            assertTrue(
                "${profile.displayName} moved the image by %.2f deltaE".format(distance),
                distance <= MAX_LOOK_DELTA_E,
            )
        }
    }

    @Test
    fun theStocksAreDistinguishableFromEachOther() {
        val scene = Scene()
        val rendered = FilmCatalog.profiles.associate { it.displayName to scene.render(it) }
        rendered.entries.forEachIndexed { index, (name, pixels) ->
            rendered.entries.drop(index + 1).forEach { (otherName, other) ->
                val distance = meanDeltaE(pixels, other)
                assertTrue(
                    "$name and $otherName differ by only %.2f deltaE".format(distance),
                    distance >= MIN_STOCK_SEPARATION,
                )
            }
        }
    }

    @Test
    fun noStockCrushesTheEndpointsOfAnOrdinaryScene() {
        val scene = Scene()
        FilmCatalog.profiles.forEach { profile ->
            val rendered = scene.render(profile)
            val black = rendered.count { luma(it) <= .004f } / rendered.size.toFloat()
            val white = rendered.count { luma(it) >= .996f } / rendered.size.toFloat()
            assertTrue("${profile.displayName} crushed %.1f%% to black".format(black * 100), black < .12f)
            assertTrue("${profile.displayName} blew %.1f%% to white".format(white * 100), white < .12f)
        }
    }

    /** A synthetic frame with sky, foliage, skin, neutrals, saturated colour, and a highlight. */
    private class Scene {
        val width = 96
        val height = 72

        fun pixels(): IntArray = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                y < 22 -> rgb(96 + y, 148 + y, 214) // sky, brightening toward the horizon
                y < 40 -> rgb(70 + (x % 12) * 3, 108 + (x % 12) * 4, 52 + (x % 12) * 2) // foliage
                y < 52 -> rgb(224 - (x % 8) * 4, 172 - (x % 8) * 3, 142 - (x % 8) * 3) // skin
                y < 60 -> rgb(20 + x * 2, 20 + x * 2, 20 + x * 2) // neutral ramp
                y < 66 -> rgb(if (x < 32) 208 else 40, if (x in 32..63) 176 else 44, 60) // colour
                else -> rgb(246, 244, 238) // highlight
            }
        }

        fun render(profile: FilmProfile): IntArray = pixels().also {
            FilmProcessor.render(it, width, height, profile, grainEnabled = false, renderSeed = 3L)
        }

        private fun rgb(r: Int, g: Int, b: Int) = -0x1000000 or
            (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun meanDeltaE(a: IntArray, b: IntArray): Double {
        val left = FloatArray(3)
        val right = FloatArray(3)
        var sum = 0.0
        for (i in a.indices) {
            toLab(a[i], left)
            toLab(b[i], right)
            var squared = 0.0
            for (c in 0..2) {
                val delta = (left[c] - right[c]).toDouble()
                squared += delta * delta
            }
            sum += sqrt(squared)
        }
        return sum / a.size
    }

    private fun luma(color: Int) = ColorMath.luma(
        (color ushr 16 and 0xff) / 255f,
        (color ushr 8 and 0xff) / 255f,
        (color and 0xff) / 255f,
    )

    private fun toLab(color: Int, out: FloatArray) {
        val r = ColorMath.SRGB_TO_LINEAR[color ushr 16 and 0xff]
        val g = ColorMath.SRGB_TO_LINEAR[color ushr 8 and 0xff]
        val b = ColorMath.SRGB_TO_LINEAR[color and 0xff]
        val x = (.4124f * r + .3576f * g + .1805f * b) / .95047f
        val y = .2126f * r + .7152f * g + .0722f * b
        val z = (.0193f * r + .1192f * g + .9505f * b) / 1.08883f
        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)
        out[0] = 116f * fy - 16f
        out[1] = 500f * (fx - fy)
        out[2] = 200f * (fy - fz)
    }

    private fun labF(t: Float): Float =
        if (t > .008856f) Math.cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f

    private companion object {
        /** Comfortably above a just-noticeable difference, well under what the stocks now reach. */
        const val MIN_LOOK_DELTA_E = 4.0
        const val MAX_LOOK_DELTA_E = 40.0
        const val MIN_STOCK_SEPARATION = 1.0
    }
}
