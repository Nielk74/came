package com.nielk74.came.filters

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Resolution-independent film-plane grain, driven by tone rather than by local detail.
 *
 * The random field lives in physical film coordinates, not output pixels. Crystal impulses sit on
 * an infinite, non-repeating hashed lattice and are convolved with a compact tent response, which
 * is a practical analytic approximation to a stochastic Boolean grain model.
 *
 * What matters most is that the tent is integrated *analytically over each output pixel's film
 * footprint* rather than point-sampled. A preview pixel then holds the area-average of the very
 * field a full-resolution export samples, so grain keeps its physical size instead of turning into
 * soft blur at high resolution and coarse blotches at low. Sampling the lattice bilinearly, as this
 * did before, is value noise: it drifts with output size and reads as cloudy rather than crystalline.
 *
 * [GrainProfile.size] is one reference sample on a 3000-sample 35 mm long edge, so one unit is
 * about 12 micrometres of emulsion.
 */
object ToneDrivenGrain {
    /**
     * Perturbs [pixels] in place. [renderSeed] should be stable for one photograph but differ
     * between photographs, so a picture keeps its grain while two pictures never share a field.
     */
    fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: GrainProfile,
        renderSeed: Long,
    ) {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        if (!profile.enabled) return

        val footprint = REFERENCE_LONG_EDGE / maxOf(width, height).toFloat()
        val pitch = profile.size.coerceIn(MIN_CRYSTAL_PITCH, MAX_CRYSTAL_PITCH)
        val columnKernels = buildAxisKernels(width, footprint, pitch)
        val rowKernels = buildAxisKernels(height, footprint, pitch)
        val seed = mix64(profile.seed xor java.lang.Long.rotateLeft(renderSeed, 23))

        val firstColumnSite = columnKernels.first().firstSite
        val lastColumnSite = columnKernels.last().let { it.firstSite + it.weights.size - 1 }
        val lattice = Lattice(
            firstSiteColumn = firstColumnSite,
            siteColumns = lastColumnSite - firstColumnSite + 1,
            rowCapacity = rowKernels.maxOf { it.weights.size } + 2,
            seed = seed,
            clumping = profile.clumping,
        )

        val visibility = FloatArray(256) { visibilityForTone(it / 255f, profile) }
        val taps = rowKernels.maxOf { it.weights.size }
        val crystalRows = arrayOfNulls<FloatArray>(taps)
        val companionRows = arrayOfNulls<FloatArray>(taps)
        for (y in 0 until height) {
            val rowKernel = rowKernels[y]
            val rowWeights = rowKernel.weights
            // Resolve the lattice rows once per output row. Doing it per pixel costs tens of
            // millions of redundant lookups on a full-resolution capture.
            for (rowOffset in rowWeights.indices) {
                crystalRows[rowOffset] = lattice.crystals(rowKernel.firstSite + rowOffset)
                companionRows[rowOffset] = lattice.companions(rowKernel.firstSite + rowOffset)
            }
            val offset = y * width
            for (x in 0 until width) {
                val columnKernel = columnKernels[x]
                val columnWeights = columnKernel.weights
                val base = columnKernel.firstSite - firstColumnSite
                var primary = 0f
                var secondary = 0f
                for (rowOffset in rowWeights.indices) {
                    val rowWeight = rowWeights[rowOffset]
                    val crystals = crystalRows[rowOffset]!!
                    val companions = companionRows[rowOffset]!!
                    for (columnOffset in columnWeights.indices) {
                        val weight = rowWeight * columnWeights[columnOffset]
                        val site = base + columnOffset
                        primary += weight * crystals[site]
                        secondary += weight * companions[site]
                    }
                }
                composite(
                    pixels = pixels,
                    index = offset + x,
                    grain = primary * FIELD_GAIN,
                    companion = secondary * FIELD_GAIN,
                    profile = profile,
                    visibility = visibility,
                )
            }
        }
    }

    /**
     * Midtone-peaked density response shared by every pixel of the same luminance. It rolls off
     * at black and paper white while allowing faster stocks to retain texture in bright tones.
     */
    fun visibilityForTone(encodedLuminance: Float, profile: GrainProfile): Float {
        val luma = encodedLuminance.coerceIn(0f, 1f)
        val bias = profile.shadowBias.coerceIn(0f, 1f)
        val peak = .5f - .2f * bias
        val width = if (luma < peak) .32f + .25f * bias else .30f
        val distance = (luma - peak) / width
        val hump = exp(-.5f * distance * distance)
        val highlight = (1f - (luma - .75f).coerceAtLeast(0f) / .25f).coerceIn(.15f, 1f)
        val shadow = (.45f + luma / .06f).coerceIn(.45f, 1f)
        val rolled = hump * highlight * shadow
        val persistent = hump * shadow
        return (rolled + (persistent - rolled) * profile.highlightPersistence.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
    }

    /**
     * The integrated crystal field at one output pixel, normalized to a 3000-sample 35 mm long
     * edge so output size changes crystal integration rather than inventing a new texture.
     */
    fun crystalAt(
        x: Int,
        y: Int,
        longEdgePixels: Int,
        profile: GrainProfile,
        renderSeed: Long,
    ): Float {
        require(longEdgePixels > 0)
        val footprint = REFERENCE_LONG_EDGE / longEdgePixels.toFloat()
        val pitch = profile.size.coerceIn(MIN_CRYSTAL_PITCH, MAX_CRYSTAL_PITCH)
        val columnKernel = axisKernel(x, footprint, pitch)
        val rowKernel = axisKernel(y, footprint, pitch)
        val seed = mix64(profile.seed xor java.lang.Long.rotateLeft(renderSeed, 23))
        val tailScale = 1.8f * profile.clumping.coerceIn(0f, .5f)
        val normalization = tailNormalization(tailScale)
        var total = 0f
        for (rowOffset in rowKernel.weights.indices) {
            val rowWeight = rowKernel.weights[rowOffset]
            if (rowWeight <= 0f) continue
            for (columnOffset in columnKernel.weights.indices) {
                val weight = rowWeight * columnKernel.weights[columnOffset]
                if (weight <= 0f) continue
                val unit = signedUnit(
                    latticeHash(
                        columnKernel.firstSite + columnOffset,
                        rowKernel.firstSite + rowOffset,
                        seed,
                    ),
                    40,
                )
                total += weight * crystal(unit, tailScale, normalization)
            }
        }
        return total * FIELD_GAIN
    }

    /**
     * Density-like luminance perturbation in exact linear light, then luminance-neutral RGB
     * variation. Film density adds in a logarithmic space, so the shift is applied to the
     * log-odds of luminance rather than to gamma-encoded channels; the result is grain that
     * darkens and lightens like emulsion instead of like added noise. The chroma term stays tied
     * to the same crystal so it reads as dye rather than as per-channel sensor speckle.
     */
    private fun composite(
        pixels: IntArray,
        index: Int,
        grain: Float,
        companion: Float,
        profile: GrainProfile,
        visibility: FloatArray,
    ) {
        val color = pixels[index]
        var red = ColorMath.SRGB_TO_LINEAR[color ushr 16 and 0xff]
        var green = ColorMath.SRGB_TO_LINEAR[color ushr 8 and 0xff]
        var blue = ColorMath.SRGB_TO_LINEAR[color and 0xff]
        val tone = ColorMath.luminanceOfLinear(red, green, blue)
        if (tone <= 1e-7f || tone >= 1f - 1e-7f) return

        // The encode table doubles as the index into the tone response, which keeps a transfer
        // function evaluation out of the per-pixel path.
        val density = visibility[ColorMath.toByteFromLinear(tone)]
        if (density <= MIN_VISIBLE_DENSITY) return
        val bounded = tone.coerceIn(.00001f, .99999f)
        val shifted = ln(bounded / (1f - bounded)) + grain * profile.amount * density * DENSITY_GAIN
        val toned = (1f / (1f + exp(-shifted))).coerceIn(0f, 1f)
        val scale = toned / tone.coerceAtLeast(1e-8f)
        red *= scale
        green *= scale
        blue *= scale

        val chroma = profile.amount * density * profile.chroma.coerceIn(0f, 1f) * CHROMA_GAIN
        if (chroma > 0f) {
            // A small companion field keeps every grain from carrying the same fixed colour.
            val chromaRed = .86f * grain + .14f * companion
            val chromaBlue = .86f * grain - .14f * companion
            val chromaGreen = -(ColorMath.KR * chromaRed + ColorMath.KB * chromaBlue) / ColorMath.KG
            val envelope = 4f * toned * (1f - toned) * chroma
            red += chromaRed * envelope
            green += chromaGreen * envelope
            blue += chromaBlue * envelope
        }

        // Compress toward the new physical luminance rather than clipping channels independently.
        val maximum = maxOf(red, green, blue)
        if (maximum > 1f && maximum > toned) {
            val mix = ((1f - toned) / (maximum - toned)).coerceIn(0f, 1f)
            red = toned + (red - toned) * mix
            green = toned + (green - toned) * mix
            blue = toned + (blue - toned) * mix
        }
        val minimum = minOf(red, green, blue)
        if (minimum < 0f && minimum < toned) {
            val mix = (toned / (toned - minimum)).coerceIn(0f, 1f)
            red = toned + (red - toned) * mix
            green = toned + (green - toned) * mix
            blue = toned + (blue - toned) * mix
        }

        pixels[index] = color and -0x1000000 or
            (ColorMath.toByteFromLinear(red) shl 16) or
            (ColorMath.toByteFromLinear(green) shl 8) or
            ColorMath.toByteFromLinear(blue)
    }

    /**
     * Lattice rows, hashed once and reused.
     *
     * Each output row draws on only a handful of neighbouring site rows, and consecutive rows
     * overlap almost entirely, so a small ring keeps the cost at roughly one hash per lattice site
     * instead of one per pixel per kernel tap.
     */
    private class Lattice(
        val firstSiteColumn: Int,
        siteColumns: Int,
        rowCapacity: Int,
        val seed: Long,
        clumping: Float,
    ) {
        private val tailScale = 1.8f * clumping.coerceIn(0f, .5f)
        private val normalization = tailNormalization(tailScale)
        private val crystalRows = Array(rowCapacity) { FloatArray(siteColumns) }
        private val companionRows = Array(rowCapacity) { FloatArray(siteColumns) }
        private val loaded = IntArray(rowCapacity) { Int.MIN_VALUE }

        fun crystals(siteRow: Int): FloatArray = crystalRows[load(siteRow)]

        fun companions(siteRow: Int): FloatArray = companionRows[load(siteRow)]

        private fun load(siteRow: Int): Int {
            val slot = Math.floorMod(siteRow, crystalRows.size)
            if (loaded[slot] == siteRow) return slot
            val crystals = crystalRows[slot]
            val companions = companionRows[slot]
            for (offset in crystals.indices) {
                val hash = latticeHash(firstSiteColumn + offset, siteRow, seed)
                crystals[offset] = crystal(signedUnit(hash, 40), tailScale, normalization)
                companions[offset] = signedUnit(hash, 8)
            }
            loaded[slot] = siteRow
            return slot
        }
    }

    private class AxisKernel(val firstSite: Int, val weights: FloatArray)

    private fun buildAxisKernels(
        outputSize: Int,
        footprint: Float,
        pitch: Float,
    ): Array<AxisKernel> = Array(outputSize) { index -> axisKernel(index, footprint, pitch) }

    /**
     * Exact integral of the tent basis over one output pixel. The weights form a partition of
     * unity, so reducing resolution area-integrates one continuous field instead of redrawing it.
     */
    private fun axisKernel(index: Int, footprint: Float, pitch: Float): AxisKernel {
        val lower = index * footprint / pitch
        val upper = (index + 1) * footprint / pitch
        val first = floor(lower.toDouble()).toInt() - 1
        val last = ceil(upper.toDouble()).toInt() + 1
        val weights = FloatArray(last - first + 1)
        val inverseSpan = 1f / (upper - lower).coerceAtLeast(1e-8f)
        var sum = 0f
        for (offset in weights.indices) {
            val site = first + offset
            val weight = (tentPrimitive(upper - site) - tentPrimitive(lower - site)) * inverseSpan
            weights[offset] = weight.coerceAtLeast(0f)
            sum += weights[offset]
        }
        // Floating point endpoints leave a few ulps of error; normalising retains exact DC.
        if (sum > 0f && sum != 1f) {
            for (offset in weights.indices) weights[offset] /= sum
        }
        // The search bounds deliberately overshoot the tent's support, so the outermost taps are
        // usually zero. Dropping them here removes them from the innermost loop of the render.
        var start = 0
        while (start < weights.size - 1 && weights[start] <= 0f) start++
        var end = weights.size - 1
        while (end > start && weights[end] <= 0f) end--
        return AxisKernel(first + start, weights.copyOfRange(start, end + 1))
    }

    /** Antiderivative of `max(1-|x|, 0)`, with total area one. */
    private fun tentPrimitive(x: Float): Float = when {
        x <= -1f -> 0f
        x < 0f -> {
            val t = x + 1f
            .5f * t * t
        }
        x < 1f -> {
            val t = 1f - x
            1f - .5f * t * t
        }
        else -> 1f
    }

    /**
     * For U[-1,1], `u^3 - 3u/5` is orthogonal to `u` with variance 4/175. Correcting the variance
     * means clumping changes the shape of the distribution's tails, not the overall amount.
     */
    private fun tailNormalization(tailScale: Float): Float =
        1f / sqrt(1f + tailScale * tailScale * (12f / 175f))

    private fun crystal(unit: Float, tailScale: Float, normalization: Float): Float {
        val heavyTail = unit * unit * unit - .6f * unit
        return (unit + tailScale * heavyTail) * normalization
    }

    /** Infinite coordinate hash: no texture dimensions, wrapping, or practical repeat period. */
    private fun latticeHash(x: Int, y: Int, seed: Long): Long = mix64(
        seed + x.toLong() * -7046029254386353131L + y.toLong() * -4417276706812531889L,
    )

    private fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    private fun signedUnit(hash: Long, shift: Int): Float {
        val bits = (hash ushr shift) and 0x00FF_FFFFL
        return bits.toFloat() / 8_388_607.5f - 1f
    }

    private const val REFERENCE_LONG_EDGE = 3000f
    private const val MIN_CRYSTAL_PITCH = .5f
    private const val MAX_CRYSTAL_PITCH = 8f
    private const val FIELD_GAIN = 1.25f
    private const val DENSITY_GAIN = 2.65f
    private const val CHROMA_GAIN = .20f

    /** Below this the density shift cannot move an 8-bit level, so the pixel is left untouched. */
    private const val MIN_VISIBLE_DENSITY = .002f
}
