package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.SRGB_TO_LINEAR
import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.luminanceOfLinear
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.toByte
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recovers a washed-out sky during scene development, before any film stock is applied.
 *
 * A computational camera exposes for the subject and lets a bright sky run up against the top of
 * the range, so it arrives pale and close to neutral — the blue is not clipped away so much as
 * diluted. This stage finds the sky, pulls its brightness back, and takes red down further than
 * blue, which returns colour to it rather than merely making a grey sky darker.
 *
 * Detection deliberately combines two different kinds of evidence:
 *
 *  - A coarse block grid decides *where the sky is*. Blocks are judged on brightness, flatness,
 *    and coolness, and then only those in a region touching the top edge of the frame are kept.
 *    Working on blocks makes a real flood fill affordable and keeps single bright objects from
 *    seeding a region on their own.
 *  - Each pixel is then judged on its own. A block is a coarse thing to hang a brightness change
 *    on, and applying the block mask alone would darken a band of whatever borders the skyline.
 *    Multiplying by a per-pixel gate means a dark branch inside a sky block keeps its brightness
 *    and the correction stops exactly at the edge of the sky, with no halo to blur away.
 *
 * The strength is adaptive: a sky that is already a deep saturated blue has nothing to recover and
 * is left essentially alone, while a bright near-neutral one receives the full correction.
 */
internal object SkyRecovery {
    fun apply(pixels: IntArray, width: Int, height: Int, strength: Float = 1f) {
        require(width > 0 && height > 0 && pixels.size == width * height)
        if (strength <= 0f) return

        val blockSize = (max(width, height) / BLOCK_GRID_LONG_EDGE).coerceAtLeast(MIN_BLOCK_SIZE)
        val columns = (width + blockSize - 1) / blockSize
        val rows = (height + blockSize - 1) / blockSize
        if (columns < 2 || rows < 2) return

        val blocks = measureBlocks(pixels, width, height, blockSize, columns, rows)
        val mask = topConnectedSkyMask(blocks, columns, rows) ?: return
        feather(mask, columns, rows)

        val amount = recoveryAmount(blocks, mask) * strength
        if (amount <= 0f) return
        composite(pixels, width, height, blockSize, columns, rows, mask, amount)
    }

    /** Per-block brightness, coolness, chroma, and roughness, all from one full-resolution pass. */
    private fun measureBlocks(
        pixels: IntArray,
        width: Int,
        height: Int,
        blockSize: Int,
        columns: Int,
        rows: Int,
    ): Blocks {
        val blocks = Blocks(columns * rows)
        for (row in 0 until rows) {
            val yStart = row * blockSize
            val yEnd = min(yStart + blockSize, height)
            for (column in 0 until columns) {
                val xStart = column * blockSize
                val xEnd = min(xStart + blockSize, width)
                var sumY = 0f
                var sumYSquared = 0f
                var sumCool = 0f
                var sumChroma = 0f
                var count = 0
                for (y in yStart until yEnd) {
                    val offset = y * width
                    for (x in xStart until xEnd) {
                        val color = pixels[offset + x]
                        val red = (color ushr 16 and 0xff) / 255f
                        val green = (color ushr 8 and 0xff) / 255f
                        val blue = (color and 0xff) / 255f
                        val tone = ColorMath.luma(red, green, blue)
                        sumY += tone
                        sumYSquared += tone * tone
                        sumCool += blue - red
                        val maximum = maxOf(red, green, blue)
                        sumChroma += if (maximum <= 0f) 0f else (maximum - minOf(red, green, blue)) / maximum
                        count++
                    }
                }
                val inverse = 1f / count.coerceAtLeast(1)
                val meanY = sumY * inverse
                val index = row * columns + column
                blocks.brightness[index] = meanY
                blocks.coolness[index] = sumCool * inverse
                blocks.chroma[index] = sumChroma * inverse
                blocks.roughness[index] =
                    sqrt((sumYSquared * inverse - meanY * meanY).coerceAtLeast(0f))
            }
        }
        return blocks
    }

