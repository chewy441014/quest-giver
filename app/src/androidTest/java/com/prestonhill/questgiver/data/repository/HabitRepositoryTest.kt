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
    fun displaySectionsCanBeReordered() =
        runBlocking {
            val sectionId =
                repository.createDisplaySection(
                    "Training"
                )

            assertTrue(
                repository.moveDisplaySectionUp(
                    sectionId
                )
            )

            var sections =
                repository
                    .observeDisplaySections()
                    .first()

            assertEquals(
                listOf(
                    "Morning",
                    "Anytime",
                    "Before bed",
                    "Training",
                    "Uncategorized",
                ),
                sections.map { it.name },
            )

            assertTrue(
                repository.moveDisplaySectionDown(
                    sectionId
                )
            )

            sections =
                repository
                    .observeDisplaySections()
                    .first()

            assertEquals(
                listOf(
                    "Morning",
                    "Anytime",
                    "Before bed",
                    "Uncategorized",
                    "Training",
                ),
                sections.map { it.name },
            )

            assertEquals(
                sections.indices.toList(),
                sections.map { it.displayOrder },
            )
        }

    @Test
    fun displaySectionCannotMovePastBoundary() =
        runBlocking {
            assertFalse(
                repository.moveDisplaySectionUp(
                    DefaultHabitDisplaySections
                        .MORNING_ID
                )
            )

            assertFalse(
                repository.moveDisplaySectionDown(
                    DefaultHabitDisplaySections
                        .UNCATEGORIZED_ID
                )
            )

            assertFalse(
                repository.moveDisplaySectionUp(
                    "missing"
                )
            )
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
                    "Uncategorized",
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

            assertEquals(
                DisplaySectionDeleteResult.SUCCESS,
                repository.deleteDisplaySection(
                    sectionId
                ),
            )

            assertFalse(
                repository
                    .observeDisplaySections()
                    .first()
                    .any { it.id == sectionId }
            )
        }

    @Test
    fun deletingSectionMovesReferencedHabits() =
        runBlocking {
            val sectionId =
                repository.createDisplaySection(
                    "Training"
                )

            val activeId =
                addHabit(
                    name = "Active",
                    displaySectionId = sectionId,
                    displayOrder = 1,
                )

            val archivedId =
                addHabit(
                    name = "Archived",
                    displaySectionId = sectionId,
                    displayOrder = 0,
                )

            assertTrue(
                repository.archiveHabit(
                    archivedId
                )
            )

            assertEquals(
                DisplaySectionDeleteResult.SUCCESS,
                repository.deleteDisplaySection(
                    sectionId
                ),
            )

            val active =
                requireNotNull(
                    repository.getHabit(activeId)
                )

            val archived =
                requireNotNull(
                    repository.getHabit(archivedId)
                )

            assertEquals(
                DefaultHabitDisplaySections
                    .UNCATEGORIZED_ID,
                active.displaySectionId,
            )

            assertEquals(
                DefaultHabitDisplaySections
                    .UNCATEGORIZED_ID,
                archived.displaySectionId,
            )

            assertNull(
                active.archivedAtEpochMillis
            )

            assertTrue(
                archived.archivedAtEpochMillis != null
            )

            assertFalse(
                repository
                    .observeDisplaySections()
                    .first()
                    .any { it.id == sectionId }
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
    fun habitCanCreateDisplaySectionAtomically() =
        runBlocking {
            val habitId =
                addHabit(
                    name = "Lift",
                    newDisplaySectionName =
                        "  Training  ",
                )

            val habit =
                requireNotNull(
                    repository.getHabit(habitId)
                )

            val section =
                repository
                    .observeDisplaySections()
                    .first()
                    .single {
                        it.name == "Training"
                    }

            assertEquals(
                section.id,
                habit.displaySectionId,
            )

            assertEquals(
                0,
                habit.displayOrder,
            )
        }

    @Test
    fun habitUpdateCanCreateDisplaySection() =
        runBlocking {
            val habitId = addHabit()

            val existing =
                requireNotNull(
                    repository.getHabit(habitId)
                )

            assertTrue(
                repository.updateHabit(
                    habit =
                        existing.copy(
                            name = "Updated"
                        ),
                    newDisplaySectionName =
                        "Training",
                )
            )

            val updated =
                requireNotNull(
                    repository.getHabit(habitId)
                )

            val section =
                repository
                    .observeDisplaySections()
                    .first()
                    .single {
                        it.name == "Training"
                    }

            assertEquals(
                section.id,
                updated.displaySectionId,
            )

            assertEquals(0, updated.displayOrder)
            assertEquals("Updated", updated.name)
        }

    @Test
    fun invalidHabitDoesNotCreateDisplaySection() =
        runBlocking {
            val result =
                runCatching {
                    addHabit(
                        name = "   ",
                        newDisplaySectionName =
                            "Temporary",
                    )
                }

            assertTrue(
                result.exceptionOrNull()
                        is IllegalArgumentException
            )

            assertFalse(
                repository
                    .observeDisplaySections()
                    .first()
                    .any {
                        it.name == "Temporary"
                    }
            )

            assertTrue(
                repository
                    .observeAllHabits()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun missingHabitUpdateDoesNotCreateSection() =
        runBlocking {
            val existingId = addHabit()

            val missing =
                requireNotNull(
                    repository.getHabit(existingId)
                )
                    .copy(id = Long.MAX_VALUE)

            assertFalse(
                repository.updateHabit(
                    habit = missing,
                    newDisplaySectionName =
                        "Temporary",
                )
            )

            assertFalse(
                repository
                    .observeDisplaySections()
                    .first()
                    .any {
                        it.name == "Temporary"
                    }
            )
        }

    @Test
    fun deletionNormalizesSectionOrder() =
        runBlocking {
            val first =
                repository.createDisplaySection(
                    "First custom"
                )

            repository.createDisplaySection(
                "Second custom"
            )

            assertEquals(
                DisplaySectionDeleteResult.SUCCESS,
                repository.deleteDisplaySection(
                    first
                ),
            )

            val sections =
                repository
                    .observeDisplaySections()
                    .first()

            assertEquals(
                sections.indices.toList(),
                sections.map { it.displayOrder },
            )
        }

    @Test
    fun missingDisplaySectionCannotBeDeleted() =
        runBlocking {
            assertEquals(
                DisplaySectionDeleteResult.NOT_FOUND,
                repository.deleteDisplaySection(
                    "missing"
                ),
            )
        }

    @Test
    fun uncategorizedSectionIsProtected() =
        runBlocking {
            assertFalse(
                repository.renameDisplaySection(
                    sectionId =
                        DefaultHabitDisplaySections
                            .UNCATEGORIZED_ID,
                    name = "Other",
                )
            )

            assertEquals(
                DisplaySectionDeleteResult.PROTECTED,
                repository.deleteDisplaySection(
                    DefaultHabitDisplaySections
                        .UNCATEGORIZED_ID
                ),
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
        displayOrder: Int = 0,
        newDisplaySectionName: String? = null,
    ): Long =
        repository.createHabit(
            habit =
                HabitEntity(
                    name = name,
                    displaySectionId =
                        displaySectionId,
                    historyCategory =
                        historyCategory,
                    displayOrder = displayOrder,
                    allowsMultipleCompletions = true,
                    scheduleType =
                        HabitScheduleTypeDb.DAILY,
                    scheduleTarget = 1,
                    createdAtEpochMillis = TEST_TIME,
                ),
            newDisplaySectionName =
                newDisplaySectionName,
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