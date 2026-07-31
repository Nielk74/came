package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.linearLuma
import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.luma
import com.nielk74.came.filters.ColorMath.luminanceOfLinear
import com.nielk74.came.filters.ColorMath.paperWhiteRolloff
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.srgbToLinear
import kotlin.math.abs

/**
 * Stock-specific vegetation and sky colour, applied after the pointwise negative/print response.
 *
 * Both moves are deliberately *selective*. A global green- or blue-channel rotation would drag
 * skin, clothing, and painted surfaces with it; the character these stocks are known for lives in
 * how foliage and open sky render, not in every green or blue pixel. Each stage therefore gates on
 * a soft hue/chroma/luminance likelihood — the sky additionally on the frame's [SkyRegion] — and
 * restores the original linear-light luminance afterwards, so these change colour rather than
 * exposure.
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
        val hueAmount = foliageHueAmount(tone, layerMix)
        val saturationAmount = foliageSaturationAmount(tone, layerMix)
        if (hueAmount <= 0f && saturationAmount <= 0f) return

        val pixel = Rgb()
        for (index in pixels.indices) {
            pixel.setFrom(pixels[index])
            shadeFoliage(pixel, hueAmount, saturationAmount)
            pixels[index] = pixel.pack(pixels[index] and -0x1000000)
        }
    }

    fun foliageHueAmount(tone: FoliageTone, layerMix: Float): Float =
        (tone.cyanShift * layerMix).coerceIn(0f, 1f)

    fun foliageSaturationAmount(tone: FoliageTone, layerMix: Float): Float =
        (tone.saturationBoost * layerMix).coerceIn(0f, .50f)

    /** [applyFoliage] for one pixel already in flight, so the chain need not round-trip it. */
    fun shadeFoliage(pixel: Rgb, hueAmount: Float, saturationAmount: Float) {
        val red = pixel.red
        val green = pixel.green
        val blue = pixel.blue

        val weight = foliageGreenLikelihood(red, green, blue)
        if (weight <= 0f) return
        val outputLuma = linearLuma(red, green, blue)
        if (outputLuma <= 0f) return

        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        if (delta <= 0f || max <= 0f) return

        val hue = 60f * ((blue - red) / delta + 2f)
        val shiftedHue = hue + (FOLIAGE_TARGET_HUE - hue) * hueAmount * weight
        // Writes the shifted colour into [pixel] itself; lumaMatchedChroma reads its targets into
        // locals before writing, so using it as its own scratch is safe and saves an allocation.
        hsvToRgb(shiftedHue, delta / max, max, pixel)
        lumaMatchedChroma(
            pixel = pixel,
            targetR = pixel.red,
            targetG = pixel.green,
            targetB = pixel.blue,
            outputLuma = outputLuma,
            chromaScale = 1f + saturationAmount * weight,
        )
    }

    /**
     * Move blue sky toward cyan without rotating blue everywhere in the frame.
     *
     * The hue window does most of the work; [region] supplies the rest, holding the shift off blue
     * objects lower in the picture than the sky ever reaches. It replaces a row-wise connectivity
     * pass that had to be able to trace a path back to the top edge — anything spanning the frame,
     * a wire or a roof edge, cut every sky pixel below it out of the shift while the sky above kept
     * it, and the two sides of that line then rendered differently.
     */
    fun applySky(
        pixels: IntArray,
        width: Int,
        height: Int,
        region: SkyRegion,
        tone: SkyTone,
        layerMix: Float,
    ) {
        val hueAmount = skyHueAmount(tone, layerMix)
        val saturationAmount = skySaturationAmount(tone, layerMix)
        if ((hueAmount <= 0f && saturationAmount <= 0f) || width <= 0 || height <= 0) return

        val pixel = Rgb()
        for (y in 0 until height) {
            val vertical = region.weightAt(y, height)
            if (vertical <= 0f) continue
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                pixel.setFrom(pixels[index])
                shadeSky(pixel, hueAmount, saturationAmount, vertical)
                pixels[index] = pixel.pack(pixels[index] and -0x1000000)
            }
        }
    }

    fun skyHueAmount(tone: SkyTone, layerMix: Float): Float =
        (tone.cyanShift * layerMix).coerceIn(0f, .45f)

    fun skySaturationAmount(tone: SkyTone, layerMix: Float): Float =
        (tone.saturationBoost * layerMix).coerceIn(0f, .50f)

    /**
     * [applySky] for one pixel already in flight.
     *
     * The stock's sky colour is rolled off as the pixel approaches paper white, on the same ramp
     * the print uses for its own highlight tint. Without it this stage had no highlight limit at
     * all: the brightest pixel inside the sky region took the largest hue rotation and the largest
     * chroma expansion, which is precisely a cloud, and it left one leaning cyan.
     */
    fun shadeSky(pixel: Rgb, hueAmount: Float, saturationAmount: Float, vertical: Float) {
        val red = pixel.red
        val green = pixel.green
        val blue = pixel.blue

        val weight = skyBlueLikelihood(red, green, blue) * vertical *
            paperWhiteRolloff(luma(red, green, blue))
        if (weight <= 0f) return
        val hueMix = hueAmount * weight
        val chromaScale = 1f + saturationAmount * weight
        if (hueMix <= 0f && chromaScale <= 1f) return
        val outputLuma = linearLuma(red, green, blue)
        if (outputLuma <= 0f) return

        // Raising G and slightly easing B rotates blue toward cyan. Chroma is then
        // expanded around the old luminance, with a gamut limit instead of clipping.
        val blueGreenGap = (blue - green).coerceAtLeast(0f)
        lumaMatchedChroma(
            pixel = pixel,
            targetR = red,
            targetG = green + blueGreenGap * hueMix,
            targetB = blue - blueGreenGap * hueMix * .08f,
            outputLuma = outputLuma,
            chromaScale = chromaScale,
        )
    }

    /**
     * Put the target's hue/chroma around [outputLuma], then expand chroma by [chromaScale]. One
     * shared gamut scale keeps every channel in range without clipping them independently, so hue
     * and the exact linear-light luminance both survive the saturation boost.
     */
    private fun lumaMatchedChroma(
        pixel: Rgb,
        targetR: Float,
        targetG: Float,
        targetB: Float,
        outputLuma: Float,
        chromaScale: Float,
    ) {
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
        pixel.set(
            linearToSrgb(outputLuma + deltaR * scale),
            linearToSrgb(outputLuma + deltaG * scale),
            linearToSrgb(outputLuma + deltaB * scale),
        )
    }

    private fun chromaLimit(luma: Float, delta: Float): Float = when {
        delta > 0f -> (1f - luma) / delta
        delta < 0f -> luma / -delta
        else -> Float.POSITIVE_INFINITY
    }

    /** HSV-to-RGB into caller-owned [out], avoiding a per-pixel allocation. */
    private fun hsvToRgb(hue: Float, saturation: Float, value: Float, out: Rgb) {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val chroma = value * saturation.coerceIn(0f, 1f)
        val x = chroma * (1f - abs(h % 2f - 1f))
        val m = value - chroma
        when (h.toInt().coerceIn(0, 5)) {
            0 -> out.set(chroma, x, 0f)
            1 -> out.set(x, chroma, 0f)
            2 -> out.set(0f, chroma, x)
            3 -> out.set(0f, x, chroma)
            4 -> out.set(x, 0f, chroma)
            else -> out.set(chroma, 0f, x)
        }
        out.set(out.red + m, out.green + m, out.blue + m)
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
}
