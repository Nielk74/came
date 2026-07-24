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
     * pixels and skips spatial layers; CAPTURE preserves dimensions and applies halation/grain.
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
        ScenePreprocessor.apply(pixels, width, height)
        applyPointwise(pixels, profile)
        if (quality == RenderQuality.CAPTURE) {
            if (profile.halation.enabled) applyHalation(pixels, width, height, profile.halation)
            if (grainEnabled && profile.grain.enabled) {
                applyGrain(pixels, width, height, profile.grain, renderSeed)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        if (input !== source) input.recycle()
        return output
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

    /** A quarter-resolution highlight bloom keeps full-resolution capture memory bounded. */
    private fun applyHalation(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: HalationProfile,
    ) {
        val scale = 4
        val smallWidth = (width + scale - 1) / scale
        val smallHeight = (height + scale - 1) / scale
        val highlights = FloatArray(smallWidth * smallHeight)
        for (smallY in 0 until smallHeight) {
            for (smallX in 0 until smallWidth) {
                val color = pixels[minOf(smallY * scale, height - 1) * width +
                    minOf(smallX * scale, width - 1)]
                val tone = luma(
                    (color ushr 16 and 0xff) / 255f,
                    (color ushr 8 and 0xff) / 255f,
                    (color and 0xff) / 255f,
                )
                highlights[smallY * smallWidth + smallX] =
                    ((tone - profile.threshold) / (1f - profile.threshold)).coerceIn(0f, 1f)
            }
        }
        val blurred = boxBlur(highlights, smallWidth, smallHeight,
            (profile.radius / scale).coerceAtLeast(1))
        for (y in 0 until height) {
            val smallRow = minOf(y / scale, smallHeight - 1) * smallWidth
            for (x in 0 until width) {
                val bloom = blurred[smallRow + minOf(x / scale, smallWidth - 1)] *
                    profile.strength * .10f
                if (bloom <= 0f) continue
                val index = y * width + x
                val color = pixels[index]
                pixels[index] = color and -0x1000000 or
                    (toByte((color ushr 16 and 0xff) / 255f + bloom * profile.tintR) shl 16) or
                    (toByte((color ushr 8 and 0xff) / 255f + bloom * profile.tintG) shl 8) or
                    toByte((color and 0xff) / 255f + bloom * profile.tintB)
            }
        }
    }

    private fun boxBlur(input: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(input.size)
        for (y in 0 until height) {
            var sum = 0f
            for (x in -radius..radius) sum += input[y * width + x.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                horizontal[y * width + x] = sum / (radius * 2 + 1)
                sum -= input[y * width + (x - radius).coerceIn(0, width - 1)]
                sum += input[y * width + (x + radius + 1).coerceIn(0, width - 1)]
            }
        }
        val output = FloatArray(input.size)
        for (x in 0 until width) {
            var sum = 0f
            for (y in -radius..radius) sum += horizontal[y.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                output[y * width + x] = sum / (radius * 2 + 1)
                sum -= horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                sum += horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            }
        }
        return output
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
        .2126f * red + .7152f * green + .0722f * blue

    private fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun linearToSrgb(value: Float): Float = when {
        value <= 0f -> 0f
        value <= .0031308f -> 12.92f * value
        else -> 1.055f * value.toDouble().pow(1.0 / 2.4).toFloat() - .055f
    }

    private val SRGB_TO_LINEAR = FloatArray(256) { index ->
        val value = index / 255f
        if (value <= .04045f) value / 12.92f
        else ((value + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    private const val EPSILON = .000001f
    private const val TABLE_SIZE = 4096
    private const val PREVIEW_LONG_EDGE = 1280
}
