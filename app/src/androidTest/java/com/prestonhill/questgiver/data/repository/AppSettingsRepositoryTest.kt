package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.doublePreferencesKey
import org.junit.Assert.assertNotNull
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryTest {
    private lateinit var dataStore:
            DataStore<Preferences>

    private lateinit var repository:
            AppSettingsRepository

    private lateinit var dataStoreScope:
            CoroutineScope

    private lateinit var testFile: File

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        testFile =
            File(
                context.cacheDir,
                "app_settings_test_" +
                        "${System.nanoTime()}.preferences_pb"
            )

        dataStoreScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO
            )

        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = {
                    testFile
                }
            )

        repository =
            AppSettingsRepository(dataStore)
    }

    @After
    fun close() {
        dataStoreScope.cancel()
        testFile.delete()
    }

    @Test
    fun nullMaximumsRemoveStoredMaximums(): Unit =
        runBlocking {
            repository.setNutritionGoals(
                calorieGoal = 1_500.0,
                maximumCalorieGoal = 2_200.0,
                proteinGoalGrams = 40.0,
                maximumProteinGoalGrams =
                    160.0,
            )

            repository.setNutritionGoals(
                calorieGoal = 1_500.0,
                maximumCalorieGoal = null,
                proteinGoalGrams = 40.0,
                maximumProteinGoalGrams =
                    null,
            )

            val settings =
                repository.settings.first {
                    it.maximumCalorieGoal ==
                            null &&
                            it.maximumProteinGoalGrams ==
                            null
                }

            assertNull(
                settings.maximumCalorieGoal
            )

            assertNull(
                settings.maximumProteinGoalGrams
            )
        }

    @Test
    fun defaultSettings() = runBlocking {
        val settings =
            repository.settings.first()

        assertEquals(
            LocalTime.MIDNIGHT,
            settings.dayBoundary
        )

        assertEquals(
            DayOfWeek.MONDAY,
            settings.weekStart
        )
        assertTrue(
            settings.daylightSavingEnabled
        )
        assertEquals(
            1_500.0,
            settings.calorieGoal,
            0.0,
        )

        assertEquals(
            40.0,
            settings.proteinGoalGrams,
            0.0,
        )

        assertNull(
            settings.maximumCalorieGoal
        )

        assertNull(
            settings.maximumProteinGoalGrams
        )

    }

    @Test
    fun savesNutritionGoals(): Unit =
        runBlocking {
            repository.setNutritionGoals(
                calorieGoal = 2_250.0,
                maximumCalorieGoal = 2_750.0,
                proteinGoalGrams = 125.0,
                maximumProteinGoalGrams =
                    175.0,
            )

            val settings =
                repository.settings.first {
                    it.calorieGoal == 2_250.0 &&
                            it.maximumCalorieGoal ==
                            2_750.0 &&
                            it.proteinGoalGrams ==
                            125.0 &&
                            it.maximumProteinGoalGrams ==
                            175.0
                }

            assertEquals(
                2_250.0,
                settings.calorieGoal,
                0.0,
            )

            assertEquals(
                2_750.0,
                settings.maximumCalorieGoal,
            )

            assertEquals(
                125.0,
                settings.proteinGoalGrams,
                0.0,
            )

            assertEquals(
                175.0,
                settings.maximumProteinGoalGrams,
            )
        }

    @Test
    fun invalidNutritionGoalsAreRejected(): Unit =
        runBlocking {
            repository.setNutritionGoals(
                calorieGoal = 2_000.0,
                maximumCalorieGoal = 2_500.0,
                proteinGoalGrams = 100.0,
                maximumProteinGoalGrams =
                    150.0,
            )

            val invalidGoals =
                listOf(
                    GoalValues(
                        calories = 399.0,
                        maximumCalories = null,
                        protein = 40.0,
                        maximumProtein = null,
                    ),
                    GoalValues(
                        calories = 1_500.0,
                        maximumCalories = null,
                        protein = 4.0,
                        maximumProtein = null,
                    ),
                    GoalValues(
                        calories = 1_500.0,
                        maximumCalories = 1_499.0,
                        protein = 40.0,
                        maximumProtein = null,
                    ),
                    GoalValues(
                        calories = 1_500.0,
                        maximumCalories = null,
                        protein = 40.0,
                        maximumProtein = 39.0,
                    ),
                    GoalValues(
                        calories = 1_500.5,
                        maximumCalories = null,
                        protein = 40.0,
                        maximumProtein = null,
                    ),
                    GoalValues(
                        calories = Double.NaN,
                        maximumCalories = null,
                        protein = 40.0,
                        maximumProtein = null,
                    ),
                )

            invalidGoals.forEach { goals ->
                val failure =
                    runCatching {
                        repository
                            .setNutritionGoals(
                                calorieGoal =
                                    goals.calories,
                                maximumCalorieGoal =
                                    goals
                                        .maximumCalories,
                                proteinGoalGrams =
                                    goals.protein,
                                maximumProteinGoalGrams =
                                    goals
                                        .maximumProtein,
                            )
                    }

                assertNotNull(
                    failure.exceptionOrNull()
                )
            }

            val unchanged =
                repository.settings.first()

            assertEquals(
                2_000.0,
                unchanged.calorieGoal,
                0.0,
            )

            assertEquals(
                2_500.0,
                unchanged.maximumCalorieGoal,
            )

            assertEquals(
                100.0,
                unchanged.proteinGoalGrams,
                0.0,
            )

            assertEquals(
                150.0,
                unchanged
                    .maximumProteinGoalGrams,
            )
        }

    @Test
    fun savesDaylightSaving() = runBlocking {
        repository.setDaylightSaving(false)

        val settings =
            repository.settings.first {
                !it.daylightSavingEnabled
            }

        assertFalse(
            settings.daylightSavingEnabled
        )
    }

    @Test
    fun savesDayBoundary() = runBlocking {
        val expected = LocalTime.of(4, 30)

        repository.setDayBoundary(expected)

        val settings =
            repository.settings.first {
                it.dayBoundary == expected
            }

        assertEquals(
            expected,
            settings.dayBoundary
        )
    }

    @Test
    fun savesWeekStart() = runBlocking {
        val expected = DayOfWeek.SUNDAY

        repository.setWeekStart(expected)

        val settings =
            repository.settings.first {
                it.weekStart == expected
            }

        assertEquals(
            expected,
            settings.weekStart
        )
    }

    @Test
    fun savesBothSettings() = runBlocking {
        val expectedBoundary =
            LocalTime.of(3, 15)

        val expectedWeekStart =
            DayOfWeek.SATURDAY

        repository.setDayBoundary(
            expectedBoundary
        )

        repository.setWeekStart(
            expectedWeekStart
        )

        val settings =
            repository.settings.first {
                it.dayBoundary == expectedBoundary &&
                        it.weekStart == expectedWeekStart
            }

        assertEquals(
            expectedBoundary,
            settings.dayBoundary
        )

        assertEquals(
            expectedWeekStart,
            settings.weekStart
        )
    }

    @Test
    fun invalidValuesUseDefaults() = runBlocking {
        dataStore.edit { preferences ->
            preferences[
                doublePreferencesKey(
                    "calorie_goal"
                )
            ] = -1.0

            preferences[
                doublePreferencesKey(
                    "protein_goal_grams"
                )
            ] = Double.NaN
        }

        val settings =
            repository.settings.first()

        assertEquals(
            LocalTime.MIDNIGHT,
            settings.dayBoundary
        )

        assertEquals(
            DayOfWeek.MONDAY,
            settings.weekStart
        )

        assertEquals(
            1_500.0,
            settings.calorieGoal,
            0.0,
        )

        assertEquals(
            40.0,
            settings.proteinGoalGrams,
            0.0,
        )
    }

    private data class GoalValues(
        val calories: Double,
        val maximumCalories: Double?,
        val protein: Double,
        val maximumProtein: Double?,
    )
}