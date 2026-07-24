package com.nielk74.came.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nielk74.came.filters.FilmCatalog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.cameraSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "camera_settings",
)

/** DataStore-backed settings. Every write preserves a valid, enabled selected film profile. */
class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.cameraSettingsDataStore

    val state: Flow<CameraSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::readSettings)
        .distinctUntilChanged()

    suspend fun setGrainEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GrainEnabled] = enabled }
    }

    suspend fun setFilterEnabled(filterId: String, enabled: Boolean) {
        requireNotNull(FilmCatalog.find(filterId)) { "Unknown film profile: $filterId" }
        dataStore.edit { preferences ->
            val current = preferences[Keys.EnabledFilters]
                ?.filterTo(linkedSetOf()) { FilmCatalog.find(it) != null }
                ?.ifEmpty { allFilterIds() }
                ?: allFilterIds()
            if (enabled) {
                current += filterId
            } else if (current.size > 1) {
                current -= filterId
            }
            preferences[Keys.EnabledFilters] = current
            val selected = preferences[Keys.SelectedFilter] ?: FilmCatalog.default.id
            if (selected !in current) {
                preferences[Keys.SelectedFilter] = FilmCatalog.profiles.first { it.id in current }.id
            }
        }
    }

    suspend fun selectFilter(filterId: String) {
        requireNotNull(FilmCatalog.find(filterId)) { "Unknown film profile: $filterId" }
        dataStore.edit { preferences ->
            val enabled = preferences[Keys.EnabledFilters]
                ?.filterTo(linkedSetOf()) { FilmCatalog.find(it) != null }
                ?.ifEmpty { allFilterIds() }
                ?: allFilterIds()
            require(filterId in enabled) { "Cannot select disabled film profile: $filterId" }
            preferences[Keys.EnabledFilters] = enabled
            preferences[Keys.SelectedFilter] = filterId
        }
    }

    suspend fun setTimerSeconds(seconds: Int) {
        require(seconds in CameraSettings.TIMER_CHOICES) { "Unsupported timer: $seconds seconds" }
        dataStore.edit { it[Keys.TimerSeconds] = seconds }
    }

    private fun readSettings(preferences: Preferences): CameraSettings = CameraSettings(
        grainEnabled = preferences[Keys.GrainEnabled] ?: true,
        enabledFilterIds = preferences[Keys.EnabledFilters] ?: allFilterIds(),
        selectedFilterId = preferences[Keys.SelectedFilter] ?: FilmCatalog.default.id,
        timerSeconds = preferences[Keys.TimerSeconds] ?: 0,
    ).normalized()

    private fun allFilterIds(): LinkedHashSet<String> =
        FilmCatalog.profiles.mapTo(linkedSetOf()) { it.id }

    private object Keys {
        val GrainEnabled = booleanPreferencesKey("grain_enabled")
        val EnabledFilters = stringSetPreferencesKey("enabled_filter_ids")
        val SelectedFilter = stringPreferencesKey("selected_filter_id")
        val TimerSeconds = intPreferencesKey("timer_seconds")
    }
}
