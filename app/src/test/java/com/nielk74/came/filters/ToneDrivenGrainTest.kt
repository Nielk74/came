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
}