    /**
     * Flood fill the sky region inward from the top edge, returning per-block confidence.
     *
     * Requiring a path back to the top of the frame is what separates sky from every other bright
     * flat surface: a white wall, a page, or a snowfield is enclosed by the scene, while sky is
     * not. Returns null when no seed on the top row is plausible, which is the common case for
     * interiors and close-ups and skips the rest of the work.
     */
    private fun topConnectedSkyMask(blocks: Blocks, columns: Int, rows: Int): FloatArray? {
        val likelihood = FloatArray(columns * rows) { index ->
            val brightness = smoothstep(MIN_SKY_BRIGHTNESS, FULL_SKY_BRIGHTNESS, blocks.brightness[index])
            val flatness = 1f - smoothstep(FLAT_ROUGHNESS, TEXTURED_ROUGHNESS, blocks.roughness[index])
            val coolness = smoothstep(WARM_LIMIT, COOL_ENOUGH, blocks.coolness[index])
            brightness * flatness * coolness
        }

        val mask = FloatArray(columns * rows)
        val queue = IntArray(columns * rows)
        var head = 0
        var tail = 0
        for (column in 0 until columns) {
            if (likelihood[column] > CONNECT_THRESHOLD) {
                mask[column] = likelihood[column]
                queue[tail++] = column
            }
        }
        if (tail == 0) return null

        while (head < tail) {
            val index = queue[head++]
            val row = index / columns
            val column = index % columns
            fun visit(nextColumn: Int, nextRow: Int) {
                if (nextColumn !in 0 until columns || nextRow !in 0 until rows) return
                val next = nextRow * columns + nextColumn
                if (mask[next] > 0f || likelihood[next] <= CONNECT_THRESHOLD) return
                mask[next] = likelihood[next]
                queue[tail++] = next
            }
            visit(column - 1, row)
            visit(column + 1, row)
            visit(column, row - 1)
            visit(column, row + 1)
        }
        return mask
    }

    /** A three-tap separable blur so the block mask has no visible grid structure. */
    private fun feather(mask: FloatArray, columns: Int, rows: Int) {
        val scratch = FloatArray(mask.size)
        for (row in 0 until rows) {
            val offset = row * columns
            for (column in 0 until columns) {
                val left = mask[offset + max(column - 1, 0)]
                val right = mask[offset + min(column + 1, columns - 1)]
                scratch[offset + column] = (left + 2f * mask[offset + column] + right) * .25f
            }
        }
        for (column in 0 until columns) {
            for (row in 0 until rows) {
                val up = scratch[max(row - 1, 0) * columns + column]
                val down = scratch[min(row + 1, rows - 1) * columns + column]
                mask[row * columns + column] =
                    (up + 2f * scratch[row * columns + column] + down) * .25f
            }
        }
    }

    /**
     * How much recovery the detected sky actually needs.
     *
     * A deep blue sky is already carrying its colour and only wants leaving alone; the correction
     * is aimed at the bright, near-neutral sky that a computational exposure produces.
     */
    private fun recoveryAmount(blocks: Blocks, mask: FloatArray): Float {
        var weight = 0f
        var brightness = 0f
        var chroma = 0f
        for (index in mask.indices) {
            val w = mask[index]
            if (w <= 0f) continue
            weight += w
            brightness += blocks.brightness[index] * w
            chroma += blocks.chroma[index] * w
        }
        if (weight < MIN_SKY_WEIGHT) return 0f
        val meanBrightness = brightness / weight
        val meanChroma = chroma / weight
        val washedOut = (1f - smoothstep(RICH_SKY_CHROMA, SATURATED_SKY_CHROMA, meanChroma)) *
            smoothstep(MIN_SKY_BRIGHTNESS, FULL_SKY_BRIGHTNESS, meanBrightness)
        return MAX_DARKENING * washedOut
    }

