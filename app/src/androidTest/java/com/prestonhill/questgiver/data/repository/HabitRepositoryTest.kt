package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.dao.HabitDao
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.local.database.HABIT_DISPLAY_SECTION_CALLBACK
import com.prestonhill.questgiver.data.local.database.entity.DefaultHabitDisplaySections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitRepositoryTest {
    private lateinit var database: QuestGiverDatabase
    private lateinit var dao: HabitDao
    private lateinit var repository: HabitRepository

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<QuestGiverDatabase>(
                context
            )
                .addCallback(
                    HABIT_DISPLAY_SECTION_CALLBACK
                )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()


        dao = database.habitDao()
        repository = HabitRepository(database)
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun archiveKeepsLogs() = runBlocking {
        val habitId = addHabit()
        addLogs(habitId, 2)

        assertTrue(repository.archiveHabit(habitId))

        assertFalse(
            repository.observeActiveHabits()
                .first()
                .any { it.id == habitId }
        )

        assertTrue(
            repository.observeArchivedHabits()
                .first()
                .any { it.id == habitId }
        )

        assertEquals(2, logCount(habitId))
    }

    @Test
    fun restoreKeepsLogs() = runBlocking {
        val habitId = addHabit()
        addLogs(habitId, 2)

        repository.archiveHabit(habitId)
        assertTrue(repository.restoreHabit(habitId))

        assertTrue(
            repository.observeActiveHabits()
                .first()
                .any { it.id == habitId }
        )

        assertFalse(
            repository.observeArchivedHabits()
                .first()
                .any { it.id == habitId }
        )

        assertEquals(2, logCount(habitId))
    }

    @Test
    fun deleteEmptyHabit() = runBlocking {
        val habitId = addHabit()

        assertTrue(repository.deleteHabit(habitId))
        assertNull(repository.getHabit(habitId))
        assertEquals(0, logCount(habitId))
    }

    @Test
    fun deleteOneLog() = runBlocking {
        val habitId = addHabit()
        addLogs(habitId, 1)

        assertTrue(repository.deleteHabit(habitId))
        assertNull(repository.getHabit(habitId))
        assertEquals(0, logCount(habitId))
    }

    @Test
    fun deleteManyLogs() = runBlocking {
        val deletedId = addHabit("Deleted")
        val remainingId = addHabit("Remaining")

        addLogs(deletedId, 3)
        addLogs(remainingId, 1)

        assertTrue(repository.deleteHabit(deletedId))

        assertNull(repository.getHabit(deletedId))
        assertEquals(0, logCount(deletedId))

        assertTrue(repository.getHabit(remainingId) != null)
        assertEquals(1, logCount(remainingId))
    }

    @Test
    fun defaultDisplaySectionsAreAvailable() =
        runBlocking {
            val sections =
                repository
                    .observeDisplaySections()
                    .first()

            assertEquals(
                listOf(
                    "Morning",
                    "Anytime",
                    "Before bed",
                ),
                sections.map { it.name },
            )
        }

    @Test
    fun displaySectionCanBeRenamedAndDeleted() =
        runBlocking {
            val sectionId =
                repository.createDisplaySection(
                    "Training"
                )

            assertTrue(
                repository.renameDisplaySection(
                    sectionId = sectionId,
                    name = "Exercise",
                )
            )

            val renamed =
                repository
                    .observeDisplaySections()
                    .first()
                    .single {
                        it.id == sectionId
                    }

            assertEquals(
                "Exercise",
                renamed.name,
            )

            assertTrue(
                repository.deleteDisplaySection(
                    sectionId
                )
            )

            assertFalse(
                repository
                    .observeDisplaySections()
                    .first()
                    .any { it.id == sectionId }
            )
        }

    @Test
    fun referencedDisplaySectionCannotBeDeleted() =
        runBlocking {
            val sectionId =
                repository.createDisplaySection(
                    "Training"
                )

            val habitId =
                addHabit(
                    displaySectionId =
                        sectionId
                )

            assertFalse(
                repository.deleteDisplaySection(
                    sectionId
                )
            )

            repository.archiveHabit(habitId)

            // Archived habits still reference their
            // display section.
            assertFalse(
                repository.deleteDisplaySection(
                    sectionId
                )
            )

            repository.deleteHabit(habitId)

            assertTrue(
                repository.deleteDisplaySection(
                    sectionId
                )
            )
        }

    @Test
    fun duplicateDisplaySectionNameIsRejected() =
        runBlocking {
            repository.createDisplaySection(
                "Training"
            )

            val result =
                runCatching {
                    repository.createDisplaySection(
                        "  training  "
                    )
                }

            assertTrue(
                result.exceptionOrNull()
                        is IllegalArgumentException
            )
        }

    @Test
    fun historyCategoryIsNormalized() =
        runBlocking {
            val categorizedId =
                addHabit(
                    name = "Lift",
                    historyCategory = "  Gym  ",
                )

            val uncategorizedId =
                addHabit(
                    name = "Walk",
                    historyCategory = "   ",
                )

            assertEquals(
                "Gym",
                repository
                    .getHabit(categorizedId)
                    ?.historyCategory,
            )

            assertNull(
                repository
                    .getHabit(uncategorizedId)
                    ?.historyCategory
            )
        }

    @Test
    fun deleteArchivedHabit() = runBlocking {
        val habitId = addHabit()
        addLogs(habitId, 3)
        repository.archiveHabit(habitId)

        assertTrue(repository.deleteHabit(habitId))

        assertNull(repository.getHabit(habitId))
        assertEquals(0, logCount(habitId))

        assertFalse(
            repository.observeArchivedHabits()
                .first()
                .any { it.id == habitId }
        )
    }

    private suspend fun addHabit(
        name: String = "Test habit",
        displaySectionId: String =
            DefaultHabitDisplaySections.ANYTIME_ID,
        historyCategory: String? = null,
    ): Long =
        repository.createHabit(
            HabitEntity(
                name = name,
                displaySectionId =
                    displaySectionId,
                historyCategory =
                    historyCategory,
                displayOrder = 0,
                allowsMultipleCompletions = true,
                scheduleType =
                    HabitScheduleTypeDb.DAILY,
                scheduleTarget = 1,
                createdAtEpochMillis = TEST_TIME,
            )
        )

    private suspend fun addLogs(
        habitId: Long,
        count: Int
    ) {
        repeat(count) { index ->
            dao.insertHabitLog(
                HabitLogEntity(
                    habitId = habitId,
                    completionTimestampMillis =
                        TEST_TIME + index,
                    recordedTimestampMillis =
                        TEST_TIME + index,
                    delta = 1
                )
            )
        }
    }

    private suspend fun logCount(habitId: Long): Int =
        repository.observeAllHabitLogs()
            .first()
            .count { it.habitId == habitId }

    private companion object {
        const val TEST_TIME = 1_700_000_000_000L
    }
}