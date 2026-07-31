package com.nielk74.came.filters

import kotlin.math.tanh

/**
 * Camera-sized adaptation of ricoh-gr3-android's FilmLookCatalog.
 *
 * IDs, labels, negative/print controls, split tones, halation, and grain intent are retained.
 * The values are manufacturer-anchored visual fits, not laboratory measurements or official
 * profiles from the named manufacturers.
 */
object FilmCatalog {
    val profiles: List<FilmProfile> = listOf(
        profile(
            id = "portra400", name = "Portra 400", top = 0xFFE8C7A9, bottom = 0xFF8C9A91,
            red = channel(.23f, .035f, .82f, 1.035f),
            green = channel(.24f, .025f, .78f, 1f),
            blue = channel(.27f, .012f, .70f, .97f),
            crossTalk = crossTalk(.025f, .008f), print = print(.92f, .10f, .44f, .02f),
            saturation = .91f,
            splitTone = split(0f, .006f, .016f, .030f, .017f, 0f, .78f),
            foliageTone = FoliageTone(.56f, .20f), skyTone = SkyTone(.22f, .20f),
            grain = grain(.060f, 2.15f, .16f, 400L, .35f),
        ),
        profile(
            id = "portra800", name = "Portra 800", top = 0xFFE2B98F, bottom = 0xFF776F68,
            red = channel(.28f, .025f, .78f, 1.045f),
            green = channel(.29f, .018f, .74f, 1f),
            blue = channel(.32f, 0f, .66f, .955f),
            crossTalk = crossTalk(.028f, .010f), print = print(.94f, .11f, .40f, .015f),
            saturation = .94f,
            splitTone = split(.003f, .006f, .017f, .035f, .018f, 0f, .82f),
            foliageTone = FoliageTone(.62f, .24f), skyTone = SkyTone(.25f, .24f),
            grain = grain(.092f, 2.55f, .25f, 800L, .42f),
        ),
        profile(
            id = "gold200", name = "Gold 200", top = 0xFFF0C36F, bottom = 0xFF9B7248,
            red = channel(.31f, .038f, .70f, 1.05f),
            green = channel(.31f, .026f, .68f, 1.005f),
            blue = channel(.34f, .010f, .62f, .93f),
            crossTalk = crossTalk(.032f, .014f), print = print(.98f, .08f, .34f, .01f),
            saturation = 1.01f,
            splitTone = split(.018f, .010f, 0f, .040f, .027f, 0f, .82f),
            grain = grain(.031f, 1.75f, .15f, 200L),
        ),
        profile(
            id = "ektar100", name = "Ektar 100", top = 0xFFE75B51, bottom = 0xFF246B70,
            red = channel(.39f, -.012f, .78f, 1.015f),
            green = channel(.38f, -.010f, .80f, 1f),
            blue = channel(.40f, -.014f, .82f, 1f),
            crossTalk = crossTalk(.018f),
            print = print(1.06f, -.02f, .32f, -.01f, .0002f, .996f),
            saturation = 1.14f,
            grain = grain(.018f, 1.25f, .06f, 100L),
        ),
        profile(
            id = "superia400", name = "Superia 400", top = 0xFF70B89B, bottom = 0xFF47778F,
            red = channel(.32f, .005f, .68f, .985f),
            green = channel(.31f, .015f, .72f, 1.025f),
            blue = channel(.33f, .010f, .68f, 1.005f),
            crossTalk = Matrix3(.955f, .050f, -.005f, .018f, .964f, .018f, .002f, .045f, .953f),
            print = print(1.01f, .04f, .32f), saturation = 1.07f,
            splitTone = split(0f, .012f, .020f, .004f, .018f, .008f, .70f),
            grain = grain(.034f, 1.75f, .16f, 404L),
        ),
        profile(
            id = "cinestill800t", name = "CineStill 800T", top = 0xFFCF594B,
            bottom = 0xFF355F83,
            red = channel(.28f, .014f, .78f, .94f),
            green = channel(.27f, .024f, .82f, 1f),
            blue = channel(.29f, .035f, .86f, 1.07f),
            crossTalk = crossTalk(.025f), print = print(.96f, .09f, .42f, .01f),
            saturation = .98f,
            splitTone = split(0f, .012f, .040f, .026f, .010f, 0f, .88f),
            halation = HalationProfile(.78f, 11, 1.05f, 1f, .006f, .012f),
            grain = grain(.046f, 2.05f, .25f, 801L),
        ),
        profile(
            id = "vision3_250d", name = "Vision3 250D", top = 0xFFD8B486,
            bottom = 0xFF567A7A,
            red = channel(.19f, .035f, .90f, 1.018f),
            green = channel(.19f, .032f, .90f, 1f),
            blue = channel(.21f, .025f, .86f, .978f),
            crossTalk = crossTalk(.035f, .006f), print = print(.88f, .14f, .56f, .03f),
            saturation = .94f,
            splitTone = split(0f, .010f, .025f, .026f, .014f, 0f, .74f),
            halation = HalationProfile(.77f, 6, .22f, 1f, .28f, .09f),
            grain = grain(.024f, 1.55f, .10f, 250L),
        ),
        profile(
            id = "vision3_500t", name = "Vision3 500T", top = 0xFF4D7897,
            bottom = 0xFFC88759,
            red = channel(.20f, .038f, .90f, .96f),
            green = channel(.19f, .035f, .92f, 1f),
            blue = channel(.21f, .042f, .88f, 1.05f),
            crossTalk = crossTalk(.036f, .006f), print = print(.90f, .15f, .55f, .02f),
            saturation = .92f,
            splitTone = split(0f, .014f, .036f, .036f, .017f, 0f, .82f),
            halation = HalationProfile(.75f, 7, .30f, 1f, .14f, .035f),
            grain = grain(.034f, 1.85f, .18f, 500L),
        ),
        profile(
            id = "eterna", name = "Eterna Cinema", top = 0xFFB9B9A9, bottom = 0xFF505D5C,
            red = channel(.15f, .050f, .96f, 1.005f),
            green = channel(.15f, .048f, .98f, 1f),
            blue = channel(.17f, .045f, .94f, .99f),
            crossTalk = crossTalk(.035f),
            print = print(.82f, .19f, .65f, .05f, .001f, .986f),
            saturation = .82f,
            splitTone = split(0f, .008f, .018f, .018f, .010f, .003f, .62f),
            halation = HalationProfile(.79f, 6, .15f, 1f, .24f, .08f),
            grain = grain(.025f, 1.65f, .10f, 18L),
        ),
        profile(
            id = "trix400", name = "Tri-X 400", top = 0xFFE7E5DF, bottom = 0xFF171717,
            red = channel(.46f, -.018f, .72f), green = channel(.46f, -.018f, .72f),
            blue = channel(.46f, -.018f, .72f), crossTalk = Matrix3.Identity,
            print = print(1.11f, -.04f, .28f, -.02f, .0002f, .996f),
            saturation = 0f,
            grain = GrainProfile(.056f, 2.1f, .58f, .30f, 0f, 320L),
            monochromeWeights = RgbWeights(.27f, .63f, .10f),
        ),
        profile(
            id = "hp5", name = "HP5 Plus", top = 0xFFE2DFD7, bottom = 0xFF363636,
            red = channel(.30f, .025f, .86f), green = channel(.30f, .025f, .86f),
            blue = channel(.30f, .025f, .86f), crossTalk = Matrix3.Identity,
            print = print(.98f, .08f, .44f, .02f, .001f, .988f),
            saturation = 0f,
            grain = GrainProfile(.050f, 2f, .54f, .26f, 0f, 405L),
            monochromeWeights = RgbWeights(.24f, .64f, .12f),
        ),
    )

