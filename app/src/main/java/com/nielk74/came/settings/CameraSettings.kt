package com.nielk74.came.settings

import com.nielk74.came.filters.FilmCatalog
import com.nielk74.came.filters.FilmProfile

data class CameraSettings(
    val grainEnabled: Boolean = true,
    val enabledFilterIds: Set<String> = FilmCatalog.profiles.mapTo(linkedSetOf()) { it.id },
    val selectedFilterId: String = FilmCatalog.default.id,
    val timerSeconds: Int = 0,
) {
    val selectedProfile: FilmProfile
        get() = FilmCatalog.find(selectedFilterId) ?: FilmCatalog.default

    val enabledProfiles: List<FilmProfile>
        get() = FilmCatalog.enabled(enabledFilterIds)

    /** Repairs data written by an older catalog or interrupted preference migration. */
    fun normalized(): CameraSettings {
        val validIds = FilmCatalog.profiles.mapTo(linkedSetOf()) { it.id }
        val enabled = enabledFilterIds.filterTo(linkedSetOf()) { it in validIds }
            .ifEmpty { linkedSetOf(FilmCatalog.default.id) }
        val selected = selectedFilterId.takeIf { it in enabled } ?: enabled.first()
        val timer = timerSeconds.takeIf { it in TIMER_CHOICES } ?: 0
        return copy(
            enabledFilterIds = enabled,
            selectedFilterId = selected,
            timerSeconds = timer,
        )
    }

    companion object {
        val TIMER_CHOICES: List<Int> = listOf(0, 3, 5, 10)
        val Default = CameraSettings()
    }
}
