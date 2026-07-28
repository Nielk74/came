package com.nielk74.came.camera

import kotlin.math.abs
import kotlin.math.roundToInt

/** The five physical-looking exposure detents camé exposes to the photographer. */
enum class ExposureValue(val ev: Int) {
    MINUS_TWO(-2),
    MINUS_ONE(-1),
    ZERO(0),
    PLUS_ONE(1),
    PLUS_TWO(2),
    ;

    val label: String
        get() = if (ev > 0) "+$ev" else ev.toString()

    val accessibilityLabel: String
        get() = when {
            ev < 0 -> "EV minus ${-ev}"
            ev > 0 -> "EV plus $ev"
            else -> "EV zero"
        }

    companion object {
        fun closestTo(ev: Float): ExposureValue =
            if (ev.isFinite()) entries.minBy { abs(it.ev - ev) } else ZERO
    }
}

/**
 * The user-facing detent and the value CameraX actually accepted are kept separate.
 *
 * A device can expose a narrower range than -2..+2 EV, or use a step which cannot represent a
 * whole stop exactly. [appliedEv] lets callers report that limitation without making the
 * thumbwheel itself device-specific.
 */
data class ExposureControlState(
    val selectedValue: ExposureValue = ExposureValue.ZERO,
    val isSupported: Boolean = false,
    val isApplying: Boolean = false,
    val appliedIndex: Int = 0,
    val appliedEv: Float = 0f,
)

internal data class ExposureCompensationMapping(
    val index: Int,
    val ev: Float,
)

/**
 * Converts photographic stops into CameraX's device-specific compensation index.
 *
 * CameraX describes one index using a rational EV step. Rounding chooses the closest device
 * setting and the final clamp guarantees the request is inside the reported index range.
 * Malformed/zero step metadata falls back to the safest in-range index instead of dividing by
 * zero or passing an arbitrary value to the camera HAL.
 */
internal fun mapExposureCompensation(
    value: ExposureValue,
    minimumIndex: Int,
    maximumIndex: Int,
    stepNumerator: Int,
    stepDenominator: Int,
): ExposureCompensationMapping {
    val lower = minOf(minimumIndex, maximumIndex)
    val upper = maxOf(minimumIndex, maximumIndex)
    val step = if (stepDenominator == 0) {
        Double.NaN
    } else {
        abs(stepNumerator.toDouble() / stepDenominator.toDouble())
    }
    if (!step.isFinite() || step <= 0.0) {
        val safeIndex = 0.coerceIn(lower, upper)
        return ExposureCompensationMapping(index = safeIndex, ev = 0f)
    }

    // Round equal-distance negative and positive targets symmetrically, away from zero.
    val rawIndex = value.ev / step
    val nearestIndex = if (rawIndex < 0.0) {
        -abs(rawIndex).roundToInt()
    } else {
        rawIndex.roundToInt()
    }
    val index = nearestIndex.coerceIn(lower, upper)
    return ExposureCompensationMapping(
        index = index,
        ev = exposureEvForIndex(index, stepNumerator, stepDenominator),
    )
}

internal fun exposureEvForIndex(
    index: Int,
    numerator: Int,
    denominator: Int,
): Float {
    if (denominator == 0) return 0f
    val step = abs(numerator.toDouble() / denominator.toDouble())
    if (!step.isFinite() || step <= 0.0) return 0f
    return (index * step).toFloat()
}
