package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
                intPreferencesKey(
                    "day_boundary_minutes"
                )
            ] = -1

            preferences[
                intPreferencesKey(
                    "week_start"
                )
            ] = 99
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
    }
}