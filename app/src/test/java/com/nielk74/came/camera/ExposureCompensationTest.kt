package com.nielk74.came.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureCompensationTest {
    @Test
    fun wheelHasFiveSignedPhotographicLabels() {
        assertEquals(
            listOf("-2", "-1", "0", "+1", "+2"),
            ExposureValue.entries.map(ExposureValue::label),
        )
        assertEquals("EV minus 2", ExposureValue.MINUS_TWO.accessibilityLabel)
        assertEquals("EV zero", ExposureValue.ZERO.accessibilityLabel)
        assertEquals("EV plus 2", ExposureValue.PLUS_TWO.accessibilityLabel)
    }

    @Test
    fun thirdStopDeviceMapsWholeEvDetentsToExactIndices() {
        val mappings = ExposureValue.entries.map {
            mapExposureCompensation(
                value = it,
                minimumIndex = -12,
                maximumIndex = 12,
                stepNumerator = 1,
                stepDenominator = 3,
            )
        }

        assertEquals(listOf(-6, -3, 0, 3, 6), mappings.map { it.index })
        assertEquals(-2f, mappings.first().ev, 0.0001f)
        assertEquals(2f, mappings.last().ev, 0.0001f)
    }

    @Test
    fun requestIsRoundedToNearestRepresentableCompensation() {
        val positive = mapExposureCompensation(
            value = ExposureValue.PLUS_ONE,
            minimumIndex = -4,
            maximumIndex = 4,
            stepNumerator = 2,
            stepDenominator = 3,
        )
        val negative = mapExposureCompensation(
            value = ExposureValue.MINUS_ONE,
            minimumIndex = -4,
            maximumIndex = 4,
            stepNumerator = 2,
            stepDenominator = 3,
        )

        assertEquals(2, positive.index)
        assertEquals(4f / 3f, positive.ev, 0.0001f)
        assertEquals(-2, negative.index)
        assertEquals(-4f / 3f, negative.ev, 0.0001f)
    }

    @Test
    fun deviceRangeClampsTheFiveUserFacingChoices() {
        val low = mapExposureCompensation(
            value = ExposureValue.MINUS_TWO,
            minimumIndex = -2,
            maximumIndex = 3,
            stepNumerator = 1,
            stepDenominator = 2,
        )
        val high = mapExposureCompensation(
            value = ExposureValue.PLUS_TWO,
            minimumIndex = -2,
            maximumIndex = 3,
            stepNumerator = 1,
            stepDenominator = 2,
        )

        assertEquals(-2, low.index)
        assertEquals(-1f, low.ev, 0.0001f)
        assertEquals(3, high.index)
        assertEquals(1.5f, high.ev, 0.0001f)
    }

    @Test
    fun invalidStepMetadataFallsBackToSafeInRangeZero() {
        assertEquals(
            ExposureCompensationMapping(index = 0, ev = 0f),
            mapExposureCompensation(
                value = ExposureValue.PLUS_TWO,
                minimumIndex = -2,
                maximumIndex = 2,
                stepNumerator = 0,
                stepDenominator = 0,
            ),
        )
        assertEquals(
            ExposureCompensationMapping(index = 2, ev = 0f),
            mapExposureCompensation(
                value = ExposureValue.MINUS_TWO,
                minimumIndex = 2,
                maximumIndex = 5,
                stepNumerator = 0,
                stepDenominator = 1,
            ),
        )
    }

    @Test
    fun continuousAccessibilityInputSnapsToNearestWheelDetent() {
        assertEquals(ExposureValue.MINUS_TWO, ExposureValue.closestTo(-1.8f))
        assertEquals(ExposureValue.ZERO, ExposureValue.closestTo(0.2f))
        assertEquals(ExposureValue.PLUS_TWO, ExposureValue.closestTo(9f))
        assertEquals(ExposureValue.ZERO, ExposureValue.closestTo(Float.NaN))
    }
}