    val default: FilmProfile = profiles.first()

    private val byId = profiles.associateBy(FilmProfile::id)

    fun find(id: String?): FilmProfile? = id?.let(byId::get)

    fun require(id: String): FilmProfile =
        checkNotNull(find(id)) { "Unknown film profile: $id" }

    fun enabled(enabledIds: Set<String>): List<FilmProfile> {
        val filtered = profiles.filter { it.id in enabledIds }
        return filtered.ifEmpty { listOf(default) }
    }

    private fun profile(
        id: String,
        name: String,
        top: Long,
        bottom: Long,
        red: ChannelCurve,
        green: ChannelCurve,
        blue: ChannelCurve,
        crossTalk: Matrix3,
        print: PrintCurve,
        saturation: Float,
        splitTone: SplitTone = SplitTone.None,
        foliageTone: FoliageTone = FoliageTone.None,
        skyTone: SkyTone = SkyTone.None,
        grain: GrainProfile = GrainProfile.None,
        halation: HalationProfile = HalationProfile.None,
        monochromeWeights: RgbWeights? = null,
    ): FilmProfile {
        val tunedPrint = intensifyPrint(print, green)
        val tunedCrossTalk = intensifyCrossTalk(crossTalk)
        val tunedSaturation = if (saturation <= 0f) 0f
        else 1f + soften(saturation - 1f, COLOUR_CHARACTER, SATURATION_HEADROOM)
        val tunedSplitTone = intensifySplitTone(splitTone)
        return FilmProfile(
            id = id,
            displayName = name,
            swatchTop = top,
            swatchBottom = bottom,
            red = red,
            green = green,
            blue = blue,
            crossTalk = tunedCrossTalk,
            print = tunedPrint,
            saturation = tunedSaturation,
            strength = LOOK_STRENGTH,
            splitTone = tunedSplitTone,
            foliageTone = foliageTone,
            skyTone = skyTone,
            grain = grain,
            halation = halation,
            monochromeWeights = monochromeWeights,
            previewColorMatrix = previewMatrix(
                redGain = red.gain,
                greenGain = green.gain,
                blueGain = blue.gain,
                crossTalk = tunedCrossTalk,
                saturation = tunedSaturation,
                strength = LOOK_STRENGTH,
                splitTone = tunedSplitTone,
                monochromeWeights = monochromeWeights,
            ),
        )
    }

