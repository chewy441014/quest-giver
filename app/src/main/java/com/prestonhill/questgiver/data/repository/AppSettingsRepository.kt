package com.prestonhill.questgiver.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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

                val calorieGoal =
                    preferences[CALORIE_GOAL]
                        ?.takeIf {
                            isValidMinimum(
                                value = it,
                                lowestAllowed =
                                    AppSettings
                                        .LOWEST_CALORIE_GOAL,
                            )
                        }
                        ?: AppSettings.DEFAULT_CALORIE_GOAL

                val maximumCalorieGoal =
                    preferences[MAXIMUM_CALORIE_GOAL]
                        ?.takeIf {
                            isValidMaximum(
                                value = it,
                                minimum = calorieGoal,
                            )
                        }

                val proteinGoalGrams =
                    preferences[PROTEIN_GOAL_GRAMS]
                        ?.takeIf {
                            isValidMinimum(
                                value = it,
                                lowestAllowed =
                                    AppSettings
                                        .LOWEST_PROTEIN_GOAL_GRAMS,
                            )
                        }
                        ?: AppSettings
                            .DEFAULT_PROTEIN_GOAL_GRAMS

                val maximumProteinGoalGrams =
                    preferences[
                        MAXIMUM_PROTEIN_GOAL_GRAMS
                    ]
                        ?.takeIf {
                            isValidMaximum(
                                value = it,
                                minimum = proteinGoalGrams,
                            )
                        }

                AppSettings(
                    dayBoundary =
                        LocalTime.ofSecondOfDay(
                            boundaryMinutes * 60L
                        ),
                    weekStart =
                        DayOfWeek.of(weekStartValue),
                    daylightSavingEnabled =
                        preferences[
                            DAYLIGHT_SAVING_ENABLED
                        ] ?: DEFAULT_DAYLIGHT_SAVING_ENABLED,
                    calorieGoal = calorieGoal,
                    proteinGoalGrams =
                        proteinGoalGrams,
                    maximumCalorieGoal = maximumCalorieGoal,
                    maximumProteinGoalGrams = maximumProteinGoalGrams
                )
            }

//    private fun isValidGoal(
//        value: Double,
//    ): Boolean =
//        value.isFinite() &&
//                value > 0.0 &&
//                value % 1.0 == 0.0

    private fun isWholeNumber(
        value: Double,
    ): Boolean =
        value.isFinite() &&
                value % 1.0 == 0.0

    private fun isValidMinimum(
        value: Double,
        lowestAllowed: Double,
    ): Boolean =
        isWholeNumber(value) &&
                value >= lowestAllowed

    private fun isValidMaximum(
        value: Double,
        minimum: Double,
    ): Boolean =
        isWholeNumber(value) &&
                value >= minimum

    suspend fun setNutritionGoals(
        calorieGoal: Double,
        maximumCalorieGoal: Double?,
        proteinGoalGrams: Double,
        maximumProteinGoalGrams: Double?,
    ) {
        require(
            isValidMinimum(
                calorieGoal,
                AppSettings.LOWEST_CALORIE_GOAL,
            )
        )

        require(
            maximumCalorieGoal == null ||
                    isValidMaximum(
                        maximumCalorieGoal,
                        calorieGoal,
                    )
        )

        require(
            isValidMinimum(
                proteinGoalGrams,
                AppSettings
                    .LOWEST_PROTEIN_GOAL_GRAMS,
            )
        )

        require(
            maximumProteinGoalGrams == null ||
                    isValidMaximum(
                        maximumProteinGoalGrams,
                        proteinGoalGrams,
                    )
        )

        dataStore.edit { preferences ->
            preferences[CALORIE_GOAL] =
                calorieGoal

            preferences[PROTEIN_GOAL_GRAMS] =
                proteinGoalGrams

            if (maximumCalorieGoal == null) {
                preferences.remove(
                    MAXIMUM_CALORIE_GOAL
                )
            } else {
                preferences[MAXIMUM_CALORIE_GOAL] =
                    maximumCalorieGoal
            }

            if (maximumProteinGoalGrams == null) {
                preferences.remove(
                    MAXIMUM_PROTEIN_GOAL_GRAMS
                )
            } else {
                preferences[
                    MAXIMUM_PROTEIN_GOAL_GRAMS
                ] = maximumProteinGoalGrams
            }
        }
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

        val MAXIMUM_CALORIE_GOAL =
            doublePreferencesKey(
                "maximum_calorie_goal"
            )

        val MAXIMUM_PROTEIN_GOAL_GRAMS =
            doublePreferencesKey(
                "maximum_protein_goal_grams"
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
        val CALORIE_GOAL =
            doublePreferencesKey(
                "calorie_goal"
            )

        val PROTEIN_GOAL_GRAMS =
            doublePreferencesKey(
                "protein_goal_grams"
            )

        const val DEFAULT_CALORIE_GOAL =
            1_500.0

        const val DEFAULT_PROTEIN_GOAL_GRAMS =
            40.0
    }
}