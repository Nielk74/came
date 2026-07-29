package com.nielk74.came.level

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round

/** Default tolerance used by camera-style electronic levels. */
const val DEFAULT_LEVEL_TOLERANCE_DEGREES = 1.5f

/**
 * Pure orientation math kept separate from Android's sensor APIs so the behavior can be covered by
 * local tests.
 */
internal object ElectronicLevelMath {
    /**
     * Finds the horizon roll from gravity projected into the device plane.
     *
     * camé's UI stays portrait locked, so display rotation cannot identify a landscape pose. The
     * gravity angle is instead measured relative to its nearest cardinal orientation. Portrait,
     * reverse portrait, and both landscapes therefore all report zero when held level. A value is
     * unavailable when the phone is close to lying flat because gravity then has no stable
     * screen-plane direction.
     */
    fun rollDegreesFromGravity(
        gravityX: Float,
        gravityY: Float,
    ): Float? {
        val projectedMagnitudeSquared = gravityX * gravityX + gravityY * gravityY
        if (projectedMagnitudeSquared < MINIMUM_GRAVITY_PROJECTION_SQUARED) return null

        val naturalRoll = normalizeDegrees(
            Math.toDegrees(atan2(gravityX.toDouble(), -gravityY.toDouble())).toFloat(),
        )
        val nearestCardinal = round(naturalRoll / QUARTER_TURN_DEGREES) * QUARTER_TURN_DEGREES
        return normalizeDegrees(naturalRoll - nearestCardinal)
    }

    fun isNearLevel(
        rollDegrees: Float,
        toleranceDegrees: Float = DEFAULT_LEVEL_TOLERANCE_DEGREES,
    ): Boolean = abs(normalizeDegrees(rollDegrees)) <= toleranceDegrees

    fun normalizeDegrees(degrees: Float): Float {
        var normalized = (degrees + HALF_TURN_DEGREES) % FULL_TURN_DEGREES
        if (normalized < 0f) normalized += FULL_TURN_DEGREES
        return normalized - HALF_TURN_DEGREES
    }

    private const val MINIMUM_GRAVITY_PROJECTION_SQUARED = 0.01f
    private const val QUARTER_TURN_DEGREES = 90f
    private const val HALF_TURN_DEGREES = 180f
    private const val FULL_TURN_DEGREES = 360f
}

/**
 * Low-pass filter for angles. Interpolating the shortest circular delta avoids a false sweep
 * through zero when sensor values cross from +180 to -180 degrees.
 */
internal class CircularAngleSmoother(
    private val smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR,
) {
    init {
        require(smoothingFactor in 0f..1f) {
            "smoothingFactor must be between 0 and 1"
        }
    }

    private var previousDegrees: Float? = null

    fun update(measuredDegrees: Float): Float {
        val measurement = ElectronicLevelMath.normalizeDegrees(measuredDegrees)
        val previous = previousDegrees
        val smoothed = if (previous == null) {
            measurement
        } else {
            val shortestDelta = ElectronicLevelMath.normalizeDegrees(measurement - previous)
            ElectronicLevelMath.normalizeDegrees(previous + shortestDelta * smoothingFactor)
        }
        previousDegrees = smoothed
        return smoothed
    }

    fun reset() {
        previousDegrees = null
    }

    private companion object {
        const val DEFAULT_SMOOTHING_FACTOR = 0.18f
    }
}
