package com.nielk74.came.filters

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** High-quality capture renderer paired with each profile's inexpensive live preview matrix. */
object FilmProcessor {
    /**
     * Returns a new ARGB bitmap and never mutates [source]. PREVIEW bounds the long edge to 1280
     * pixels and skips the texture layers; CAPTURE preserves dimensions and applies halation and
     * grain. Both qualities render the stock's full colour response, selective foliage/sky
     * included.
     */
    fun apply(
        source: Bitmap,
        profile: FilmProfile,
        grainEnabled: Boolean,
        renderSeed: Long,
        quality: RenderQuality = RenderQuality.CAPTURE,
    ): Bitmap {
        require(source.width > 0 && source.height > 0)
        val input = if (quality == RenderQuality.PREVIEW &&
            maxOf(source.width, source.height) > PREVIEW_LONG_EDGE
        ) {
            val scale = PREVIEW_LONG_EDGE.toFloat() / maxOf(source.width, source.height)
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }

        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        render(pixels, width, height, profile, grainEnabled, renderSeed, quality)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        if (input !== source) input.recycle()
        return output
    }

    /**
     * The whole render, in place on a packed ARGB buffer. [apply] is the Bitmap-shaped wrapper
     * around this; keeping the pixel work Android-free lets it be measured and regression-tested
     * on the JVM against real camera output.
     */
    internal fun render(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: FilmProfile,
        grainEnabled: Boolean,
        renderSeed: Long,
        quality: RenderQuality = RenderQuality.CAPTURE,
    ) {
        ScenePreprocessor.apply(pixels, width, height)
        // Still part of developing the capture: recover the sky before the stock reads the scene,
        // so the film renders a sky that has its brightness and colour back rather than a pale one.
        SkyRecovery.apply(pixels, width, height)
        applyPointwise(pixels, profile)
        // Selective foliage/sky colour is part of the stock's colour response, not a texture
        // layer, so it runs at both qualities and the preview keeps matching the capture.
        if (profile.foliageTone.enabled) {
            SelectiveColor.applyFoliage(pixels, profile.foliageTone, profile.strength)
        }
        if (profile.skyTone.enabled) {
            SelectiveColor.applySky(pixels, width, height, profile.skyTone, profile.strength)
        }
        if (quality == RenderQuality.CAPTURE) {
            if (profile.halation.enabled) applyHalation(pixels, width, height, profile.halation)
            if (grainEnabled && profile.grain.enabled) {
                applyGrain(pixels, width, height, profile.grain, renderSeed)
            }
        }
    }

