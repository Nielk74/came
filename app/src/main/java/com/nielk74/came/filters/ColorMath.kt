package com.nielk74.came.filters

import kotlin.math.pow

/**
 * Shared colour primitives for the film pipeline.
 *
 * The pixel buffers are display-referred sRGB, so any light-energy operation (halation spill,
 * luminance-preserving chroma moves) has to cross the transfer boundary explicitly instead of
 * applying Rec.709 coefficients to gamma-encoded values. Perceptual *gating* may still use the
 * encoded values — that is a deliberate, documented choice at each call site.
 */
internal object ColorMath {
    const val KR = .2126f
    const val KG = .7152f
    const val KB = .0722f

    /** IEC 61966-2-1 sRGB electro-optical transfer function, tabulated for the 8-bit inputs. */
    val SRGB_TO_LINEAR = FloatArray(256) { index -> srgbToLinear(index / 255f) }

    fun srgbToLinear(value: Float): Float {
        val c = value.coerceAtLeast(0f)
        return if (c <= .04045f) c / 12.92f
        else ((c + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    fun linearToSrgb(value: Float): Float {
        val c = value.coerceAtLeast(0f)
        return if (c <= .0031308f) 12.92f * c
        else 1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - .055f
    }

    /** Rec.709 weights applied to gamma-encoded values. For tone *gating* only. */
    fun luma(red: Float, green: Float, blue: Float): Float = KR * red + KG * green + KB * blue

    /** Relative linear-light luminance of a display-referred sRGB triplet. */
    fun linearLuma(red: Float, green: Float, blue: Float): Float =
        KR * srgbToLinear(red) + KG * srgbToLinear(green) + KB * srgbToLinear(blue)

    /** Relative luminance of an already-linear triplet. */
    fun luminanceOfLinear(red: Float, green: Float, blue: Float): Float =
        KR * red + KG * green + KB * blue

    fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Where a colour move starts easing off toward paper white, and where it is gone entirely. */
    const val PAPER_APPROACH = .74f
    const val PAPER_WHITE = .95f

    /**
     * One over the mids, falling to zero at paper white.
     *
     * White in the picture should be white, so every stage that adds colour shares this ramp rather
     * than choosing its own idea of where the top of the range begins. The print already rolled its
     * stock's highlight tint off here; the sky stages did not, which is how the brightest pixel
     * inside a sky region — a cloud — ended up taking the largest colour push in the frame.
     */
    fun paperWhiteRolloff(tone: Float): Float = 1f - smoothstep(PAPER_APPROACH, PAPER_WHITE, tone)

    fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + .5f).toInt()

    /**
     * Encodes a linear-light value straight to its 8-bit sRGB level.
     *
     * The exact transfer function costs a `pow` per channel, which the full-resolution grain and
     * halation passes would pay tens of millions of times. Interpolating a table is exact below
     * the transfer function's linear segment and stays far inside a single 8-bit step above it.
     */
    fun toByteFromLinear(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f) * ENCODE_LAST
        val lower = clamped.toInt()
        if (lower >= ENCODE_LAST) return 255
        val encoded = ENCODE[lower] + (ENCODE[lower + 1] - ENCODE[lower]) * (clamped - lower)
        return (encoded * 255f + .5f).toInt()
    }

    private const val ENCODE_LAST = 8_192
    private val ENCODE = FloatArray(ENCODE_LAST + 1) { linearToSrgb(it.toFloat() / ENCODE_LAST) }
}

/**
 * One pixel's colour, carried between pointwise stages without going back through 8 bits.
 *
 * The pointwise stages used to hand each other a packed ARGB buffer, which meant every one of them
 * read a byte, worked in floats, and rounded back to a byte for the next. Five stages in a row cost
 * five roundings, and they land hardest exactly where they are least affordable: below code 32 the
 * whole shadow range has 32 levels to carry, and a stage that darkens or compresses spends some of
 * them permanently. Passing this instead lets a stage read what the previous one actually computed.
 *
 * It is deliberately mutable and allocated once per pass rather than per pixel — a render touches
 * tens of millions of pixels, and a short-lived object for each of them is the one allocation
 * pattern this pipeline cannot afford.
 */
internal class Rgb {
    @JvmField var red: Float = 0f
    @JvmField var green: Float = 0f
    @JvmField var blue: Float = 0f

    fun set(red: Float, green: Float, blue: Float) {
        this.red = red
        this.green = green
        this.blue = blue
    }

    /** Unpacks the 8-bit channels of [color] into 0..1 floats. */
    fun setFrom(color: Int) {
        red = (color ushr 16 and 0xff) / 255f
        green = (color ushr 8 and 0xff) / 255f
        blue = (color and 0xff) / 255f
    }

    /** Brings every channel into range without rounding, for stages that assume 0..1 input. */
    fun clamp01() {
        red = red.coerceIn(0f, 1f)
        green = green.coerceIn(0f, 1f)
        blue = blue.coerceIn(0f, 1f)
    }

    /** Packs back to 8-bit under [alpha], which is the only point rounding is paid. */
    fun pack(alpha: Int): Int =
        alpha or (ColorMath.toByte(red) shl 16) or (ColorMath.toByte(green) shl 8) or
            ColorMath.toByte(blue)
}
