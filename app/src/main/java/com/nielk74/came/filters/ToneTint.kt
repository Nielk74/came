package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.toByte

/**
 * The colour a print carries by tone: blue through the low-mids, warmth above them, white at the top.
 *
 * A computational-camera JPEG is close to colorimetrically neutral all the way up its range, and
 * neutral is the one thing colour film never is. What a viewer reads as film is largely this — the
 * shadow side of the scale sitting slightly cool, the tones the subject actually occupies sitting
 * slightly warm, and the two meeting without the picture looking tinted.
 *
 * The highlights are deliberately left out of it. Paper white is white, so both lobes are back to
 * zero well before the top of the range: a cloud, a shirt, a wall in sun, and the blown edge of a
 * window all keep the neutral they arrived with, and nothing here can put a cast on them.
 *
 * Each lobe is a chroma move, not an exposure one: the tint's own luminance is subtracted before it
 * is added, so the pixel changes colour at nearly constant brightness. It runs once for every stock,
 * after the print, which is what makes it read as the base the whole set is printed on rather than
 * as another authored per-stock response.
 */
internal object ToneTint {
    fun apply(pixels: IntArray) {
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff

            val tone = lumaByte(red, green, blue)
            val cool = COOL_WEIGHT[tone]
            val warm = WARM_WEIGHT[tone]
            if (cool <= 0f && warm <= 0f) continue

            pixels[index] = color and -0x1000000 or
                (toByte(red / 255f + COOL_R * cool + WARM_R * warm) shl 16) or
                (toByte(green / 255f + COOL_G * cool + WARM_G * warm) shl 8) or
                toByte(blue / 255f + COOL_B * cool + WARM_B * warm)
        }
    }

    /** Rises over [rise], holds between [full] and [hold], and is gone by [fall]. */
    private fun lobe(tone: Float, rise: Float, full: Float, hold: Float, fall: Float): Float =
        smoothstep(rise, full, tone) * (1f - smoothstep(hold, fall, tone))

    private fun lumaByte(red: Int, green: Int, blue: Int): Int =
        ((54 * red + 183 * green + 19 * blue + 128) shr 8).coerceIn(0, 255)

    /** Blue, placed low enough to colour the shadow side without reaching the subject. */
    private const val COOL_AMOUNT = .030f
    private const val COOL_RISE = .06f
    private const val COOL_FULL = .26f
    private const val COOL_HOLD = .44f
    private const val COOL_FALL = .60f

    /** Warmth across the tones a subject actually occupies, and clear of the highlights. */
    private const val WARM_AMOUNT = .030f
    private const val WARM_RISE = .44f
    private const val WARM_FULL = .58f
    private const val WARM_HOLD = .74f
    private const val WARM_FALL = .86f

    private val COOL_WEIGHT = FloatArray(256) { index ->
        lobe(index / 255f, COOL_RISE, COOL_FULL, COOL_HOLD, COOL_FALL) * COOL_AMOUNT
    }
    private val WARM_WEIGHT = FloatArray(256) { index ->
        lobe(index / 255f, WARM_RISE, WARM_FULL, WARM_HOLD, WARM_FALL) * WARM_AMOUNT
    }

    // Each tint with its own luminance subtracted, so adding it moves colour and not exposure.
    private const val COOL_R = -ColorMath.KB
    private const val COOL_G = -ColorMath.KB
    private const val COOL_B = 1f - ColorMath.KB

    private const val WARM_GREEN = .55f
    private const val WARM_LUMA = ColorMath.KR + ColorMath.KG * WARM_GREEN
    private const val WARM_R = 1f - WARM_LUMA
    private const val WARM_G = WARM_GREEN - WARM_LUMA
    private const val WARM_B = -WARM_LUMA
}