    private fun applyPointwise(pixels: IntArray, profile: FilmProfile) {
        val tables = Tables(profile)
        val matrix = profile.crossTalk
        val mono = profile.monochromeWeights
        val split = profile.splitTone
        val shadowTintLuma = luma(split.shadowR, split.shadowG, split.shadowB)
        val highlightTintLuma = luma(split.highlightR, split.highlightG, split.highlightB)

        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color ushr 24
            val sourceR = (color ushr 16) and 0xff
            val sourceG = (color ushr 8) and 0xff
            val sourceB = color and 0xff

            val redDensity: Float
            val greenDensity: Float
            val blueDensity: Float
            if (mono == null) {
                redDensity = tables.redNegative[sourceR]
                greenDensity = tables.greenNegative[sourceG]
                blueDensity = tables.blueNegative[sourceB]
            } else {
                val exposure = mono.red * SRGB_TO_LINEAR[sourceR] +
                    mono.green * SRGB_TO_LINEAR[sourceG] + mono.blue * SRGB_TO_LINEAR[sourceB]
                redDensity = negativeDensity(exposure, profile.red)
                greenDensity = negativeDensity(exposure, profile.green)
                blueDensity = negativeDensity(exposure, profile.blue)
            }

            val mixedR = (matrix.m00 * redDensity + matrix.m01 * greenDensity +
                matrix.m02 * blueDensity).coerceIn(0f, 1f)
            val mixedG = (matrix.m10 * redDensity + matrix.m11 * greenDensity +
                matrix.m12 * blueDensity).coerceIn(0f, 1f)
            val mixedB = (matrix.m20 * redDensity + matrix.m21 * greenDensity +
                matrix.m22 * blueDensity).coerceIn(0f, 1f)

            var renderedR = tables.positive(mixedR)
            var renderedG = tables.positive(mixedG)
            var renderedB = tables.positive(mixedB)
            val renderedLuma = luma(renderedR, renderedG, renderedB)
            renderedR = renderedLuma + (renderedR - renderedLuma) * profile.saturation
            renderedG = renderedLuma + (renderedG - renderedLuma) * profile.saturation
            renderedB = renderedLuma + (renderedB - renderedLuma) * profile.saturation

            val originalR = sourceR / 255f
            val originalG = sourceG / 255f
            val originalB = sourceB / 255f
            renderedR = originalR + (renderedR - originalR) * profile.strength
            renderedG = originalG + (renderedG - originalG) * profile.strength
            renderedB = originalB + (renderedB - originalB) * profile.strength

            if (split.amount > 0f) {
                val tone = luma(renderedR, renderedG, renderedB).coerceIn(0f, 1f)
                val highlightWeight = tone * tone * split.amount
                val shadowWeight = (1f - tone) * (1f - tone) * split.amount
                renderedR += (split.shadowR - shadowTintLuma) * shadowWeight +
                    (split.highlightR - highlightTintLuma) * highlightWeight
                renderedG += (split.shadowG - shadowTintLuma) * shadowWeight +
                    (split.highlightG - highlightTintLuma) * highlightWeight
                renderedB += (split.shadowB - shadowTintLuma) * shadowWeight +
                    (split.highlightB - highlightTintLuma) * highlightWeight
            }

            pixels[index] = alpha shl 24 or
                (toByte(renderedR) shl 16) or
                (toByte(renderedG) shl 8) or
                toByte(renderedB)
        }
    }

    private fun applyGrain(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: GrainProfile,
        renderSeed: Long,
    ) {
        val longEdge = maxOf(width, height)
        val field = ToneDrivenGrain.FieldSampler(width, longEdge, profile, renderSeed)
        val visibility = FloatArray(256) { tone ->
            ToneDrivenGrain.visibilityForTone(tone / 255f, profile)
        }
        for (y in 0 until height) {
            field.beginRow(y)
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val color = pixels[index]
                val red = (color ushr 16 and 0xff) / 255f
                val green = (color ushr 8 and 0xff) / 255f
                val blue = (color and 0xff) / 255f
                val tone = luma(red, green, blue)
                if (tone <= .001f || tone >= .999f) continue
                val toneVisibility = visibility[(tone * 255f).roundToInt().coerceIn(0, 255)]
                val crystal = field.sample(x)
                val shift = crystal * profile.amount * toneVisibility * .34f
                val endpoint = 4f * tone * (1f - tone)
                val chroma = shift * profile.chroma * endpoint * .15f
                val newR = red + shift + chroma
                val newG = green + shift - chroma * .45f
                val newB = blue + shift - chroma * .75f
                pixels[index] = color and -0x1000000 or
                    (toByte(newR) shl 16) or (toByte(newG) shl 8) or toByte(newB)
            }
        }
    }

    /**
     * Edge-driven red-orange halation in linear light.
     *
     * A broad, predominantly red base-reflection lobe is paired with a tighter emulsion-scatter
     * lobe. Both source masks come from the same pre-halation pixels, so the broad halo can never
     * seed the tight one. Each pixel's own unblurred source core is subtracted from the blurred
     * mask before compositing, which is what makes this a fringe *around* a highlight instead of a
     * red wash over it, and a receiver term keeps the spill off surfaces that are already bright.
     *
     * The masks live at quarter resolution to bound capture memory; halation is a low-frequency
     * bloom, but the subtracted core and the composite are both full-resolution so highlight edges
     * stay sharp. [profile] radii are authored against a 1600px long edge and rescaled here, so a
     * preview and a full-size export show the same halo.
     */
    internal fun applyHalation(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: HalationProfile,
    ) {
        val dimensionScale = maxOf(width, height) / HALATION_REFERENCE_EDGE
        val tightRadius = (profile.radius * dimensionScale).roundToInt().coerceIn(1, 48)
        val broadRadius = (tightRadius * 2.15f).roundToInt().coerceIn(tightRadius + 1, 96)

        val scale = 4
        val smallWidth = (width + scale - 1) / scale
        val smallHeight = (height + scale - 1) / scale
        val tightMask = halationMask(
            pixels, width, height, smallWidth, smallHeight, scale, profile, TIGHT_RED_BIAS,
        )
        val broadMask = halationMask(
            pixels, width, height, smallWidth, smallHeight, scale, profile, BROAD_RED_BIAS,
        )
        gaussianBlur(tightMask, smallWidth, smallHeight, (tightRadius / scale).coerceAtLeast(1))
        gaussianBlur(broadMask, smallWidth, smallHeight, (broadRadius / scale).coerceAtLeast(1))

        for (y in 0 until height) {
            val smallRow = minOf(y / scale, smallHeight - 1) * smallWidth
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val red = SRGB_TO_LINEAR[color ushr 16 and 0xff]
                val green = SRGB_TO_LINEAR[color ushr 8 and 0xff]
                val blue = SRGB_TO_LINEAR[color and 0xff]
                val sourceLuma = ColorMath.luminanceOfLinear(red, green, blue)
                val receiver = (1f - sourceLuma).coerceIn(0f, 1f).toDouble().pow(.65).toFloat()
                if (receiver <= 0f) continue

                val smallIndex = smallRow + minOf(x / scale, smallWidth - 1)
                val broadSpill = (broadMask[smallIndex] -
                    halationCore(sourceLuma, red, profile.threshold, BROAD_RED_BIAS))
                    .coerceAtLeast(0f)
                val tightSpill = (tightMask[smallIndex] -
                    halationCore(sourceLuma, red, profile.threshold, TIGHT_RED_BIAS))
                    .coerceAtLeast(0f)
                if (broadSpill <= 0f && tightSpill <= 0f) continue

                // Screen-composite each lobe so a halo adds light without clipping the receiver.
                var outR = red
                var outG = green
                var outB = blue
                if (broadSpill > 0f) {
                    val energy = (broadSpill * profile.strength * BROAD_STRENGTH * receiver)
                        .coerceIn(0f, .72f)
                    outR = 1f - (1f - outR) * (1f - energy * profile.tintR)
                    outG = 1f - (1f - outG) * (1f - energy * profile.tintG * .30f)
                    outB = 1f - (1f - outB) * (1f - energy * profile.tintB * .08f)
                }
                if (tightSpill > 0f) {
                    val energy = (tightSpill * profile.strength * TIGHT_STRENGTH * receiver)
                        .coerceIn(0f, .72f)
                    outR = 1f - (1f - outR) * (1f - energy * profile.tintR)
                    outG = 1f - (1f - outG) * (1f - energy * profile.tintG * .82f)
                    outB = 1f - (1f - outB) * (1f - energy * profile.tintB * .55f)
                }

                pixels[index] = color and -0x1000000 or
                    (ColorMath.toByte(ColorMath.linearToSrgb(outR)) shl 16) or
                    (ColorMath.toByte(ColorMath.linearToSrgb(outG)) shl 8) or
                    ColorMath.toByte(ColorMath.linearToSrgb(outB))
            }
        }
    }

    /**
     * Quarter-resolution source mask. Longer wavelengths penetrate further into the emulsion and
     * base, so biasing a lobe toward red lets a saturated practical light drive its own halo.
     */
    private fun halationMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        smallWidth: Int,
        smallHeight: Int,
        scale: Int,
        profile: HalationProfile,
        redSourceBias: Float,
    ): FloatArray {
        val mask = FloatArray(smallWidth * smallHeight)
        for (smallY in 0 until smallHeight) {
            // Average the block rather than point-sampling it: a specular highlight smaller than
            // the decimation stride would otherwise flicker in and out of its own halo.
            val yStart = smallY * scale
            val yEnd = minOf(yStart + scale, height)
            for (smallX in 0 until smallWidth) {
                val xStart = smallX * scale
                val xEnd = minOf(xStart + scale, width)
                var sum = 0f
                var count = 0
                for (y in yStart until yEnd) {
                    val row = y * width
                    for (x in xStart until xEnd) {
                        val color = pixels[row + x]
                        val red = SRGB_TO_LINEAR[color ushr 16 and 0xff]
                        val luminance = ColorMath.luminanceOfLinear(
                            red,
                            SRGB_TO_LINEAR[color ushr 8 and 0xff],
                            SRGB_TO_LINEAR[color and 0xff],
                        )
                        sum += halationCore(luminance, red, profile.threshold, redSourceBias)
                        count++
                    }
                }
                mask[smallY * smallWidth + smallX] = if (count > 0) sum / count else 0f
            }
        }
        return mask
    }

    private fun halationCore(
        linearLuma: Float,
        linearRed: Float,
        threshold: Float,
        redSourceBias: Float,
    ): Float {
        val source = linearLuma + (linearRed - linearLuma) * redSourceBias
        return ColorMath.smoothstep(threshold, 1f, source)
    }

    /**
     * Separable box-approximated Gaussian of a single-channel [plane], in place. Three box passes
     * are the standard fast approximation; one pass alone left visible square halos.
     */
    private fun gaussianBlur(plane: FloatArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val scratch = FloatArray(plane.size)
        repeat(3) {
            boxBlurHorizontal(plane, scratch, width, height, radius)
            boxBlurVertical(scratch, plane, width, height, radius)
        }
    }

    private fun boxBlurHorizontal(
        source: FloatArray,
        out: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val norm = 1f / (2 * radius + 1)
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (k in -radius..radius) sum += source[row + k.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                out[row + x] = sum * norm
                sum += source[row + (x + radius + 1).coerceIn(0, width - 1)] -
                    source[row + (x - radius).coerceIn(0, width - 1)]
            }
        }
    }

    private fun boxBlurVertical(
        source: FloatArray,
        out: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val norm = 1f / (2 * radius + 1)
        for (x in 0 until width) {
            var sum = 0f
            for (k in -radius..radius) sum += source[k.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                out[y * width + x] = sum * norm
                sum += source[(y + radius + 1).coerceIn(0, height - 1) * width + x] -
                    source[(y - radius).coerceIn(0, height - 1) * width + x]
            }
        }
    }

    private class Tables(profile: FilmProfile) {
        val redNegative = FloatArray(256) { negativeDensity(SRGB_TO_LINEAR[it], profile.red) }
        val greenNegative = FloatArray(256) { negativeDensity(SRGB_TO_LINEAR[it], profile.green) }
        val blueNegative = FloatArray(256) { negativeDensity(SRGB_TO_LINEAR[it], profile.blue) }
        private val positive = FloatArray(TABLE_SIZE + 1) { index ->
            linearToSrgb(printPositive(index / TABLE_SIZE.toFloat(), profile.print))
        }

        fun positive(density: Float): Float {
            val location = density.coerceIn(0f, 1f) * TABLE_SIZE
            val lower = location.toInt().coerceAtMost(TABLE_SIZE - 1)
            return positive[lower] + (positive[lower + 1] - positive[lower]) * (location - lower)
        }
    }

    private fun negativeDensity(exposure: Float, curve: ChannelCurve): Float {
        if (exposure <= 0f) return 0f
        if (exposure >= 1f) return 1f
        val x = exposure.coerceIn(EPSILON, 1f - EPSILON)
        val slope = 1f + curve.contrast.coerceIn(-.5f, 1f) * .34f
        val speed = ln(curve.gain.coerceIn(.5f, 2f))
        val toe = curve.toe.coerceIn(-.12f, .12f) * 4f * (1f - x) * (1f - x)
        val shoulder = curve.shoulder.coerceIn(0f, 1.2f) * .46f * x * x
        return logistic(logit(x) * slope + speed + toe - shoulder)
    }

    private fun printPositive(density: Float, curve: PrintCurve): Float {
        if (density <= 0f) return curve.blackPoint
        if (density >= 1f) return curve.paperWhite
        val x = density.coerceIn(EPSILON, 1f - EPSILON)
        val toe = curve.toe.coerceIn(-.5f, .8f) * (1f - x) * (1f - x)
        val shoulder = curve.shoulder.coerceIn(0f, 1.2f) * x * x
        val exposure = curve.exposureEv.coerceIn(-1f, 1f) * .6931472f
        val positive = logistic(logit(x) * curve.contrast.coerceIn(.65f, 1.35f) +
            exposure + toe - shoulder)
        return curve.blackPoint + positive * (curve.paperWhite - curve.blackPoint)
    }

    private fun logit(value: Float): Float = ln(value / (1f - value))
    private fun logistic(value: Float): Float = (1f / (1f + exp(-value))).coerceIn(0f, 1f)
    private fun luma(red: Float, green: Float, blue: Float): Float =
        ColorMath.luma(red, green, blue)

    private fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun linearToSrgb(value: Float): Float = ColorMath.linearToSrgb(value)

    private val SRGB_TO_LINEAR = ColorMath.SRGB_TO_LINEAR

    private const val EPSILON = .000001f
    private const val TABLE_SIZE = 4096
    private const val PREVIEW_LONG_EDGE = 1280

    /** Halation radii are authored against this long edge and rescaled to the actual output. */
    private const val HALATION_REFERENCE_EDGE = 1600f
    private const val TIGHT_RED_BIAS = .24f
    private const val BROAD_RED_BIAS = .58f
    private const val TIGHT_STRENGTH = .78f
    private const val BROAD_STRENGTH = .24f
}
