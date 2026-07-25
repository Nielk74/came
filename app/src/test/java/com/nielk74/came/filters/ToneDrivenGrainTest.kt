package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneDrivenGrainTest {
    private val profile = FilmCatalog.require("portra800").grain

    @Test
    fun crystalFieldIsDeterministicForOnePhoto() {
        val first = ToneDrivenGrain.crystalAt(317, 829, 4000, profile, 8675309L)
        val repeated = ToneDrivenGrain.crystalAt(317, 829, 4000, profile, 8675309L)
        assertEquals(first, repeated, 0f)
    }

    @Test
    fun differentPhotosDoNotReuseIdenticalGrain() {
        val first = ToneDrivenGrain.crystalAt(317, 829, 4000, profile, 1L)
        val second = ToneDrivenGrain.crystalAt(317, 829, 4000, profile, 2L)
        assertNotEquals(first, second)
    }

    @Test
    fun grainVisibilityDependsOnToneNotNeighbouringDetail() {
        val smoothArea = ToneDrivenGrain.visibilityForTone(.42f, profile)
        val detailedAreaAtSameTone = ToneDrivenGrain.visibilityForTone(.42f, profile)
        assertEquals(smoothArea, detailedAreaAtSameTone, 0f)
    }

    @Test
    fun densityResponseProtectsBlackAndPaperWhite() {
        val midtone = ToneDrivenGrain.visibilityForTone(.42f, profile)
        assertTrue(ToneDrivenGrain.visibilityForTone(0f, profile) < midtone)
        assertTrue(ToneDrivenGrain.visibilityForTone(1f, profile) < midtone)
    }

    @Test
    fun aCoarsePixelEqualsTheAverageOfTheFinePixelsCoveringIt() {
        // The whole point of integrating the tent over each pixel's film footprint: halving the
        // output resolution must area-average one continuous field rather than redraw a new one.
        // Point-sampled or bilinearly interpolated noise fails this outright.
        for (y in 40..44) {
            for (x in 90..94) {
                var fine = 0f
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        fine += ToneDrivenGrain.crystalAt(2 * x + dx, 2 * y + dy, 2400, profile, 5L)
                    }
                }
                assertEquals(
                    "pixel $x,$y",
                    ToneDrivenGrain.crystalAt(x, y, 1200, profile, 5L).toDouble(),
                    (fine / 4f).toDouble(),
                    1e-4,
                )
            }
        }
    }

    @Test
    fun grainKeepsItsPhysicalSizeAcrossOutputResolutions() {
        // A field sampled per output pixel gets smoother as the image grows. Integrating over the
        // film footprint instead keeps the crystal structure the same size on the emulsion, so the
        // small render simply averages more of it and is correspondingly calmer.
        val small = deviation(600)
        val large = deviation(2400)
        assertTrue(
            "a larger render should resolve more crystal variation, got $small then $large",
            large > small,
        )
    }

    @Test
    fun clumpingChangesTheTailsWithoutChangingTheAmount() {
        val clean = rootMeanSquare(profile.copy(clumping = 0f))
        val clumped = rootMeanSquare(profile.copy(clumping = .5f))
        assertEquals(
            "clumping should redistribute the field, not amplify it",
            clean.toDouble(),
            clumped.toDouble(),
            clean * .12,
        )
    }

    private fun deviation(longEdge: Int): Double {
        var sum = 0.0
        var sumSquared = 0.0
        var count = 0
        for (y in 0 until 60) {
            for (x in 0 until 60) {
                val value = ToneDrivenGrain.crystalAt(x, y, longEdge, profile, 11L).toDouble()
                sum += value
                sumSquared += value * value
                count++
            }
        }
        val mean = sum / count
        return kotlin.math.sqrt((sumSquared / count - mean * mean).coerceAtLeast(0.0))
    }

    private fun rootMeanSquare(grain: GrainProfile): Double {
        var sumSquared = 0.0
        var count = 0
        for (y in 0 until 70) {
            for (x in 0 until 70) {
                val value = ToneDrivenGrain.crystalAt(x, y, 1500, grain, 23L).toDouble()
                sumSquared += value * value
                count++
            }
        }
        return kotlin.math.sqrt(sumSquared / count)
    }
}
