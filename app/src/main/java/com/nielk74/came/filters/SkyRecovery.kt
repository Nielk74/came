package com.nielk74.came.filters

import com.nielk74.came.filters.ColorMath.linearToSrgb
import com.nielk74.came.filters.ColorMath.luminanceOfLinear
import com.nielk74.came.filters.ColorMath.smoothstep
import com.nielk74.came.filters.ColorMath.srgbToLinear

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

        val pixel = Rgb()
        for (y in 0 until height) {
            val vertical = region.weightAt(y, height) * strength
            if (vertical <= 0f) continue
            val offset = y * width
            for (x in 0 until width) {
                pixel.setFrom(pixels[offset + x])
                shade(pixel, vertical, region.ambientToneAt(x, y))
                pixels[offset + x] = pixel.pack(pixels[offset + x] and -0x1000000)
            }
        }
    }

    /**
     * [apply] for one pixel already in flight, given its row's [vertical] weight and the brightness
     * of the sky around it ([ambientTone], from [SkyRegion.ambientToneAt]).
     */
    fun shade(pixel: Rgb, vertical: Float, ambientTone: Float) {
        val encoded = ColorMath.luma(pixel.red, pixel.green, pixel.blue)
        val depth = vertical * washedSkyWeight(pixel.red, pixel.green, pixel.blue, encoded, ambientTone)
        if (depth <= 0f) return

        val linearRed = srgbToLinear(pixel.red)
        val linearGreen = srgbToLinear(pixel.green)
        val linearBlue = srgbToLinear(pixel.blue)
        val tone = luminanceOfLinear(linearRed, linearGreen, linearBlue)

        // Deliberately no paper-white rolloff here, though every other stage that adds colour has
        // one. A sky this stage exists to rescue is itself near the top of the range — a washed-out
        // one measures around 92% — so rolling the correction off by brightness would take it away
        // from exactly the pixels it is for. Cloud is held out by what it is, not by how bright it
        // is: no blue of its own, and standing above the sky around it. Both tests are in
        // [washedSkyWeight], and by here the pixel has already passed or failed them.
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
        pixel.set(
            linearToSrgb(exposure + deltaRed * scale),
            linearToSrgb(exposure + deltaGreen * scale),
            linearToSrgb(exposure + deltaBlue * scale),
        )
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
     * And it must be sky rather than cloud, which takes two tests because colour alone cannot do
     * it. Cloud is lit by the whole sky and comes back carrying much the same faint blue the sky
     * does — only less of it — so the blue-over-red ramp is placed where the two actually separate
     * rather than just above neutral, where it scored both at full. What that still cannot settle,
     * structure does: cloud stands above the brightness of the sky around it, and clear sky does
     * not. A white wall or a genuinely blown patch fails both and keeps its white.
     *
     * Once a pixel has enough chroma for its hue to mean anything, that hue is required to be the
     * sky's — blue leading green leading red — which keeps pale lilac, magenta, and cyan-green
     * surfaces out. Below that the hue is noise and only the brightness and warmth tests apply.
     */
    private fun washedSkyWeight(
        r: Float,
        g: Float,
        b: Float,
        encoded: Float,
        ambientTone: Float,
    ): Float {
        val brightness = smoothstep(PIXEL_DIM, PIXEL_BRIGHT, encoded)
        if (brightness <= 0f) return 0f
        val cool = smoothstep(CLOUD_NEUTRAL, SKY_BLUE_BIAS, b - r)
        if (cool <= 0f) return 0f

        // Cloud stands above the sky around it; clear sky sits at or below that average. This is
        // the test that actually separates the two, because their colour does not — a cloud is lit
        // by the sky and comes back carrying the same faint blue.
        val cloud = smoothstep(CLOUD_ABOVE_SKY_RISE, CLOUD_ABOVE_SKY_FULL, encoded - ambientTone)
        if (cloud >= 1f) return 0f

        val maximum = maxOf(r, g, b)
        val chroma = if (maximum <= 0f) 0f else (maximum - minOf(r, g, b)) / maximum
        val washedOut = 1f - smoothstep(RICH_SKY_CHROMA, SATURATED_SKY_CHROMA, chroma)
        if (washedOut <= 0f) return 0f

        val hue = smoothstep(-.020f, .010f, b - g) * smoothstep(-.020f, .010f, g - r)
        val hueMatters = smoothstep(NEUTRAL_CHROMA, HUED_CHROMA, chroma)
        return brightness * cool * washedOut * (1f - cloud) * (1f - hueMatters * (1f - hue))
    }

    private const val PIXEL_DIM = .42f
    private const val PIXEL_BRIGHT = .68f

    /**
     * How much blue over red marks a pixel as sky rather than as something white.
     *
     * The ramp is short and sits just above neutral because that is the regime this stage exists
     * for: a sky a computational exposure has run up against the top of its range arrives with
     * about ten levels of blue in it, and a cloud beside it with none. Widening it to where the two
     * separate on a frame whose sky is already deep — around thirty levels against eighty-five —
     * measures better on that frame and stops the washed-out sky from being recovered at all, which
     * is the whole point of the stage. So this test keeps the case it was built for, and the
     * ambient-brightness test above takes the cloud that has picked up the sky's own blue.
     */
    private const val CLOUD_NEUTRAL = .002f
    private const val SKY_BLUE_BIAS = .018f

    /**
     * How far above the surrounding sky a pixel has to sit before it is read as cloud rather than
     * as a bright patch of sky, as a fraction of the encoded range. Measured on real frames, clear
     * sky sits within a couple of percent of its own neighbourhood average while cloud runs eight
     * to thirty above it, so the ramp starts just outside the sky's own variation.
     */
    private const val CLOUD_ABOVE_SKY_RISE = .03f
    private const val CLOUD_ABOVE_SKY_FULL = .14f

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
