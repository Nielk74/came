package com.nielk74.came.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

class UpdatePreferences(context: Context) {
    private val context = context.applicationContext

    val lastCheckMillis: Flow<Long> = this.context.updateDataStore.data.map { preferences ->
        preferences[LastCheckMillisKey] ?: 0L
    }

    suspend fun shouldCheckAutomatically(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isAutomaticCheckDue(lastCheckMillis.first(), nowMillis)

    suspend fun recordCheck(nowMillis: Long = System.currentTimeMillis()) {
        context.updateDataStore.edit { preferences ->
            preferences[LastCheckMillisKey] = nowMillis
        }
    }

    companion object {
        const val AutomaticCheckIntervalMillis = 24L * 60L * 60L * 1000L
        private val LastCheckMillisKey = longPreferencesKey("last_check_millis")

        internal fun isAutomaticCheckDue(lastCheckMillis: Long, nowMillis: Long): Boolean =
            lastCheckMillis <= 0L ||
                (nowMillis >= lastCheckMillis &&
                    nowMillis - lastCheckMillis >= AutomaticCheckIntervalMillis)
    }
}
