package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmCatalogTest {
    @Test
    fun stockNamesAndOrderMatchTheSourceCatalog() {
        assertEquals(
            listOf(
                "Portra 400", "Portra 800", "Gold 200", "Ektar 100", "Superia 400",
                "CineStill 800T", "Vision3 250D", "Vision3 500T", "Eterna Cinema",
                "Tri-X 400", "HP5 Plus",
            ),
            FilmCatalog.profiles.map { it.displayName },
        )
        assertEquals(FilmCatalog.profiles.size, FilmCatalog.profiles.map { it.id }.toSet().size)
    }

    @Test
    fun everyProfileHasAUsableLivePreviewMatrix() {
        FilmCatalog.profiles.forEach { profile ->
            assertEquals(profile.displayName, 20, profile.previewColorMatrix.size)
            assertTrue(profile.displayName, profile.previewColorMatrix.all(Float::isFinite))
        }
    }

    @Test
    fun catalogRetainsStockSpecificSpatialIntent() {
        assertTrue(FilmCatalog.require("cinestill800t").halation.enabled)
        assertTrue(FilmCatalog.require("portra400").grain.enabled)
        assertEquals(0f, FilmCatalog.require("trix400").saturation)
        assertEquals(0f, FilmCatalog.require("hp5").saturation)
    }

    @Test
    fun selectiveFoliageAndSkyColourStayScopedToTheStocksThatAuthorIt() {
        val withSelectiveColour = FilmCatalog.profiles
            .filter { it.foliageTone.enabled || it.skyTone.enabled }
            .map { it.id }
        assertEquals(listOf("portra400", "portra800"), withSelectiveColour)

        FilmCatalog.profiles.forEach { profile ->
            assertTrue(profile.displayName, profile.foliageTone.cyanShift in 0f..1f)
            assertTrue(profile.displayName, profile.skyTone.cyanShift in 0f..0.45f)
            assertTrue(profile.displayName, profile.foliageTone.saturationBoost in 0f..0.5f)
            assertTrue(profile.displayName, profile.skyTone.saturationBoost in 0f..0.5f)
        }
    }

    @Test
    fun anEmptyEnabledSetStillReturnsSafeDefault() {
        assertEquals(listOf(FilmCatalog.default), FilmCatalog.enabled(emptySet()))
    }
}
