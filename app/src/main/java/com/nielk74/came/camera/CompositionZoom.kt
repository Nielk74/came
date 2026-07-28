package com.nielk74.came.camera

import kotlin.math.roundToInt

/**
 * The viewfinder's composition-only zoom.
 *
 * This deliberately does not drive CameraX's digital zoom. The camera captures the complete
 * sensor frame, the film renderer finishes every pass at full resolution, and [CameraCaptureStore]
 * applies the matching centered crop as the final bitmap operation.
 */
@JvmInline
value class CompositionZoom private constructor(val factor: Float) {
    /** Applies one incremental scale change from a pinch gesture. */
    fun scaledBy(scaleChange: Float): CompositionZoom {
        if (!scaleChange.isFinite() || scaleChange <= 0f) return this
        return of(factor * scaleChange)
    }

    companion object {
        const val MIN_FACTOR = 1f
        const val MAX_FACTOR = 4f

        val Identity = CompositionZoom(MIN_FACTOR)

        /** Creates a finite zoom clamped to the range supported by the final crop. */
        fun of(requestedFactor: Float): CompositionZoom {
            val finiteFactor = when {
                requestedFactor.isNaN() -> MIN_FACTOR
                requestedFactor == Float.POSITIVE_INFINITY -> MAX_FACTOR
                requestedFactor == Float.NEGATIVE_INFINITY -> MIN_FACTOR
                else -> requestedFactor
            }
            return CompositionZoom(finiteFactor.coerceIn(MIN_FACTOR, MAX_FACTOR))
        }
    }
}

/** The exact source pixels retained by a composition zoom. */
internal data class CenterCropWindow(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    fun isIdentityFor(sourceWidth: Int, sourceHeight: Int): Boolean =
        left == 0 && top == 0 && width == sourceWidth && height == sourceHeight
}

/**
 * Calculates a centered crop without depending on Android bitmap classes, keeping the crop
 * geometry directly testable on the host JVM.
 */
internal fun centeredCropWindow(
    sourceWidth: Int,
    sourceHeight: Int,
    zoom: CompositionZoom,
): CenterCropWindow {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }

    val cropWidth = (sourceWidth / zoom.factor).roundToInt().coerceIn(1, sourceWidth)
    val cropHeight = (sourceHeight / zoom.factor).roundToInt().coerceIn(1, sourceHeight)
    return CenterCropWindow(
        left = (sourceWidth - cropWidth) / 2,
        top = (sourceHeight - cropHeight) / 2,
        width = cropWidth,
        height = cropHeight,
    )
}

/**
 * Reference implementation used by unit tests to prove which pixels [centeredCropWindow] keeps.
 * Production applies that same window directly to the already-rendered bitmap.
 */
internal fun centeredCropPixels(
    pixels: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    zoom: CompositionZoom,
): IntArray {
    require(pixels.size == sourceWidth * sourceHeight) {
        "Pixel count must match source dimensions"
    }
    val crop = centeredCropWindow(sourceWidth, sourceHeight, zoom)
    if (crop.isIdentityFor(sourceWidth, sourceHeight)) return pixels

    return IntArray(crop.width * crop.height).also { output ->
        for (row in 0 until crop.height) {
            pixels.copyInto(
                destination = output,
                destinationOffset = row * crop.width,
                startIndex = (crop.top + row) * sourceWidth + crop.left,
                endIndex = (crop.top + row) * sourceWidth + crop.left + crop.width,
            )
        }
    }
}
