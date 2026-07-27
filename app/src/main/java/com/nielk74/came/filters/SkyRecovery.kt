package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.SRGB_TO_LINEAR
import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.toByte

/**
 * Recovers a washed-out sky during scene development, before any film stock is applied.
 *
 * A computational camera exposes for the subject and lets a bright sky run up against the top of
 * the range, so it arrives pale and close to neutral — the blue is not clipped away so much as
 * diluted. This stage pulls that brightness back and takes red down further than blue, which
 * returns colour to the sky rather than merely making a grey one darker.
 *
 * Which pixels it treats is decided from their colour alone. An earlier version carried a per-block
 * sky mask into the correction, and every place the mask stopped short — a branch across the frame,
 * a bright haze band, the far side of a roofline — left a step between corrected and uncorrected
 * sky. A per-pixel colour rule has no such boundary to leak: it follows a skyline exactly, keeps a
 * dark branch inside the sky at its own brightness, and varies smoothly everywhere else, so the
 * worst it can do is treat a little too much or too little rather than draw an edge.
 *
 * [SkyRegion] still supplies the frame-level judgement — whether there is open sky at all, and how
 * far down it reaches — which is what keeps a bright, cool interior wall out of the correction
 * without putting a mask edge back into the picture.
 */
internal object SkyRecovery {
    fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        region: SkyRegion,
        strength: Float = 1f,
    ) {
        require(width > 0 && height > 0 && pixels.size == width * height)
        if (strength <= 0f) return

        for (y in 0 until height) {
            val vertical = region.weightAt(y, height) * strength
            if (vertical <= 0f) continue
            val offset = y * width
            for (x in 0 until width) {
                val color = pixels[offset + x]
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val depth = vertical * washedSkyWeight(red, green, blue)
                if (depth <= 0f) continue

                val linearRed = SRGB_TO_LINEAR[red]
                val linearGreen = SRGB_TO_LINEAR[green]
                val linearBlue = SRGB_TO_LINEAR[blue]
                val gain = 1f - MAX_DARKENING * depth
                val tint = BLUE_RESTORATION * depth
                pixels[offset + x] = color and -0x1000000 or
                    (toByte(linearToSrgb(linearRed * gain * (1f - tint))) shl 16) or
                    (toByte(linearToSrgb(linearGreen * gain * (1f - tint * GREEN_SHARE))) shl 8) or
                    toByte(linearToSrgb(linearBlue * gain))
            }
        }
    }

    /**
     * How much recovery a single pixel asks for, on its colour alone.
     *
     * Three things have to hold at once. It must be bright, because a washed sky is the top of the
     * scene's range and nothing dim in the frame should be pulled down with it. It must not be warm:
     * a blown sky keeps a slight blue bias even when it has lost most of its colour, whereas sunlit
     * stone, render, paintwork, and skin sit neutral to warm, and a neutral pixel is deliberately
     * left almost untouched so a white wall is not quietly darkened. And it must still be pale —
     * chroma is the measure of what there is to recover, so a sky that already carries a deep blue
     * is left exactly as it is while the diluted one gets the full correction. Because that last
     * term is per-pixel rather than per-frame, a sky that runs from deep blue overhead to pale at
     * the horizon is corrected by exactly the amount each part of it needs.
     *
     * Once a pixel has enough chroma for its hue to mean anything, that hue is required to be the
     * sky's — blue leading green leading red — which keeps pale lilac, magenta, and cyan-green
     * surfaces out. Below that the hue is noise and only the brightness and warmth tests apply.
     */
    private fun washedSkyWeight(red: Int, green: Int, blue: Int): Float {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val brightness = smoothstep(PIXEL_DIM, PIXEL_BRIGHT, ColorMath.luma(r, g, b))
        if (brightness <= 0f) return 0f
        val cool = smoothstep(WARM_LIMIT, COOL_ENOUGH, b - r)
        if (cool <= 0f) return 0f

        val maximum = maxOf(r, g, b)
        val chroma = if (maximum <= 0f) 0f else (maximum - minOf(r, g, b)) / maximum
        val washedOut = 1f - smoothstep(RICH_SKY_CHROMA, SATURATED_SKY_CHROMA, chroma)
        if (washedOut <= 0f) return 0f

        val hue = smoothstep(-.020f, .010f, b - g) * smoothstep(-.020f, .010f, g - r)
        val hueMatters = smoothstep(NEUTRAL_CHROMA, HUED_CHROMA, chroma)
        return brightness * cool * washedOut * (1f - hueMatters * (1f - hue))
    }

    private const val PIXEL_DIM = .42f
    private const val PIXEL_BRIGHT = .68f

    /**
     * A neutral pixel sits at the very bottom of this ramp, so pale render, concrete, and paintwork
     * keep their exposure while a sky that has held on to even a trace of blue is recovered.
     */
    private const val WARM_LIMIT = -.005f
    private const val COOL_ENOUGH = .040f

    private const val RICH_SKY_CHROMA = .16f
    private const val SATURATED_SKY_CHROMA = .40f
    private const val NEUTRAL_CHROMA = .05f
    private const val HUED_CHROMA = .14f

    /** Peak fraction of linear light removed from the brightest, most washed-out sky. */
    private const val MAX_DARKENING = .26f

    /** How much further red is taken down than blue, which is what returns colour to the sky. */
    private const val BLUE_RESTORATION = .17f
    private const val GREEN_SHARE = .45f
}
