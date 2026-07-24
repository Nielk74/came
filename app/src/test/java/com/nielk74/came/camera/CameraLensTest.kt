package com.nielk74.came.camera

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLensTest {
    @Test
    fun physicalRatiosBecomeSortedNamedSuggestions() {
        val lenses = recommendCameraLenses(
            physicalCameraRatios = listOf(4.98f, .51f, 1f),
            minimumZoomRatio = .5f,
            maximumZoomRatio = 30f,
        )

        assertEquals(listOf("0.5×", "1×", "5×"), lenses.map { it.ratioLabel })
        assertEquals(
            listOf("ULTRA WIDE", "WIDE", "LONG TELE"),
            lenses.map { it.roleLabel },
        )
        assertEquals("ULTRA WIDE camera view, 0.5 times", lenses.first().accessibilityLabel)
    }

    @Test
    fun minimumZoomSuppliesUltraWideFallbackWhenPhysicalMetadataIsHidden() {
        val lenses = recommendCameraLenses(
            physicalCameraRatios = emptyList(),
            minimumZoomRatio = .55f,
            maximumZoomRatio = 8f,
        )

        assertEquals(listOf("0.6×", "1×"), lenses.map { it.ratioLabel })
    }

    @Test
    fun digitalMaximumDoesNotInventATelephotoLens() {
        val lenses = recommendCameraLenses(
            physicalCameraRatios = emptyList(),
            minimumZoomRatio = 1f,
            maximumZoomRatio = 10f,
        )

        assertEquals(listOf("1×"), lenses.map { it.ratioLabel })
    }

    @Test
    fun duplicateAndUnsupportedRatiosAreRemoved() {
        val lenses = recommendCameraLenses(
            physicalCameraRatios = listOf(Float.NaN, -.5f, .49f, .5f, .52f, 1.03f, 6f),
            minimumZoomRatio = .5f,
            maximumZoomRatio = 5f,
        )

        assertEquals(listOf("0.5×", "1×"), lenses.map { it.ratioLabel })
        assertTrue(lenses.all { it.zoomRatio in .5f..5f })
    }

    @Test
    fun closestSuggestionTracksTheSelectedFieldOfView() {
        val lenses = recommendCameraLenses(listOf(.5f, 1f, 5f), .5f, 20f)

        assertEquals("5×", closestCameraLens(lenses, 4.7f)?.ratioLabel)
        assertEquals("1×", closestCameraLens(lenses, 1.2f)?.ratioLabel)
    }

    @Test
    fun onlyPersistentInvalidRatioFailuresRemoveASuggestion() {
        assertTrue(shouldRemoveLensAfter(IllegalArgumentException()))
        assertTrue(shouldRemoveLensAfter(ExecutionException(IllegalArgumentException())))
        assertFalse(shouldRemoveLensAfter(CancellationException()))
        assertFalse(shouldRemoveLensAfter(ExecutionException(IllegalStateException())))
    }
}
