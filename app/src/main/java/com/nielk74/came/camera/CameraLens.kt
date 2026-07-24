package com.nielk74.came.camera

import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.roundToInt

/** A device-reported camera field-of-view shortcut exposed in the viewfinder. */
data class CameraLens(
    val zoomRatio: Float,
    val ratioLabel: String,
    val roleLabel: String,
) {
    val accessibilityLabel: String
        get() = "$roleLabel camera view, ${ratioLabel.removeSuffix("×")} times"
}

/**
 * Turns unordered, approximate CameraX intrinsic ratios into stable viewfinder suggestions.
 *
 * Physical-camera ratios are preferred. [minimumZoomRatio] is only used as an ultra-wide
 * fallback because a maximum zoom value alone cannot distinguish a telephoto lens from digital
 * zoom. The default rear camera is guaranteed by CameraX to have an intrinsic ratio of 1x.
 */
internal fun recommendCameraLenses(
    physicalCameraRatios: Iterable<Float>,
    minimumZoomRatio: Float,
    maximumZoomRatio: Float,
): List<CameraLens> {
    val minimum = minimumZoomRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val maximum = maximumZoomRatio.takeIf { it.isFinite() && it >= minimum } ?: 1f
    val candidates = buildList {
        add(1f.coerceIn(minimum, maximum))
        physicalCameraRatios.forEach { ratio ->
            if (ratio.isFinite() && ratio > 0f && ratio in minimum..maximum) add(ratio)
        }
        if (minimum < ULTRA_WIDE_THRESHOLD && none { it < ULTRA_WIDE_THRESHOLD }) add(minimum)
    }.sorted()

    val distinct = mutableListOf<Float>()
    candidates.forEach { candidate ->
        val existing = distinct.lastOrNull()
        if (existing == null || abs(candidate - existing) / existing >= RATIO_DEDUPLICATION) {
            distinct += candidate
        } else if (abs(candidate - 1f) < abs(existing - 1f)) {
            distinct[distinct.lastIndex] = candidate
        }
    }
    return distinct.map(::cameraLens)
}

internal fun closestCameraLens(lenses: List<CameraLens>, ratio: Float): CameraLens? =
    lenses.minByOrNull { abs(it.zoomRatio - ratio) }

/** Transient camera closure or a superseding zoom request must not hide a valid view. */
internal fun shouldRemoveLensAfter(failure: Throwable?): Boolean {
    val cause = (failure as? ExecutionException)?.cause ?: failure
    return cause is IllegalArgumentException
}

private fun cameraLens(ratio: Float): CameraLens = CameraLens(
    zoomRatio = ratio,
    ratioLabel = formatRatio(ratio),
    roleLabel = when {
        ratio < ULTRA_WIDE_THRESHOLD -> "ULTRA WIDE"
        ratio < TELEPHOTO_THRESHOLD -> "WIDE"
        ratio < LONG_TELEPHOTO_THRESHOLD -> "TELEPHOTO"
        else -> "LONG TELE"
    },
)

private fun formatRatio(ratio: Float): String {
    val rounded = ratio.roundToInt()
    return if (abs(ratio - rounded) < INTEGER_LABEL_TOLERANCE) {
        "$rounded×"
    } else {
        String.format(Locale.US, "%.1f×", ratio)
    }
}

private const val ULTRA_WIDE_THRESHOLD = .85f
private const val TELEPHOTO_THRESHOLD = 1.5f
private const val LONG_TELEPHOTO_THRESHOLD = 3.5f
private const val RATIO_DEDUPLICATION = .08f
private const val INTEGER_LABEL_TOLERANCE = .05f
