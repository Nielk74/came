package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.linearLuma
import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.luma
import com.nielk74.came.filters.ColorMath.luminanceOfLinear
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.srgbToLinear
import kotlin.math.abs

/**
 * Stock-specific vegetation and sky colour, applied after the pointwise negative/print response.
 *
 * Both moves are deliberately *selective*. A global green- or blue-channel rotation would drag
 * skin, clothing, and painted surfaces with it; the character these stocks are known for lives in
 * how foliage and open sky render, not in every green or blue pixel. Each stage therefore gates on
 * a soft hue/chroma/luminance likelihood — the sky additionally on connectivity to the top frame
 * edge — and restores the original linear-light luminance afterwards, so these change colour
 * rather than exposure.
 *
 * Adapted from ricoh-gr3-android's DevelopPipeline, operating in place on the packed ARGB buffer
 * that the camera pipeline already owns rather than on three full-resolution float planes.
 */
internal object SelectiveColor {
    /**
     * Rotate vegetation-like yellow-green and green pixels toward cyan-green.
     *
     * Skin and warm objects have red as their dominant channel, existing cyan sits outside the hue
     * window, and near-neutrals fail the saturation gate, so the mask stays on plausible foliage.
     */
    fun applyFoliage(pixels: IntArray, tone: FoliageTone, layerMix: Float) {
        val hueAmount = (tone.cyanShift * layerMix).coerceIn(0f, 1f)
        val saturationAmount = (tone.saturationBoost * layerMix).coerceIn(0f, .50f)
        if (hueAmount <= 0f && saturationAmount <= 0f) return

        val shifted = FloatArray(3)
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = (color ushr 16 and 0xff) / 255f
            val green = (color ushr 8 and 0xff) / 255f
            val blue = (color and 0xff) / 255f

            val weight = foliageGreenLikelihood(red, green, blue)
            if (weight <= 0f) continue
            val outputLuma = linearLuma(red, green, blue)
            if (outputLuma <= 0f) continue

            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            val delta = max - min
            if (delta <= 0f || max <= 0f) continue

            val hue = 60f * ((blue - red) / delta + 2f)
            val shiftedHue = hue + (FOLIAGE_TARGET_HUE - hue) * hueAmount * weight
            hsvToRgb(shiftedHue, delta / max, max, shifted)

            pixels[index] = lumaMatchedChroma(
                alpha = color and -0x1000000,
                targetR = shifted[0],
                targetG = shifted[1],
                targetB = shifted[2],
                outputLuma = outputLuma,
                chromaScale = 1f + saturationAmount * weight,
            )
        }
    }

    /**
     * Move blue sky toward cyan without rotating blue everywhere in the frame.
     *
     * A row-wise connected-component pass accepts only blue/cyan pixels whose run reaches the top
     * edge, which rejects isolated blue clothing, signage, and interior objects while avoiding a
     * full-frame flood-fill buffer on a multi-megapixel capture.
     */
    fun applySky(pixels: IntArray, width: Int, height: Int, tone: SkyTone, layerMix: Float) {
        val hueAmount = (tone.cyanShift * layerMix).coerceIn(0f, .45f)
        val saturationAmount = (tone.saturationBoost * layerMix).coerceIn(0f, .50f)
        if ((hueAmount <= 0f && saturationAmount <= 0f) || width <= 0 || height <= 0) return

        var previousConnected = BooleanArray(width)
        var currentConnected = BooleanArray(width)
        val weights = FloatArray(width)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val color = pixels[row + x]
                weights[x] = skyBlueLikelihood(
                    (color ushr 16 and 0xff) / 255f,
                    (color ushr 8 and 0xff) / 255f,
                    (color and 0xff) / 255f,
                )
                currentConnected[x] = false
            }

            var x = 0
            while (x < width) {
                while (x < width && weights[x] <= SKY_CONNECT_THRESHOLD) x++
                if (x >= width) break
                val start = x
                while (x < width && weights[x] > SKY_CONNECT_THRESHOLD) x++
                val end = x - 1

                var connected = y == 0
                var probe = (start - 1).coerceAtLeast(0)
                val probeEnd = (end + 1).coerceAtMost(width - 1)
                while (!connected && probe <= probeEnd) {
                    connected = previousConnected[probe]
                    probe++
                }
                if (!connected) continue

                for (column in start..end) {
                    currentConnected[column] = true
                    val weight = weights[column]
                    val hueMix = hueAmount * weight
                    val chromaScale = 1f + saturationAmount * weight
                    if (hueMix <= 0f && chromaScale <= 1f) continue

                    val index = row + column
                    val color = pixels[index]
                    val red = (color ushr 16 and 0xff) / 255f
                    val green = (color ushr 8 and 0xff) / 255f
                    val blue = (color and 0xff) / 255f
                    val outputLuma = linearLuma(red, green, blue)
                    if (outputLuma <= 0f) continue

                    // Raising G and slightly easing B rotates blue toward cyan. Chroma is then
                    // expanded around the old luminance, with a gamut limit instead of clipping.
                    val blueGreenGap = (blue - green).coerceAtLeast(0f)
                    pixels[index] = lumaMatchedChroma(
                        alpha = color and -0x1000000,
                        targetR = red,
                        targetG = green + blueGreenGap * hueMix,
                        targetB = blue - blueGreenGap * hueMix * .08f,
                        outputLuma = outputLuma,
                        chromaScale = chromaScale,
                    )
                }
            }

            val swap = previousConnected
            previousConnected = currentConnected
            currentConnected = swap
        }
    }

    /**
     * Put the target's hue/chroma around [outputLuma], then expand chroma by [chromaScale]. One
     * shared gamut scale keeps every channel in range without clipping them independently, so hue
     * and the exact linear-light luminance both survive the saturation boost.
     */
    private fun lumaMatchedChroma(
        alpha: Int,
        targetR: Float,
        targetG: Float,
        targetB: Float,
        outputLuma: Float,
        chromaScale: Float,
    ): Int {
        val linearR = srgbToLinear(targetR)
        val linearG = srgbToLinear(targetG)
        val linearB = srgbToLinear(targetB)
        val targetLuma = luminanceOfLinear(linearR, linearG, linearB)
        val deltaR = linearR - targetLuma
        val deltaG = linearG - targetLuma
        val deltaB = linearB - targetLuma
        val scale = minOf(
            chromaScale.coerceIn(0f, 1.60f),
            chromaLimit(outputLuma, deltaR),
            chromaLimit(outputLuma, deltaG),
            chromaLimit(outputLuma, deltaB),
        )
        return alpha or
            (ColorMath.toByte(linearToSrgb(outputLuma + deltaR * scale)) shl 16) or
            (ColorMath.toByte(linearToSrgb(outputLuma + deltaG * scale)) shl 8) or
            ColorMath.toByte(linearToSrgb(outputLuma + deltaB * scale))
    }

    private fun chromaLimit(luma: Float, delta: Float): Float = when {
        delta > 0f -> (1f - luma) / delta
        delta < 0f -> luma / -delta
        else -> Float.POSITIVE_INFINITY
    }

    /** HSV-to-RGB into caller-owned [out], avoiding a per-pixel allocation. */
    private fun hsvToRgb(hue: Float, saturation: Float, value: Float, out: FloatArray) {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val chroma = value * saturation.coerceIn(0f, 1f)
        val x = chroma * (1f - abs(h % 2f - 1f))
        val m = value - chroma
        when (h.toInt().coerceIn(0, 5)) {
            0 -> { out[0] = chroma; out[1] = x; out[2] = 0f }
            1 -> { out[0] = x; out[1] = chroma; out[2] = 0f }
            2 -> { out[0] = 0f; out[1] = chroma; out[2] = x }
            3 -> { out[0] = 0f; out[1] = x; out[2] = chroma }
            4 -> { out[0] = x; out[1] = 0f; out[2] = chroma }
            else -> { out[0] = chroma; out[1] = 0f; out[2] = x }
        }
        out[0] += m
        out[1] += m
        out[2] += m
    }

    /** Soft likelihood for useful vegetation colour (roughly HSV 52-176 degrees). */
    private fun foliageGreenLikelihood(red: Float, green: Float, blue: Float): Float {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        if (green < max || max < .08f || delta < .025f) return 0f
        val saturation = delta / max.coerceAtLeast(1e-5f)
        if (saturation < .05f) return 0f

        val hue = 60f * ((blue - red) / delta + 2f)
        val hueWeight = smoothstep(52f, 70f, hue) * (1f - smoothstep(150f, 176f, hue))
        val saturationWeight = smoothstep(.05f, .18f, saturation)
        val light = luma(red, green, blue)
        val lightWeight = smoothstep(.035f, .12f, light) * (1f - smoothstep(.84f, .97f, light))
        val greenDominance = smoothstep(.005f, .055f, green - maxOf(red, blue))
        return (hueWeight * saturationWeight * lightWeight * greenDominance).coerceIn(0f, 1f)
    }

    /** Soft likelihood for photographic blue sky (roughly HSV 185-248 degrees). */
    private fun skyBlueLikelihood(red: Float, green: Float, blue: Float): Float {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        if (blue < max || max < .10f || delta < .025f) return 0f
        val saturation = delta / max.coerceAtLeast(1e-5f)
        if (saturation < .06f) return 0f

        val hue = 60f * ((red - green) / delta + 4f)
        val hueWeight = smoothstep(184f, 200f, hue) * (1f - smoothstep(232f, 248f, hue))
        val saturationWeight = smoothstep(.06f, .22f, saturation)
        val lightWeight = smoothstep(.07f, .24f, luma(red, green, blue))
        // Suppress purple/indigo objects while retaining cool dusk sky.
        val greenOverRed = smoothstep(-.025f, .12f, green - red)
        return (hueWeight * saturationWeight * lightWeight * greenOverRed).coerceIn(0f, 1f)
    }

    private const val FOLIAGE_TARGET_HUE = 160f
    private const val SKY_CONNECT_THRESHOLD = .03f
}