    /**
     * Amplify the print curve's departure from the stock's own neutral response.
     *
     * A stock's negative slope and print contrast multiply in logit space, and the reference's
     * authored pairs very nearly cancel: measured over real Pixel 8 frames, Portra 400's combined
     * slope was 0.995, so the stock contributed under 1 ΔE on top of the scene development. That
     * was tolerable for a comparatively flat scan but not for a Pixel-class capture, which already
     * arrives tone-mapped and saturated from HDR+.
     *
     * The neutral here is the print contrast that would exactly cancel the negative — the stock's
     * own zero. Amplifying around it makes a flat stock flatter and a contrasty one contrastier
     * rather than dragging every look toward one shared S-curve, so the authored relationships
     * between stocks survive.
     *
     * Toe and shoulder lift shadows and compress highlights, so on a stock that is already flatter
     * than neutral they add to the flattening. They therefore take the same gain as that stock's
     * contrast, which keeps the whole print curve moving as one.
     */
    private fun intensifyPrint(print: PrintCurve, green: ChannelCurve): PrintCurve {
        val neutral = 1f / (1f + green.contrast.coerceIn(-.5f, 1f) * .34f)
        // Amplifying a flat stock's flatness increases its measured distance from the source while
        // reading as a *weaker* look, not a stronger one — washed out is the very complaint this
        // tuning exists to fix. Added contrast is amplified fully; a reduction stays near what the
        // stock authored and lets its character come from colour instead.
        // A contrasty print is amplified harder than a flat one, but across a band rather than at a
        // line. This used to be `if (print.contrast >= neutral)`, and because `neutral` is derived
        // from the green channel's contrast, which stock landed on which side had almost nothing to
        // do with how far apart the two were authored: Portra 400 sits .005 below the boundary and
        // Portra 800 .030 above it, so a difference of .02 in the authored numbers bought a gain
        // 2.08 times larger. Portra 800 authors a *lower* shoulder than Portra 400 and rendered one
        // 53% higher, which is most of why the two stocks diverged as far as they did.
        val character = ColorMath.smoothstep(
            neutral - CHARACTER_BLEND,
            neutral + CHARACTER_BLEND,
            print.contrast,
        )
        val gain = FLATNESS_CHARACTER + (TONE_CHARACTER - FLATNESS_CHARACTER) * character
        return print.copy(
            contrast = neutral + soften(print.contrast - neutral, gain, CONTRAST_HEADROOM),
            toe = soften(print.toe, gain, TOE_HEADROOM),
            shoulder = soften(print.shoulder, gain, SHOULDER_HEADROOM),
        )
    }

    /**
     * Amplify [deviation] by [gain] with diminishing returns, saturating toward [headroom].
     *
     * A flat multiplier suits the stocks authored close to neutral but drives the expressive ones
     * into the renderer's parameter clamps — amplified linearly, Eterna's already-flat print curve
     * clipped and rendered bleached rather than soft. Because tanh is approximately linear near
     * zero, a mild stock still receives very nearly the full [gain] while a strong one lands inside
     * the model's usable range and keeps its shape.
     */
    private fun soften(deviation: Float, gain: Float, headroom: Float): Float =
        headroom * tanh(deviation * gain / headroom)

