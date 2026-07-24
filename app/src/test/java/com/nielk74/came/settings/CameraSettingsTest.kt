package com.nielk74.came.settings

import com.nielk74.came.filters.FilmCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSettingsTest {
    @Test
    fun defaultsExposeEveryProfileAndSelectPortra400() {
        val settings = CameraSettings.Default
        assertEquals(FilmCatalog.profiles.map { it.id }.toSet(), settings.enabledFilterIds)
        assertEquals("portra400", settings.selectedFilterId)
        assertTrue(settings.grainEnabled)
    }

    @Test
    fun normalizedDropsRemovedFiltersAndRepairsSelectionAndTimer() {
        val settings = CameraSettings(
            enabledFilterIds = setOf("removed", "hp5"),
            selectedFilterId = "removed",
            timerSeconds = 17,
        ).normalized()

        assertEquals(setOf("hp5"), settings.enabledFilterIds)
        assertEquals("hp5", settings.selectedFilterId)
        assertEquals(0, settings.timerSeconds)
    }

    @Test
    fun normalizedNeverLeavesCameraWithoutAFilter() {
        val settings = CameraSettings(
            enabledFilterIds = emptySet(),
            selectedFilterId = "missing",
        ).normalized()

        assertEquals(setOf(FilmCatalog.default.id), settings.enabledFilterIds)
        assertEquals(FilmCatalog.default.id, settings.selectedFilterId)
    }
}
