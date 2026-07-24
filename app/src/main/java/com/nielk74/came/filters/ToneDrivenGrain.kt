package com.nielk74.came.filters

import kotlin.math.exp
import kotlin.math.floor

/** Deterministic continuous grain whose visibility is controlled by tone, never local detail. */
object ToneDrivenGrain {
    /** Sequential-row sampler used by the capture renderer to avoid hashing four sites per pixel. */
    internal class FieldSampler(
        width: Int,
        longEdgePixels: Int,
        private val profile: GrainProfile,
        renderSeed: Long,
    ) {
        private val footprint = REFERENCE_LONG_EDGE / longEdgePixels.toFloat()
        private val pitch = profile.size.coerceIn(.5f, 8f)
        private val seed = mix64(profile.seed xor java.lang.Long.rotateLeft(renderSeed, 23))
        private val cellX = IntArray(width)
        private val amountX = FloatArray(width)
        private val firstCellX: Int
        private val lastCellX: Int
        private var rowY = Int.MIN_VALUE
        private var top = FloatArray(0)
        private var bottom = FloatArray(0)
        private var amountY = 0f

        init {
            for (x in 0 until width) {
                val fieldX = (x + .5f) * footprint / pitch
                cellX[x] = floor(fieldX).toInt()
                amountX[x] = smooth(fieldX - cellX[x])
            }
            firstCellX = cellX.firstOrNull() ?: 0
            lastCellX = (cellX.lastOrNull() ?: 0) + 1
            top = FloatArray(lastCellX - firstCellX + 1)
            bottom = FloatArray(top.size)
        }

        fun beginRow(y: Int) {
            val fieldY = (y + .5f) * footprint / pitch
            val nextRowY = floor(fieldY).toInt()
            amountY = smooth(fieldY - nextRowY)
            if (nextRowY == rowY) return
            if (nextRowY == rowY + 1) {
                val oldTop = top
                top = bottom
                bottom = oldTop
                fillRow(bottom, nextRowY + 1)
            } else {
                fillRow(top, nextRowY)
                fillRow(bottom, nextRowY + 1)
            }
            rowY = nextRowY
        }

        fun sample(x: Int): Float {
            val offset = cellX[x] - firstCellX
            val upper = lerp(top[offset], top[offset + 1], amountX[x])
            val lower = lerp(bottom[offset], bottom[offset + 1], amountX[x])
            return lerp(upper, lower, amountY)
        }

        private fun fillRow(output: FloatArray, y: Int) {
            for (offset in output.indices) {
                output[offset] = crystal(firstCellX + offset, y, seed, profile.clumping)
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
     * Samples one non-tiling crystal field. Coordinates are normalized to a 3000-sample 35 mm
     * long edge so output size changes crystal integration rather than inventing a new texture.
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
        val pitch = profile.size.coerceIn(.5f, 8f)
        val fieldX = (x + .5f) * footprint / pitch
        val fieldY = (y + .5f) * footprint / pitch
        val x0 = floor(fieldX).toInt()
        val y0 = floor(fieldY).toInt()
        val tx = smooth(fieldX - x0)
        val ty = smooth(fieldY - y0)
        val seed = mix64(profile.seed xor java.lang.Long.rotateLeft(renderSeed, 23))
        val top = lerp(crystal(x0, y0, seed, profile.clumping),
            crystal(x0 + 1, y0, seed, profile.clumping), tx)
        val bottom = lerp(crystal(x0, y0 + 1, seed, profile.clumping),
            crystal(x0 + 1, y0 + 1, seed, profile.clumping), tx)
        return lerp(top, bottom, ty)
    }

    private fun crystal(x: Int, y: Int, seed: Long, clumping: Float): Float {
        val u = signedUnit(hash(x, y, seed))
        val tail = u * u * u - .6f * u
        return (u + 1.8f * clumping.coerceIn(0f, .5f) * tail) * 1.25f
    }

    private fun hash(x: Int, y: Int, seed: Long): Long = mix64(
        seed + x.toLong() * -7046029254386353131L + y.toLong() * -4417276706812531889L,
    )

    private fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    private fun signedUnit(hash: Long): Float {
        val bits = (hash ushr 40) and 0x00FF_FFFFL
        return bits.toFloat() / 8_388_607.5f - 1f
    }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)
    private fun lerp(a: Float, b: Float, amount: Float): Float = a + (b - a) * amount

    private const val REFERENCE_LONG_EDGE = 3000f
}
