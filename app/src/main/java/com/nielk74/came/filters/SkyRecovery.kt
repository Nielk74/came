package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.SRGB_TO_LINEAR
import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.luminanceOfLinear
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.toByte

/**
 * Puts the colour back into a washed-out sky, working on the print.
 *
 * A computational camera exposes for the subject and lets a bright sky run up against the top of
 * the range, so it arrives pale and close to neutral — the blue is not clipped away so much as
 * diluted. This stage brings the brightness down and expands what blue is left into a sky that
 * reads as one, rather than as a grey ceiling over the photograph.
 *
 * It runs after the stock rather than before it, which is the opposite of what it sounds like it
 * should do. Recovering the sky first and letting the film read the result measures badly: the
 * negative and print curves are built to compress exactly this — a large, bright, low-chroma region
 * — so they take most of the recovered blue back out again. Placed on the print, the same work
 * survives to the photograph.
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
                val tone = luminanceOfLinear(linearRed, linearGreen, linearBlue)
                val exposure = tone * (1f - MAX_DARKENING * depth)

                // Chroma is expanded around the sky's own luminance and then pushed along a
                // cyan-leaning blue, which is a colour move rather than a channel gain: what little
                // blue survived the print is amplified instead of being inferred from red alone.
                val boost = 1f + CHROMA_GAIN * depth
                val tint = SKY_TINT * depth * tone
                val deltaRed = (linearRed - tone) * boost + SKY_R * tint
                val deltaGreen = (linearGreen - tone) * boost + SKY_G * tint
                val deltaBlue = (linearBlue - tone) * boost + SKY_B * tint
                // One shared limit instead of three independent clips, so a sky at the edge of the
                // gamut keeps its hue rather than turning as each channel clips in turn.
                val scale = minOf(
                    1f,
                    headroom(exposure, deltaRed),
                    headroom(exposure, deltaGreen),
                    headroom(exposure, deltaBlue),
                )
                pixels[offset + x] = color and -0x1000000 or
                    (toByte(linearToSrgb(exposure + deltaRed * scale)) shl 16) or
                    (toByte(linearToSrgb(exposure + deltaGreen * scale)) shl 8) or
                    toByte(linearToSrgb(exposure + deltaBlue * scale))
            }
        }
    }

    /**
     * How much recovery a single pixel asks for, on its colour alone.
     *
     * Three things have to hold at once. It must be bright, because a washed sky is the top of the
     * scene's range and nothing dim in the frame should be pulled down with it. It must still be
     * pale — chroma measures what there is to recover, so a sky already carrying a deep blue is left
     * exactly as it is while the diluted one gets the full correction. Because that term is per
     * pixel rather than per frame, a sky running from deep blue overhead to washed out at the
     * horizon is corrected by what each band of it actually needs.
     *
     * And it must have kept a trace of blue. That last test is what separates the two white things
     * in a photograph of the sky. Cloud is lit by the whole sky and comes back neutral to warm; sky
     * that a computational exposure has run up against the top of the range comes back pale but
     * still faintly blue, because the blue was diluted rather than clipped away. So the warmth ramp
     * starts almost exactly at neutral: a cloud, a white wall, or a genuinely clipped patch keeps
     * its brightness and stays white, while a white-blue sky a few levels off neutral is taken as
     * sky and gets the full correction.
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
        val cool = smoothstep(CLOUD_NEUTRAL, SKY_BLUE_BIAS, b - r)
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
     * Cloud and sky part company within a few levels of neutral, so this ramp is short and sits just
     * above zero: no blue at all is cloud or paper and keeps its white, a couple of levels of blue
     * is sky and is corrected in full.
     */
    private const val CLOUD_NEUTRAL = .002f
    private const val SKY_BLUE_BIAS = .018f

    private const val RICH_SKY_CHROMA = .16f
    private const val SATURATED_SKY_CHROMA = .40f
    private const val NEUTRAL_CHROMA = .05f
    private const val HUED_CHROMA = .14f

    /** How far a delta can go before a channel leaves the gamut. */
    private fun headroom(exposure: Float, delta: Float): Float = when {
        delta > 0f -> (1f - exposure) / delta
        delta < 0f -> exposure / -delta
        else -> Float.POSITIVE_INFINITY
    }

    /**
     * Peak fraction of linear light removed from the brightest, most washed-out sky. A pale sky only
     * reads as blue once it stops competing with the paper white around it, so the brightness has to
     * come down with the colour going in — including for the white-blue sky at the top of the range.
     * The print has already brought the whole frame down by the time this runs, so it takes less
     * here than it would have before it.
     */
    private const val MAX_DARKENING = .12f

    /** How far the sky's own chroma is expanded around its luminance. */
    private const val CHROMA_GAIN = .46f

    /**
     * The colour the sky is pushed along, with its own luminance removed so the push moves hue and
     * chroma but not exposure. Green at somewhat under half of blue puts it on the cyan side of pure
     * blue — the sky of a colour negative rather than of a paint chart.
     */
    private const val SKY_GREEN = .60f
    private const val SKY_LUMA = ColorMath.KG * SKY_GREEN + ColorMath.KB
    private const val SKY_R = -SKY_LUMA
    private const val SKY_G = SKY_GREEN - SKY_LUMA
    private const val SKY_B = 1f - SKY_LUMA
    private const val SKY_TINT = .115f
}
