package com.prestonhill.questgiver.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.prestonhill.questgiver.core.settings.AppSettings
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                val boundaryMinutes =
                    preferences[DAY_BOUNDARY_MINUTES]
                        ?.takeIf {
                            it in MIN_MINUTES..MAX_MINUTES
                        }
                        ?: DEFAULT_BOUNDARY_MINUTES

                val weekStartValue =
                    preferences[WEEK_START]
                        ?.takeIf {
                            it in
                                    DayOfWeek.MONDAY.value..
                                    DayOfWeek.SUNDAY.value
                        }
                        ?: DEFAULT_WEEK_START.value

                AppSettings(
                    dayBoundary =
                        LocalTime.ofSecondOfDay(
                            boundaryMinutes * 60L
                        ),
                    weekStart =
                        DayOfWeek.of(weekStartValue),
                    daylightSavingEnabled =
                        preferences[DAYLIGHT_SAVING_ENABLED]
                            ?: DEFAULT_DAYLIGHT_SAVING_ENABLED,
                )
            }
    suspend fun setDaylightSaving(
        enabled: Boolean,
    ) {
        dataStore.edit { preferences ->
            preferences[DAYLIGHT_SAVING_ENABLED] =
                enabled
        }
    }
    suspend fun setDayBoundary(
        dayBoundary: LocalTime,
    ) {
        val minutes =
            dayBoundary.hour * 60 +
                    dayBoundary.minute

        dataStore.edit { preferences ->
            preferences[DAY_BOUNDARY_MINUTES] =
                minutes
        }
    }

    suspend fun setWeekStart(
        weekStart: DayOfWeek,
    ) {
        dataStore.edit { preferences ->
            preferences[WEEK_START] =
                weekStart.value
        }
    }

    private companion object {
        val DAY_BOUNDARY_MINUTES =
            intPreferencesKey(
                "day_boundary_minutes"
            )

        val WEEK_START =
            intPreferencesKey(
                "week_start"
            )

        const val MIN_MINUTES = 0
        const val MAX_MINUTES = 1_439
        const val DEFAULT_BOUNDARY_MINUTES = 0

        val DEFAULT_WEEK_START =
            DayOfWeek.MONDAY

        val DAYLIGHT_SAVING_ENABLED =
            booleanPreferencesKey(
                "daylight_saving_enabled"
            )

        const val DEFAULT_DAYLIGHT_SAVING_ENABLED = true
    }
}