    private fun composite(
        pixels: IntArray,
        width: Int,
        height: Int,
        blockSize: Int,
        columns: Int,
        rows: Int,
        mask: FloatArray,
        amount: Float,
    ) {
        val half = blockSize * .5f
        for (y in 0 until height) {
            // Bilinear block-mask coordinates, so the region weight varies smoothly down the frame.
            val blockY = (y - half) / blockSize
            val row0 = blockY.toInt().coerceIn(0, rows - 1)
            val row1 = (row0 + 1).coerceAtMost(rows - 1)
            val rowMix = (blockY - row0).coerceIn(0f, 1f)
            val offset = y * width
            for (x in 0 until width) {
                val blockX = (x - half) / blockSize
                val column0 = blockX.toInt().coerceIn(0, columns - 1)
                val column1 = (column0 + 1).coerceAtMost(columns - 1)
                val columnMix = (blockX - column0).coerceIn(0f, 1f)
                val top = mask[row0 * columns + column0] +
                    (mask[row0 * columns + column1] - mask[row0 * columns + column0]) * columnMix
                val bottom = mask[row1 * columns + column0] +
                    (mask[row1 * columns + column1] - mask[row1 * columns + column0]) * columnMix
                val region = top + (bottom - top) * rowMix
                if (region <= 0f) continue

                val color = pixels[offset + x]
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val weight = region * pixelSkyGate(red, green, blue)
                if (weight <= 0f) continue

                val linearRed = SRGB_TO_LINEAR[red]
                val linearGreen = SRGB_TO_LINEAR[green]
                val linearBlue = SRGB_TO_LINEAR[blue]
                val tone = luminanceOfLinear(linearRed, linearGreen, linearBlue)
                // Weight the correction toward the brightest sky so the horizon, which is pale in
                // most scenes, is not pulled down as hard as the top of the frame.
                val depth = weight * smoothstep(RECOVERY_FLOOR, RECOVERY_CEILING, tone)
                if (depth <= 0f) continue

                val gain = 1f - MAX_DARKENING.coerceAtMost(amount) * depth
                val tint = BLUE_RESTORATION * depth
                pixels[offset + x] = color and -0x1000000 or
                    (toByte(linearToSrgb(linearRed * gain * (1f - tint))) shl 16) or
                    (toByte(linearToSrgb(linearGreen * gain * (1f - tint * GREEN_SHARE))) shl 8) or
                    toByte(linearToSrgb(linearBlue * gain))
            }
        }
    }

    /**
     * How sky-like a single pixel is on its own terms. Bright and not warm: a blown sky keeps a
     * slight blue bias even when it has lost most of its colour, whereas sunlit stone, render, and
     * paintwork sit neutral to warm, which is what keeps a pale building out of the correction.
     */
    private fun pixelSkyGate(red: Int, green: Int, blue: Int): Float {
        val tone = ColorMath.luma(red / 255f, green / 255f, blue / 255f)
        val cool = (blue - red) / 255f
        return smoothstep(PIXEL_DIM, PIXEL_BRIGHT, tone) * smoothstep(WARM_LIMIT, COOL_ENOUGH, cool)
    }

    private class Blocks(size: Int) {
        val brightness = FloatArray(size)
        val coolness = FloatArray(size)
        val chroma = FloatArray(size)
        val roughness = FloatArray(size)
    }

    private const val BLOCK_GRID_LONG_EDGE = 96
    private const val MIN_BLOCK_SIZE = 4

    private const val MIN_SKY_BRIGHTNESS = .46f
    private const val FULL_SKY_BRIGHTNESS = .72f
    private const val FLAT_ROUGHNESS = .012f
    private const val TEXTURED_ROUGHNESS = .075f
    private const val WARM_LIMIT = -.020f
    private const val COOL_ENOUGH = .030f
    private const val CONNECT_THRESHOLD = .10f
    private const val MIN_SKY_WEIGHT = 3f

    private const val RICH_SKY_CHROMA = .16f
    private const val SATURATED_SKY_CHROMA = .40f

    private const val PIXEL_DIM = .42f
    private const val PIXEL_BRIGHT = .68f
    private const val RECOVERY_FLOOR = .30f
    private const val RECOVERY_CEILING = .85f

    /** Peak fraction of linear light removed from the brightest, most washed-out sky. */
    private const val MAX_DARKENING = .26f

    /** How much further red is taken down than blue, which is what returns colour to the sky. */
    private const val BLUE_RESTORATION = .17f
    private const val GREEN_SHARE = .45f
}
