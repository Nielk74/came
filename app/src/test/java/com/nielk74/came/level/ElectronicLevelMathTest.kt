package com.nielk74.came.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicLevelMathTest {
    @Test
    fun uprightGravityIsZeroRoll() {
        assertEquals(
            0f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = 0f,
                gravityY = -1f,
            )!!,
            .001f,
        )
    }

    @Test
    fun gravityProjectionReportsSignedRoll() {
        assertEquals(
            10f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = .173648f,
                gravityY = -.984808f,
            )!!,
            .01f,
        )
        assertEquals(
            -10f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = -.173648f,
                gravityY = -.984808f,
            )!!,
            .01f,
        )
    }

    @Test
    fun allCardinalDevicePosesAreLevelWithoutDisplayRotation() {
        assertEquals(
            0f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = 1f,
                gravityY = 0f,
            )!!,
            .001f,
        )
        assertEquals(
            0f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = -1f,
                gravityY = 0f,
            )!!,
            .001f,
        )
        assertEquals(
            0f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = 0f,
                gravityY = 1f,
            )!!,
            .001f,
        )
    }

    @Test
    fun landscapeRollIsMeasuredFromItsNearestHorizontal() {
        assertEquals(
            10f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = .984808f,
                gravityY = .173648f,
            )!!,
            .01f,
        )
        assertEquals(
            -10f,
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = .984808f,
                gravityY = -.173648f,
            )!!,
            .01f,
        )
    }

    @Test
    fun flatPhoneHasNoStableHorizon() {
        assertNull(
            ElectronicLevelMath.rollDegreesFromGravity(
                gravityX = .01f,
                gravityY = -.01f,
            ),
        )
    }

    @Test
    fun nearLevelUsesInclusiveToleranceAcrossNormalizedAngles() {
        assertTrue(ElectronicLevelMath.isNearLevel(1.5f))
        assertTrue(ElectronicLevelMath.isNearLevel(-1.5f))
        assertTrue(ElectronicLevelMath.isNearLevel(359f))
        assertFalse(ElectronicLevelMath.isNearLevel(1.51f))
        assertFalse(ElectronicLevelMath.isNearLevel(358f))
    }

    @Test
    fun smootherAppliesLowPassFiltering() {
        val smoother = CircularAngleSmoother(smoothingFactor = .25f)

        assertEquals(0f, smoother.update(0f), .001f)
        assertEquals(5f, smoother.update(20f), .001f)
        assertEquals(8.75f, smoother.update(20f), .001f)
    }

    @Test
    fun smootherTakesShortestPathAcrossWrapBoundary() {
        val smoother = CircularAngleSmoother(smoothingFactor = .25f)

        assertEquals(179f, smoother.update(179f), .001f)
        assertEquals(179.5f, smoother.update(-179f), .001f)
    }

    @Test
    fun resettingSmootherAcceptsNextReadingImmediately() {
        val smoother = CircularAngleSmoother(smoothingFactor = .1f)
        smoother.update(0f)
        smoother.update(30f)

        smoother.reset()

        assertEquals(-20f, smoother.update(-20f), .001f)
    }
}
