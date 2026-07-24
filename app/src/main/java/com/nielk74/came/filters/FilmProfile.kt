package com.nielk74.came.filters

/**
 * A compact, camera-friendly version of a film stock definition.
 *
 * The profile keeps the point-wise negative/print response used by the Ricoh Film Lab while
 * exposing an Android-compatible 4x5 colour matrix for a cheap live-preview approximation.
 * [FilmProcessor] applies the fuller response to the captured bitmap.
 */
class FilmProfile(
    val id: String,
    val displayName: String,
    val swatchTop: Long,
    val swatchBottom: Long,
    val red: ChannelCurve,
    val green: ChannelCurve,
    val blue: ChannelCurve,
    val crossTalk: Matrix3,
    val print: PrintCurve,
    val saturation: Float,
    val strength: Float,
    val splitTone: SplitTone = SplitTone.None,
    val grain: GrainProfile = GrainProfile.None,
    val halation: HalationProfile = HalationProfile.None,
    val monochromeWeights: RgbWeights? = null,
    previewColorMatrix: FloatArray,
) {
    private val previewMatrix = previewColorMatrix.copyOf()

    /** Row-major 4x5 matrix accepted by Android's ColorMatrix. A fresh copy protects the catalog. */
    val previewColorMatrix: FloatArray
        get() = previewMatrix.copyOf()

    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(strength in 0f..1f)
        require(saturation >= 0f)
        require(previewMatrix.size == 20) { "previewColorMatrix must contain 20 values" }
    }
}

data class ChannelCurve(
    val contrast: Float,
    val toe: Float,
    val shoulder: Float,
    val gain: Float = 1f,
)

data class PrintCurve(
    val contrast: Float,
    val toe: Float,
    val shoulder: Float,
    val exposureEv: Float = 0f,
    val blackPoint: Float = 0.0005f,
    val paperWhite: Float = 0.992f,
)

data class SplitTone(
    val shadowR: Float,
    val shadowG: Float,
    val shadowB: Float,
    val highlightR: Float,
    val highlightG: Float,
    val highlightB: Float,
    val amount: Float,
) {
    companion object {
        val None = SplitTone(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

data class GrainProfile(
    val amount: Float,
    val size: Float,
    val shadowBias: Float,
    val clumping: Float,
    val chroma: Float,
    val seed: Long,
    val highlightPersistence: Float = 0f,
) {
    val enabled: Boolean get() = amount > 0f

    companion object {
        val None = GrainProfile(0f, 1f, 0f, 0f, 0f, 0L)
    }
}

data class HalationProfile(
    val threshold: Float,
    val radius: Int,
    val strength: Float,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
) {
    val enabled: Boolean get() = radius > 0 && strength > 0f

    companion object {
        val None = HalationProfile(1f, 0, 0f, 1f, 0.25f, 0.08f)
    }
}

data class RgbWeights(val red: Float, val green: Float, val blue: Float)

data class Matrix3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float,
) {
    companion object {
        val Identity = Matrix3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}

enum class RenderQuality {
    /** Point-wise colour only, intended for a thumbnail or occasional preview bitmap. */
    PREVIEW,

    /** Full-resolution colour, deterministic film-plane grain, and stock halation. */
    CAPTURE,
}
