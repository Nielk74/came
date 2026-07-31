package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.smoothstep
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where the sky is in the frame, carried as how far down it reaches rather than as a mask.
 *
 * Detection at block resolution answers one question well — *does this frame contain open sky, and
 * how far down does it go* — and one question badly: which pixels are sky. Hanging a brightness or
 * colour change on the block mask itself put a seam wherever the region stopped: a wire, a roofline,
 * or a haze band drops a row of blocks below threshold, the flood fill stops there, and the sky
 * below it keeps the pale colour of the sky above it, with a visible step between the two.
 *
 * So the region is deliberately coarse and one-dimensional. Later stages decide *which* pixels to
 * treat from colour alone, which follows a skyline exactly and can never produce a step, and use
 * this only to keep the treatment off bright cool surfaces far below the sky. Being generous is the
 * safe direction here: reaching too far down risks a slight darkening of something sky-coloured,
 * while stopping too short is the artefact that shows.
 */
internal class SkyRegion private constructor(
    private val bottom: Float,
    private val ambient: FloatArray? = null,
    private val columns: Int = 0,
    private val rows: Int = 0,
    private val blockSize: Int = 0,
) {
    /**
     * How much of row [y] may be treated as sky, 1 down to the detected skyline and easing to 0
     * across [EXTENT_FADE] of the frame below it. The fade spans a large enough band that it reads
     * as part of the picture's own gradient rather than as an edge.
     */
    fun weightAt(y: Int, height: Int): Float {
        if (height <= 0) return 0f
        return 1f - smoothstep(bottom, bottom + EXTENT_FADE, (y + .5f) / height)
    }

    /**
     * How bright the sky is *around* the pixel at ([x], [y]), at block resolution and heavily
     * blurred.
     *
     * This is what tells cloud from sky. Colour cannot: a cloud arrives carrying the same faint
     * blue the sky around it does — it is lit by that sky — so a rule reading a single pixel's own
     * blue calls both of them sky and gives the cloud the full correction. Structure can: cloud is
     * the bright minority inside a region whose bulk is sky, so it stands above this average while
     * clear sky sits at or below it. The blur is deliberately wide, so a large cloud is measured
     * against the sky beyond it rather than against itself.
     *
     * Returns [Float.MAX_VALUE] when there is no block grid, which leaves every pixel reading as
     * sky — the behaviour callers had before this existed.
     */
    fun ambientToneAt(x: Int, y: Int): Float {
        val grid = ambient ?: return Float.MAX_VALUE
        // Sampled between block centres rather than per block. Reading the grid directly would put
        // a step into the correction at every block boundary, which is the one artefact this whole
        // region is shaped to avoid — the cost of a mask is the edge it leaves, not its coarseness.
        val fx = (x - blockSize * .5f) / blockSize
        val fy = (y - blockSize * .5f) / blockSize
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val left = x0.coerceIn(0, columns - 1)
        val right = (x0 + 1).coerceIn(0, columns - 1)
        val top = y0.coerceIn(0, rows - 1) * columns
        val bottom = (y0 + 1).coerceIn(0, rows - 1) * columns
        val upper = grid[top + left] + (grid[top + right] - grid[top + left]) * tx
        val lower = grid[bottom + left] + (grid[bottom + right] - grid[bottom + left]) * tx
        return upper + (lower - upper) * ty
    }

    companion object {
        /** For callers with no analysis of their own: every row is eligible. */
        val WholeFrame = SkyRegion(1f)

        /**
         * Analyse [pixels] for open sky, returning null when the frame has none.
         *
         * Two pieces of evidence are combined. A block grid scores brightness, flatness, and colour,
         * and a flood fill keeps only blocks with a path back to the top edge — that connectivity is
         * what separates sky from every other bright flat surface, since a white wall, a page, or a
         * lit ceiling is enclosed by the scene while sky is not. The skyline is then taken as the
         * deeper of what the fill reached and what colour alone says, so a branch or a wire spanning
         * the frame no longer truncates the region at the height where it crosses.
         */
        fun detect(pixels: IntArray, width: Int, height: Int): SkyRegion? {
            require(width > 0 && height > 0 && pixels.size == width * height)
            val blockSize = (max(width, height) / BLOCK_GRID_LONG_EDGE).coerceAtLeast(MIN_BLOCK_SIZE)
            val columns = (width + blockSize - 1) / blockSize
            val rows = (height + blockSize - 1) / blockSize
            if (columns < 2 || rows < 2) return null

            val blocks = measureBlocks(pixels, width, height, blockSize, columns, rows)
            val colour = FloatArray(columns * rows) { index -> skyColour(blocks, index) }
            val likelihood = FloatArray(colour.size) { index ->
                colour[index] * (1f - smoothstep(FLAT_ROUGHNESS, TEXTURED_ROUGHNESS, blocks.roughness[index]))
            }

            val reached = topConnectedDepth(likelihood, columns, rows) ?: return null
            val skyline = max(reached, colourSkyline(colour, columns, rows))
            val bottom = ((skyline + 1).toFloat() * blockSize / height + EXTENT_SLACK)
                .coerceIn(0f, 1f)
            val radiusX = (columns / AMBIENT_WIDE_DIVISOR).coerceAtLeast(MIN_AMBIENT_RADIUS)
            val radiusY = (rows / AMBIENT_TALL_DIVISOR).coerceAtLeast(MIN_AMBIENT_RADIUS)
            // Averaged over sky blocks only. A plain average would let a branch, a wire, or a
            // roofline pull the local level down and make the ordinary sky beside it stand above
            // its own surroundings — which is the definition of cloud used below, and would have
            // drawn a halo around every dark object in the sky.
            val lit = boxBlur(FloatArray(colour.size) { blocks.brightness[it] * colour[it] },
                columns, rows, radiusX, radiusY)
            val share = boxBlur(colour.copyOf(), columns, rows, radiusX, radiusY)
            val ambient = FloatArray(colour.size) { index ->
                if (share[index] > AMBIENT_MIN_SHARE) {
                    lit[index] / share[index]
                } else {
                    // Nothing sky-like nearby to compare against, so a pixel matches its own block
                    // and reads as sky rather than as cloud.
                    blocks.brightness[index]
                }
            }
            return SkyRegion(bottom, ambient, columns, rows, blockSize)
        }

        /**
         * Separable box blur of a block-resolution plane, clamping at the edges.
         *
         * The two radii are deliberately different. A sky's own brightness runs vertically — deep
         * overhead, pale at the horizon — so averaging a tall window would compare a hazy band just
         * above the skyline against the darker sky well above it and call the whole band cloud,
         * which is exactly the region this stage exists to recover. Averaging a wide, short window
         * instead puts each pixel against the sky at its own height, where a haze band sits at the
         * average and a cloud, which is local in both directions, stands above it.
         */
        private fun boxBlur(
            input: FloatArray,
            width: Int,
            height: Int,
            radiusX: Int,
            radiusY: Int,
        ): FloatArray {
            val horizontal = FloatArray(input.size)
            val normX = 1f / (2 * radiusX + 1)
            for (y in 0 until height) {
                val row = y * width
                var sum = 0f
                for (k in -radiusX..radiusX) sum += input[row + k.coerceIn(0, width - 1)]
                for (x in 0 until width) {
                    horizontal[row + x] = sum * normX
                    sum += input[row + (x + radiusX + 1).coerceIn(0, width - 1)] -
                        input[row + (x - radiusX).coerceIn(0, width - 1)]
                }
            }
            val output = FloatArray(input.size)
            val normY = 1f / (2 * radiusY + 1)
            for (x in 0 until width) {
                var sum = 0f
                for (k in -radiusY..radiusY) sum += horizontal[k.coerceIn(0, height - 1) * width + x]
                for (y in 0 until height) {
                    output[y * width + x] = sum * normY
                    sum += horizontal[(y + radiusY + 1).coerceIn(0, height - 1) * width + x] -
                        horizontal[(y - radiusY).coerceIn(0, height - 1) * width + x]
                }
            }
            return output
        }

        /** Per-block brightness, coolness, chroma, and roughness, from one full-resolution pass. */
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
         * How sky-like a block's colour is, ignoring how smooth it is.
         *
         * Sky arrives in two forms and the detector has to accept both: the bright near-neutral one a
         * computational exposure produces, and the deep blue of an unclipped one. A single brightness
         * rule would miss the second and lose the frame's whole sky, so the two are scored separately
         * and the better of them wins. Both insist the block is not warm, which is what keeps sunlit
         * stone, render, and skin out of it.
         */
        private fun skyColour(blocks: Blocks, index: Int): Float {
            val cool = smoothstep(WARM_LIMIT, COOL_ENOUGH, blocks.coolness[index])
            if (cool <= 0f) return 0f
            val brightness = blocks.brightness[index]
            val washedOut = smoothstep(MIN_SKY_BRIGHTNESS, FULL_SKY_BRIGHTNESS, brightness)
            val blue = smoothstep(BLUE_SKY_CHROMA, DEEP_SKY_CHROMA, blocks.chroma[index]) *
                smoothstep(DUSK_BRIGHTNESS, DAY_BRIGHTNESS, brightness)
            return cool * max(washedOut, blue)
        }

        /**
         * Flood fill inward from the top edge, returning the deepest block row reached, or null when
         * the frame holds no sky worth the rest of the work — the common case for interiors and
         * close-ups. Seeding needs real confidence while growing does not, so a sky that pales,
         * deepens, or thins on the way down stays one region instead of breaking into two.
         */
        private fun topConnectedDepth(likelihood: FloatArray, columns: Int, rows: Int): Int? {
            val visited = BooleanArray(likelihood.size)
            val queue = IntArray(likelihood.size)
            var head = 0
            var tail = 0
            for (column in 0 until columns) {
                if (likelihood[column] > SEED_CONFIDENCE) {
                    visited[column] = true
                    queue[tail++] = column
                }
            }
            if (tail == 0) return null

            var weight = 0f
            var deepest = 0
            while (head < tail) {
                val index = queue[head++]
                val row = index / columns
                val column = index % columns
                weight += likelihood[index]
                if (row > deepest) deepest = row
                fun visit(nextColumn: Int, nextRow: Int) {
                    if (nextColumn !in 0 until columns || nextRow !in 0 until rows) return
                    val next = nextRow * columns + nextColumn
                    if (visited[next] || likelihood[next] <= GROW_CONFIDENCE) return
                    visited[next] = true
                    queue[tail++] = next
                }
                visit(column - 1, row)
                visit(column + 1, row)
                visit(column, row - 1)
                visit(column, row + 1)
            }
            return if (weight < MIN_SKY_WEIGHT) null else deepest
        }

        /**
         * The deepest block row still substantially sky-coloured, scanning down from the top.
         *
         * Connectivity has already established that this frame has sky; this only asks how far it
         * goes, so it can afford to ignore texture and adjacency and simply follow the colour. A few
         * consecutive rows are allowed to fail before the scan gives up, which is what carries the
         * region past a wire, a branch, or a roofline without taking it onto the ground below.
         */
        private fun colourSkyline(colour: FloatArray, columns: Int, rows: Int): Int {
            var deepest = 0
            var gap = 0
            for (row in 0 until rows) {
                var skyBlocks = 0
                val offset = row * columns
                for (column in 0 until columns) {
                    if (colour[offset + column] >= SKY_COLOUR) skyBlocks++
                }
                if (skyBlocks.toFloat() / columns >= MIN_ROW_SHARE) {
                    deepest = row
                    gap = 0
                } else {
                    gap++
                    if (gap > MAX_GAP_ROWS) break
                }
            }
            return deepest
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
        private const val BLUE_SKY_CHROMA = .18f
        private const val DEEP_SKY_CHROMA = .34f
        private const val DUSK_BRIGHTNESS = .12f
        private const val DAY_BRIGHTNESS = .30f
        private const val FLAT_ROUGHNESS = .012f
        private const val TEXTURED_ROUGHNESS = .075f

        /**
         * Detection is deliberately more forgiving about warmth than the correction is. Finding the
         * sky is a question about the whole frame, backed by brightness, flatness, and a path to the
         * top edge; a blown sky that has kept almost no blue at all still has to be found here, and
         * whether any given pixel of it is sky or cloud is settled later, per pixel.
         */
        private const val WARM_LIMIT = -.020f
        private const val COOL_ENOUGH = .015f

        private const val SEED_CONFIDENCE = .10f
        private const val GROW_CONFIDENCE = .04f
        /**
         * A block's worth of confidence, which rejects a single stray bright patch at the top edge
         * while still accepting a sliver of sky between two buildings.
         */
        private const val MIN_SKY_WEIGHT = 1f

        private const val SKY_COLOUR = .35f
        private const val MIN_ROW_SHARE = .20f
        private const val MAX_GAP_ROWS = 3

        /** Frame fractions: how far past the skyline the region holds, and how long it then fades. */
        private const val EXTENT_SLACK = .08f
        private const val EXTENT_FADE = .22f

        /**
         * The ambient window: about two thirds of the frame across and an eighth of it down.
         * Narrower across and a large cloud starts being measured against its own brightness and
         * stops reading as cloud; taller down and the sky's own vertical gradient reads as one.
         */
        private const val AMBIENT_WIDE_DIVISOR = 3
        private const val AMBIENT_TALL_DIVISOR = 16
        private const val MIN_AMBIENT_RADIUS = 1

        /** Below this much sky in the window there is nothing meaningful to average. */
        private const val AMBIENT_MIN_SHARE = .05f
    }
}