    /**
     * Amplify channel interaction around the identity matrix. Every row's entries deviate from
     * identity by a set that sums to zero, so scaling them preserves the row sums and the stock's
     * neutral axis stays neutral.
     */
    private fun intensifyCrossTalk(matrix: Matrix3) = Matrix3(
        1f + (matrix.m00 - 1f) * CROSS_TALK_CHARACTER,
        matrix.m01 * CROSS_TALK_CHARACTER,
        matrix.m02 * CROSS_TALK_CHARACTER,
        matrix.m10 * CROSS_TALK_CHARACTER,
        1f + (matrix.m11 - 1f) * CROSS_TALK_CHARACTER,
        matrix.m12 * CROSS_TALK_CHARACTER,
        matrix.m20 * CROSS_TALK_CHARACTER,
        matrix.m21 * CROSS_TALK_CHARACTER,
        1f + (matrix.m22 - 1f) * CROSS_TALK_CHARACTER,
    )

    /**
     * Scale the tint magnitudes rather than [SplitTone.amount]: the authored amounts already sit
     * near the top of their range, so scaling them would only clip against it.
     */
    private fun intensifySplitTone(splitTone: SplitTone) = SplitTone(
        shadowR = splitTone.shadowR * SPLIT_TONE_CHARACTER,
        shadowG = splitTone.shadowG * SPLIT_TONE_CHARACTER,
        shadowB = splitTone.shadowB * SPLIT_TONE_CHARACTER,
        highlightR = splitTone.highlightR * SPLIT_TONE_CHARACTER,
        highlightG = splitTone.highlightG * SPLIT_TONE_CHARACTER,
        highlightB = splitTone.highlightB * SPLIT_TONE_CHARACTER,
        amount = splitTone.amount,
    )

    /**
     * How far each stock's authored response is amplified for computational-camera input.
     *
     * These are the adaptation, not the stock data: the per-stock numbers above remain the
     * reference's. The values were chosen by sweeping candidates over real Pixel 8 and 8 Pro
     * frames and measuring each stock's contribution *above* the scene-development stage, which is
     * the part a viewer reads as "the film look". Past roughly these values the curve of
     * improvement flattens while saturation keeps falling, so they sit at the knee.
     */
    private const val TONE_CHARACTER = 2.6f
    private const val FLATNESS_CHARACTER = 1.25f
    private const val COLOUR_CHARACTER = 2.4f

    /**
     * How far either side of a stock's own neutral the two characters are blended across. Wide
     * enough that the catalogue's prints, which are authored within a few hundredths of each other,
     * land on a ramp rather than either side of a step.
     */
    private const val CHARACTER_BLEND = .06f

    /**
     * Cross-talk is channel bleed, so amplifying it desaturates on its own. It gets a gentler gain
     * than [COLOUR_CHARACTER] to keep the two from compounding into a grey, near-monochrome render
     * on the stocks that authored the most bleed.
     */
    private const val CROSS_TALK_CHARACTER = 1.6f
    private const val SPLIT_TONE_CHARACTER = 1.8f

    /**
     * Saturation points for [soften], each set inside the renderer's own clamp for that parameter
     * so no stock is amplified into a hard limit and flattened against it.
     */
    private const val CONTRAST_HEADROOM = .30f
    private const val TOE_HEADROOM = .42f

    /**
     * The shoulder saturates far lower than it did.
     *
     * At .95 the amplifier was free to roughly double an authored shoulder, and the print's
     * shoulder term is subtracted as `shoulder * density²` — so it bites hardest exactly at the top
     * of the range. Measured on real frames, a stock that reached .76 here put a sunlit cloud out
     * at luminance 190 and left barely one percent of the picture above 200, which is a sky with no
     * white in it. Held here, the amplifier still separates a contrasty print from a flat one while
     * the highlights keep somewhere to go.
     */
    private const val SHOULDER_HEADROOM = .55f
    private const val SATURATION_HEADROOM = .26f

    /**
     * No dilution toward the untouched capture. The reference's sub-1 look strengths kept a
     * LUT-based look restrained; with the response above carrying the stock's character, blending
     * the source back in would only undo what the stock had just established.
     */
    private const val LOOK_STRENGTH = 1f

    private fun channel(contrast: Float, toe: Float, shoulder: Float, gain: Float = 1f) =
        ChannelCurve(contrast, toe, shoulder, gain)

