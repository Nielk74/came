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

    fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + .5f).toInt()
}
