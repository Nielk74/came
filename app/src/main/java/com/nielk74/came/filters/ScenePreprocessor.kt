package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Develops the camera's SDR JPEG into a stable, useful base before a film stock is applied.
 *
 * Pixel and other computational-camera JPEGs deliberately retain highlight and shadow latitude.
 * A guarded percentile curve uses that latitude without assuming every scene contains true black
 * or white. A low-resolution log-luminance mask then adds restrained local separation without
 * sharpening sensor noise or allocating another full-resolution image.
 */
internal object ScenePreprocessor {
    fun apply(pixels: IntArray, width: Int, height: Int): SceneAnalysis {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val analysis = analyze(pixels)
        val toneCurve = buildToneCurve(analysis)
        val localLuminance = createLocalLuminance(pixels, width, height, toneCurve)
        val blurredLuminance = boxBlur(
            input = localLuminance.values,
            width = localLuminance.width,
            height = localLuminance.height,
            radius = (min(localLuminance.width, localLuminance.height) / 24).coerceIn(2, 20),
        )

        for (y in 0 until height) {
            val localRow = min(y / localLuminance.blockSize, localLuminance.height - 1) *
                localLuminance.width
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val color = pixels[index]
                val alpha = color ushr 24
                val red = (color ushr 16 and 0xff) / 255f
                val green = (color ushr 8 and 0xff) / 255f
                val blue = (color and 0xff) / 255f
                val sourceLuminance = luma(red, green, blue)
                val globalLuminance = sample(toneCurve, sourceLuminance)
                val localBase = blurredLuminance[
                    localRow + min(x / localLuminance.blockSize, localLuminance.width - 1)
                ]
                // Open through the shadows and closing only toward paper white. This used to be
                // 4L(1-L), a parabola that fell to nothing at both ends — so the one stage that
                // could put separation back into the darkest tones was gated almost off exactly
                // there, and a shadow arrived flat because nothing had been allowed to work on it.
                val toneGate = smoothstep(0f, SHADOW_GATE_OPEN, globalLuminance) *
                    ColorMath.paperWhiteRolloff(globalLuminance)
                val detail = (
                    sample(LOG_LUMINANCE, globalLuminance) -
                        sample(LOG_LUMINANCE, localBase)
                ).coerceIn(-MAX_LOCAL_DETAIL, MAX_LOCAL_DETAIL)
                val edgeGate = (1f - abs(detail) / MAX_LOCAL_DETAIL)
                    .coerceIn(0f, 1f)
                    .let { it * it }
                val localExposure = detail * LOCAL_CONTRAST * toneGate * edgeGate
                // Second-order exp(x): within 0.01% here because |localExposure| <= .072.
                val developedLuminance = (
                    globalLuminance *
                        (1f + localExposure + .5f * localExposure * localExposure)
                ).coerceIn(0f, 1f)

                val redDelta = red - sourceLuminance
                val greenDelta = green - sourceLuminance
                val blueDelta = blue - sourceLuminance
                val chromaScale = gamutSafeChromaScale(
                    luminance = developedLuminance,
                    redDelta = redDelta,
                    greenDelta = greenDelta,
                    blueDelta = blueDelta,
                )
                pixels[index] = alpha shl 24 or
                    (toByte(developedLuminance + redDelta * chromaScale) shl 16) or
                    (toByte(developedLuminance + greenDelta * chromaScale) shl 8) or
                    toByte(developedLuminance + blueDelta * chromaScale)
            }
        }
        return analysis
    }

    fun analyze(pixels: IntArray): SceneAnalysis {
        require(pixels.isNotEmpty())
        val histogram = IntArray(HISTOGRAM_BINS)
        val stride = ceil(pixels.size / MAX_HISTOGRAM_SAMPLES.toDouble()).toInt()
            .coerceAtLeast(1)
        var samples = 0
        var index = 0
        while (index < pixels.size) {
            val color = pixels[index]
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            histogram[lumaByte(red, green, blue)]++
            samples++
            index += stride
        }

        val low = percentile(histogram, samples, LOW_PERCENTILE) / 255f
        val midtone = trimmedMean(histogram, samples, .10f, .90f) / 255f
        val high = percentile(histogram, samples, HIGH_PERCENTILE) / 255f
        val hasSceneRange = high - low >= MIN_SCENE_RANGE_FOR_LEVELS
        // Anchoring black on a percentile crushes exactly that share of the frame by construction.
        // Sampling nearer the true floor and then stopping short of it keeps the darkest tones as
        // shadow detail rather than as a black clip.
        val blackPoint = if (hasSceneRange) min(low, MAX_BLACK_POINT) * BLACK_POINT_HEADROOM else 0f
        val whitePoint = (if (hasSceneRange) max(high, MIN_WHITE_POINT) else 1f)
            .coerceAtLeast(blackPoint + MIN_RANGE)
        val normalizedMidtone = normalize(midtone, blackPoint, whitePoint)
        val midtoneTarget = normalizedMidtone.coerceIn(MIN_MIDPOINT, MAX_MIDPOINT)
        // A frame with no range to speak of — a night sky, a dark wall, a covered lens — has no
        // shadows to open, only a level. Reading its midtone as an under-exposure and lifting it
        // toward the middle would turn every one of them into flat grey, so the deep end of the
        // range is only offered to a scene that actually has one.
        val gammaFloor = if (hasSceneRange) MIN_GAMMA else MIN_GAMMA_WITHOUT_RANGE
        val gamma = if (normalizedMidtone in .001f..<.999f) {
            (ln(midtoneTarget) / ln(normalizedMidtone)).coerceIn(gammaFloor, MAX_GAMMA)
        } else {
            1f
        }
        val normalizedRange = ((high - low) / (whitePoint - blackPoint)).coerceIn(0f, 1f)
        val contrast = (
            MAX_CONTRAST - normalizedRange * (MAX_CONTRAST - MIN_CONTRAST)
        ).coerceIn(MIN_CONTRAST, MAX_CONTRAST)
        // How much of the frame is actually in the shadows, which the midtone cannot say. A street
        // under a bright sky meters a perfectly normal midtone and still puts a fifth of its pixels
        // near black; [gamma] is driven by the midtone and leaves exactly that frame alone.
        val shadowShare = shareBelow(histogram, samples, SHADOW_TONE)
        val shadowLift = if (hasSceneRange) {
            MAX_SHADOW_LIFT * smoothstep(SHADOW_SHARE_RISE, SHADOW_SHARE_FULL, shadowShare)
        } else {
            0f
        }
        return SceneAnalysis(
            blackPoint = blackPoint,
            whitePoint = whitePoint,
            gamma = gamma,
            contrast = contrast,
            shadowLift = shadowLift,
            sourceLow = low,
            sourceMidtone = midtone,
            sourceHigh = high,
            histogramSamples = samples,
        )
    }

    fun toneMap(luminance: Float, analysis: SceneAnalysis): Float {
        val normalized = normalize(luminance, analysis.blackPoint, analysis.whitePoint)
        val exposed = normalized.toDouble().pow(analysis.gamma.toDouble()).toFloat()
        val sCurve = exposed + analysis.contrast * (exposed - .5f) *
            4f * exposed * (1f - exposed)
        // The toe. Zero at both ends, so black stays black and white stays white, but it steepens
        // the bottom of the curve by (1 + shadowLift) — two shadow tones a level apart leave it
        // further apart than they arrived, which is what shadow detail actually is. The S-curve
        // above can only take contrast out of the shadows; this is the shape that puts it back.
        val above = 1f - sCurve
        val toe = analysis.shadowLift * sCurve * above * above * above * above * above * above * above
        return (sCurve + toe).coerceIn(0f, 1f)
    }

    private fun createLocalLuminance(
        pixels: IntArray,
        width: Int,
        height: Int,
        toneCurve: FloatArray,
    ): LocalLuminance {
        val blockSize = (max(width, height) / LOCAL_LONG_EDGE).coerceAtLeast(MIN_BLOCK_SIZE)
        val smallWidth = (width + blockSize - 1) / blockSize
        val smallHeight = (height + blockSize - 1) / blockSize
        val values = FloatArray(smallWidth * smallHeight)
        for (smallY in 0 until smallHeight) {
            val centerY = min(smallY * blockSize + blockSize / 2, height - 1)
            for (smallX in 0 until smallWidth) {
                val centerX = min(smallX * blockSize + blockSize / 2, width - 1)
                val color = pixels[centerY * width + centerX]
                values[smallY * smallWidth + smallX] = sample(
                    toneCurve,
                    luma(
                        (color ushr 16 and 0xff) / 255f,
                        (color ushr 8 and 0xff) / 255f,
                        (color and 0xff) / 255f,
                    ),
                )
            }
        }
        return LocalLuminance(values, smallWidth, smallHeight, blockSize)
    }

    private fun gamutSafeChromaScale(
        luminance: Float,
        redDelta: Float,
        greenDelta: Float,
        blueDelta: Float,
    ): Float {
        var scale = CHROMA_PRESERVATION
        fun constrain(delta: Float) {
            if (delta > 0f) scale = min(scale, (1f - luminance) / delta)
            if (delta < 0f) scale = min(scale, luminance / -delta)
        }
        constrain(redDelta)
        constrain(greenDelta)
        constrain(blueDelta)
        return scale.coerceIn(0f, CHROMA_PRESERVATION)
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

    /** The share of the frame darker than [tone], as a fraction of the encoded range. */
    private fun shareBelow(histogram: IntArray, sampleCount: Int, tone: Float): Float {
        if (sampleCount <= 0) return 0f
        val last = (tone * 255f).toInt().coerceIn(0, histogram.lastIndex)
        var counted = 0
        for (index in 0..last) counted += histogram[index]
        return counted.toFloat() / sampleCount
    }

    private fun percentile(histogram: IntArray, sampleCount: Int, fraction: Float): Int {
        val target = (sampleCount * fraction).roundToInt().coerceIn(0, sampleCount - 1)
        var cumulative = 0
        for (index in histogram.indices) {
            cumulative += histogram[index]
            if (cumulative > target) return index
        }
        return histogram.lastIndex
    }

    private fun trimmedMean(
        histogram: IntArray,
        sampleCount: Int,
        lowFraction: Float,
        highFraction: Float,
    ): Float {
        val start = (sampleCount * lowFraction).roundToInt()
        val end = (sampleCount * highFraction).roundToInt().coerceAtLeast(start + 1)
        var cumulative = 0
        var included = 0
        var weightedSum = 0L
        for (value in histogram.indices) {
            val next = cumulative + histogram[value]
            val count = (min(next, end) - max(cumulative, start)).coerceAtLeast(0)
            weightedSum += value.toLong() * count
            included += count
            cumulative = next
            if (cumulative >= end) break
        }
        return if (included > 0) weightedSum.toFloat() / included else 127.5f
    }

    private fun buildToneCurve(analysis: SceneAnalysis): FloatArray =
        FloatArray(LUMINANCE_LUT_SIZE) { index ->
            toneMap(index.toFloat() / LUMINANCE_LUT_LAST, analysis)
        }

    private fun sample(table: FloatArray, value: Float): Float =
        table[(value.coerceIn(0f, 1f) * LUMINANCE_LUT_LAST + .5f).toInt()]

    private fun normalize(value: Float, blackPoint: Float, whitePoint: Float): Float =
        ((value - blackPoint) / (whitePoint - blackPoint)).coerceIn(0f, 1f)

    private fun luma(red: Float, green: Float, blue: Float): Float =
        .2126f * red + .7152f * green + .0722f * blue

    private fun lumaByte(red: Int, green: Int, blue: Int): Int =
        ((54 * red + 183 * green + 19 * blue + 128) shr 8).coerceIn(0, 255)

    private fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private data class LocalLuminance(
        val values: FloatArray,
        val width: Int,
        val height: Int,
        val blockSize: Int,
    )

    private const val HISTOGRAM_BINS = 256
    private const val LUMINANCE_LUT_SIZE = 1_024
    private const val LUMINANCE_LUT_LAST = LUMINANCE_LUT_SIZE - 1
    private const val MAX_HISTOGRAM_SAMPLES = 65_536
    private const val LOW_PERCENTILE = .005f
    private const val HIGH_PERCENTILE = .995f
    private const val BLACK_POINT_HEADROOM = .75f
    private const val MAX_BLACK_POINT = .10f
    private const val MIN_WHITE_POINT = .90f
    private const val MIN_RANGE = .40f
    private const val MIN_SCENE_RANGE_FOR_LEVELS = .12f
    private const val MIN_MIDPOINT = .42f
    private const val MAX_MIDPOINT = .58f
    /**
     * The floor was .88, which is well inside the range real scenes ask for: a backlit street
     * computes about .63 here and was handed .88, so the metering worked out the lift the frame
     * needed and then threw most of it away. Nothing asks to be darkened, so the ceiling stays.
     */
    private const val MIN_GAMMA = .60f
    private const val MIN_GAMMA_WITHOUT_RANGE = .88f
    private const val MAX_GAMMA = 1.12f

    /** Below this the frame counts as shadow for the purpose of deciding how much toe to add. */
    private const val SHADOW_TONE = .16f
    private const val SHADOW_SHARE_RISE = .04f
    private const val SHADOW_SHARE_FULL = .20f
    private const val MAX_SHADOW_LIFT = .80f

    /** Where the local-contrast gate has finished opening above black. */
    private const val SHADOW_GATE_OPEN = .04f
    /**
     * The develop stage sets a stable exposure; the stock supplies the look.
     *
     * These were tuned when the film profiles were nearly tonally neutral and the S-curve here was
     * carrying the contrast on their behalf. Now that each stock renders its own authored response,
     * that curve compounds with the film's and the result reads as overcooked, so the develop stage
     * steps back and leaves the tonal character to the emulsion.
     */
    private const val MIN_CONTRAST = .05f
    private const val MAX_CONTRAST = .14f
    private const val LOCAL_LONG_EDGE = 448
    private const val MIN_BLOCK_SIZE = 6
    private const val LOCAL_EPSILON = .035f
    private const val LOCAL_CONTRAST = .12f
    private const val MAX_LOCAL_DETAIL = .40f
    private const val CHROMA_PRESERVATION = 1.02f
    private val LOG_LUMINANCE = FloatArray(LUMINANCE_LUT_SIZE) { index ->
        ln(index.toFloat() / LUMINANCE_LUT_LAST + LOCAL_EPSILON)
    }
}

internal data class SceneAnalysis(
    val blackPoint: Float,
    val whitePoint: Float,
    val gamma: Float,
    val contrast: Float,
    val shadowLift: Float,
    val sourceLow: Float,
    val sourceMidtone: Float,
    val sourceHigh: Float,
    val histogramSamples: Int,
)