    private fun print(
        contrast: Float,
        toe: Float,
        shoulder: Float,
        exposureEv: Float = 0f,
        blackPoint: Float = .0005f,
        paperWhite: Float = .992f,
    ) = PrintCurve(contrast, toe, shoulder, exposureEv, blackPoint, paperWhite)

    private fun split(
        sr: Float, sg: Float, sb: Float,
        hr: Float, hg: Float, hb: Float,
        amount: Float,
    ) = SplitTone(sr, sg, sb, hr, hg, hb, amount)

    private fun grain(
        amount: Float,
        size: Float,
        clumping: Float,
        seed: Long,
        highlightPersistence: Float = 0f,
    ) = GrainProfile(amount, size, .52f, clumping, .06f, seed, highlightPersistence)

    private fun crossTalk(amount: Float, warm: Float = 0f): Matrix3 = Matrix3(
        1f - 2f * amount + warm, amount, amount - warm,
        amount, 1f - 2f * amount, amount,
        amount - warm, amount + warm, 1f - 2f * amount,
    )

    private fun previewMatrix(
        redGain: Float,
        greenGain: Float,
        blueGain: Float,
        crossTalk: Matrix3,
        saturation: Float,
        strength: Float,
        splitTone: SplitTone,
        monochromeWeights: RgbWeights?,
    ): FloatArray {
        val base = monochromeWeights?.let { weights ->
            Matrix3(
                weights.red, weights.green, weights.blue,
                weights.red, weights.green, weights.blue,
                weights.red, weights.green, weights.blue,
            )
        } ?: Matrix3(
            crossTalk.m00 * redGain, crossTalk.m01 * greenGain, crossTalk.m02 * blueGain,
            crossTalk.m10 * redGain, crossTalk.m11 * greenGain, crossTalk.m12 * blueGain,
            crossTalk.m20 * redGain, crossTalk.m21 * greenGain, crossTalk.m22 * blueGain,
        )
        val sat = saturation.coerceIn(0f, 1.35f)
        val inv = 1f - sat
        val saturationMatrix = Matrix3(
            inv * .2126f + sat, inv * .7152f, inv * .0722f,
            inv * .2126f, inv * .7152f + sat, inv * .0722f,
            inv * .2126f, inv * .7152f, inv * .0722f + sat,
        )
        val colored = multiply(saturationMatrix, base)
        val mixed = mix(Matrix3.Identity, colored, strength)
        val redOffset = (splitTone.shadowR + splitTone.highlightR) * splitTone.amount * 90f
        val greenOffset = (splitTone.shadowG + splitTone.highlightG) * splitTone.amount * 90f
        val blueOffset = (splitTone.shadowB + splitTone.highlightB) * splitTone.amount * 90f
        return floatArrayOf(
            mixed.m00, mixed.m01, mixed.m02, 0f, redOffset,
            mixed.m10, mixed.m11, mixed.m12, 0f, greenOffset,
            mixed.m20, mixed.m21, mixed.m22, 0f, blueOffset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun multiply(a: Matrix3, b: Matrix3) = Matrix3(
        a.m00 * b.m00 + a.m01 * b.m10 + a.m02 * b.m20,
        a.m00 * b.m01 + a.m01 * b.m11 + a.m02 * b.m21,
        a.m00 * b.m02 + a.m01 * b.m12 + a.m02 * b.m22,
        a.m10 * b.m00 + a.m11 * b.m10 + a.m12 * b.m20,
        a.m10 * b.m01 + a.m11 * b.m11 + a.m12 * b.m21,
        a.m10 * b.m02 + a.m11 * b.m12 + a.m12 * b.m22,
        a.m20 * b.m00 + a.m21 * b.m10 + a.m22 * b.m20,
        a.m20 * b.m01 + a.m21 * b.m11 + a.m22 * b.m21,
        a.m20 * b.m02 + a.m21 * b.m12 + a.m22 * b.m22,
    )

    private fun mix(a: Matrix3, b: Matrix3, amount: Float): Matrix3 {
        fun value(x: Float, y: Float) = x + (y - x) * amount
        return Matrix3(
            value(a.m00, b.m00), value(a.m01, b.m01), value(a.m02, b.m02),
            value(a.m10, b.m10), value(a.m11, b.m11), value(a.m12, b.m12),
            value(a.m20, b.m20), value(a.m21, b.m21), value(a.m22, b.m22),
        )
    }
}
