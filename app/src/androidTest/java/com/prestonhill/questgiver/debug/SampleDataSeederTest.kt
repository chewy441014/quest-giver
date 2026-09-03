package com.prestonhill.questgiver.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import com.prestonhill.questgiver.data.repository.HabitRepository
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleDataSeederTest {
    private lateinit var database:
            QuestGiverDatabase

    private lateinit var dataStore:
            DataStore<Preferences>

    private lateinit var dataStoreScope:
            CoroutineScope

    private lateinit var dataStoreFile:
            File

    private lateinit var settingsRepository:
            AppSettingsRepository

    private lateinit var seeder:
            SampleDataSeeder

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<
                    QuestGiverDatabase
                    >(context)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(
                    Dispatchers.IO
                )
                .build()

        dataStoreFile =
            File(
                context.cacheDir,
                "sample_data_test_" +
                        "${System.nanoTime()}" +
                        ".preferences_pb",
            )

        dataStoreScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO
            )

        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = {
                    dataStoreFile
                },
            )

        settingsRepository =
            AppSettingsRepository(dataStore)

        seeder =
            SampleDataSeeder(
                database = database,
                settingsRepository =
                    settingsRepository,
                clock = FIXED_CLOCK,
            )
    }

    @After
    fun close() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun sampleDataIsCompleteAndRepeatable(): Unit =
        runBlocking {
            val first =
                seeder.replaceAll()

            assertSeedMatches(first)

            /*
             * Running the loader again must replace
             * the existing fixture, not append to it.
             */
            val second =
                seeder.replaceAll()

            assertEquals(first, second)

            assertSeedMatches(second)
        }

    private suspend fun assertSeedMatches(
        result: SampleDataResult,
    ) {
        val nutritionRepository =
            NutritionRepository(database)

        val taskRepository =
            TaskRepository(database)

        val habitRepository =
            HabitRepository(database)

        val items =
            nutritionRepository
                .observeAllItems()
                .first()

        val foodLogs =
            database
                .nutritionDao()
                .observeAllLogs()
                .first()

        val components =
            database
                .nutritionDao()
                .getAllComponents()

        val tasks =
            taskRepository
                .observeAllTasks()
                .first()

        val taskLogs =
            taskRepository
                .observeLogs()
                .first()

        val activeHabits =
            habitRepository
                .observeActiveHabits()
                .first()

        val archivedHabits =
            habitRepository
                .observeArchivedHabits()
                .first()

        val habits =
            activeHabits + archivedHabits

        val habitLogs =
            habitRepository
                .observeAllHabitLogs()
                .first()

        assertEquals(
            result.nutritionItems,
            items.size,
        )

        assertEquals(
            result.foodLogs,
            foodLogs.size,
        )

        assertEquals(
            result.tasks,
            tasks.size,
        )

        assertEquals(
            result.taskLogs,
            taskLogs.size,
        )

        assertEquals(
            result.habits,
            habits.size,
        )

        assertEquals(
            result.habitLogs,
            habitLogs.size,
        )

        // Preserve the intended fixture scale.
        assertTrue(items.size >= 10)
        assertTrue(foodLogs.size >= 350)
        assertTrue(taskLogs.size >= 600)
        assertTrue(habitLogs.size >= 900)

        // Nutrition includes versions, composition,
        // archiving, and over one year of history.
        assertTrue(
            items.groupBy { it.nameKey }
                .values
                .any { versions ->
                    versions.size >= 2
                }
        )

        assertTrue(components.isNotEmpty())

        assertTrue(
            items.any {
                it.archivedAtEpochMillis != null
            }
        )

        assertTrue(
            foodLogs.minOf {
                it.consumedAtEpochMillis
            } <= ONE_YEAR_AGO
        )

        // Every task schedule type is represented.
        TaskScheduleTypeDb.entries
            .forEach { type ->
                assertTrue(
                    tasks.any {
                        it.scheduleType == type
                    }
                )
            }

        assertTrue(
            tasks.any {
                it.archivedAtEpochMillis != null
            }
        )

        // Every habit schedule type, an archived
        // habit, multi-completion data, and correction
        // logs are represented.
        HabitScheduleTypeDb.entries
            .forEach { type ->
                assertTrue(
                    habits.any {
                        it.scheduleType == type
                    }
                )
            }

        assertTrue(
            habits.any {
                it.allowsMultipleCompletions
            }
        )

        assertTrue(archivedHabits.isNotEmpty())

        assertTrue(
            habitLogs.any {
                it.delta < 0
            }
        )

        assertTrue(
            habitLogs.minOf {
                it.completionTimestampMillis
            } <= ONE_YEAR_AGO
        )

        val settings =
            settingsRepository
                .settings
                .first()

        assertEquals(
            1_500.0,
            settings.calorieGoal,
            TOLERANCE,
        )

        assertEquals(
            2_300.0,
            requireNotNull(
                settings.maximumCalorieGoal
            ),
            TOLERANCE,
        )

        assertEquals(
            40.0,
            settings.proteinGoalGrams,
            TOLERANCE,
        )

        assertEquals(
            160.0,
            requireNotNull(
                settings.maximumProteinGoalGrams
            ),
            TOLERANCE,
        )
    }

    private companion object {
        val ZONE: ZoneId =
            ZoneId.of("America/Chicago")

        val FIXED_CLOCK: Clock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-31T17:00:00Z"
                ),
                ZONE,
            )

        val ONE_YEAR_AGO: Long =
            FIXED_CLOCK.millis() -
                    Duration.ofDays(365).toMillis()

        const val TOLERANCE = 0.0001
    }
}