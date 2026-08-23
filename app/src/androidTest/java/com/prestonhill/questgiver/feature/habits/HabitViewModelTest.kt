package com.prestonhill.questgiver.feature.habits

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.repository.HabitRepository
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class HabitViewModelTest {
    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: HabitViewModel
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<QuestGiverDatabase>(
                context
            )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        repository = HabitRepository(database)

        val appDayCalculator =
            AppDayCalculator(
                dayBoundary = LocalTime.MIDNIGHT,
                zoneId = ZoneId.systemDefault()
            )

        val factory =
            HabitViewModelFactory(
                repository = repository,
                appDayCalculator = appDayCalculator,
                scheduleCalculator =
                    HabitScheduleCalculator(
                        appDayCalculator = appDayCalculator,
                        weekStart = DayOfWeek.MONDAY
                    )
            )

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory
            )[HabitViewModel::class.java]
    }

    @After
    fun close() {
        viewModelStore.clear()
        database.close()
    }

    @Test
    fun deleteRequestShowsConfirmation() = runBlocking {
        val habitId = addHabit()
        awaitState { it.hasHabit(habitId) }

        viewModel.onAction(
            HabitAction.RequestDeleteHabit(habitId)
        )

        val confirmation =
            awaitState {
                it.confirmation?.habitId == habitId
            }.confirmation

        assertNotNull(confirmation)
        assertEquals("Test habit", confirmation?.habitName)
        assertFalse(confirmation?.isDeleting ?: true)
    }

    @Test
    fun cancelKeepsHabit() = runBlocking {
        val habitId = addHabit()
        awaitState { it.hasHabit(habitId) }

        viewModel.onAction(
            HabitAction.RequestDeleteHabit(habitId)
        )

        awaitState {
            it.confirmation?.habitId == habitId
        }

        viewModel.onAction(
            HabitAction.DismissConfirmation
        )

        awaitState { it.confirmation == null }

        assertNotNull(repository.getHabit(habitId))
    }

    @Test
    fun doubleConfirmDeletesHabit() = runBlocking {
        val habitId = addHabit()
        awaitState { it.hasHabit(habitId) }

        viewModel.onAction(
            HabitAction.RequestDeleteHabit(habitId)
        )

        awaitState {
            it.confirmation?.habitId == habitId
        }

        viewModel.onAction(HabitAction.ConfirmDelete)
        viewModel.onAction(HabitAction.ConfirmDelete)

        awaitState {
            it.confirmation == null &&
                    !it.hasHabit(habitId)
        }

        assertNull(repository.getHabit(habitId))
    }

    @Test
    fun failedDeleteShowsError() = runBlocking {
        val habitId = addHabit()
        awaitState { it.hasHabit(habitId) }

        viewModel.onAction(
            HabitAction.RequestDeleteHabit(habitId)
        )

        awaitState {
            it.confirmation?.habitId == habitId
        }

        repository.deleteHabit(habitId)

        viewModel.onAction(HabitAction.ConfirmDelete)

        val confirmation =
            awaitState {
                it.confirmation?.errorMessage != null
            }.confirmation

        assertFalse(confirmation?.isDeleting ?: true)
        assertEquals(
            "Habit could not be deleted.",
            confirmation?.errorMessage
        )
    }

    @Test
    fun deleteLastArchiveClosesDialog() = runBlocking {
        val habitId = addHabit()
        repository.archiveHabit(habitId)

        awaitState {
            it.archivedHabits.any { habit ->
                habit.id == habitId
            }
        }

        viewModel.onAction(
            HabitAction.ShowArchivedHabits
        )

        awaitState { it.showArchivedHabits }

        viewModel.onAction(
            HabitAction.RequestDeleteHabit(habitId)
        )

        awaitState {
            it.confirmation?.habitId == habitId
        }

        viewModel.onAction(HabitAction.ConfirmDelete)

        val state =
            awaitState {
                it.confirmation == null &&
                        !it.showArchivedHabits &&
                        it.archivedHabits.none { habit ->
                            habit.id == habitId
                        }
            }

        assertFalse(state.showArchivedHabits)
    }

    @Test
    fun restoreLastArchiveClosesDialog() = runBlocking {
        val habitId = addHabit()
        repository.archiveHabit(habitId)

        awaitState {
            it.archivedHabits.any { habit ->
                habit.id == habitId
            }
        }

        viewModel.onAction(
            HabitAction.ShowArchivedHabits
        )

        awaitState { it.showArchivedHabits }

        viewModel.onAction(
            HabitAction.RestoreHabit(habitId)
        )

        val state =
            awaitState {
                !it.showArchivedHabits &&
                        it.archivedHabits.none { habit ->
                            habit.id == habitId
                        } &&
                        it.hasHabit(habitId)
            }

        assertTrue(state.hasHabit(habitId))
    }

    @Test
    fun archiveFailureShowsError() = runBlocking {
        viewModel.onAction(
            HabitAction.ArchiveHabit(MISSING_HABIT_ID)
        )

        val state =
            awaitState {
                it.operationError != null
            }

        assertEquals(
            "Habit could not be archived.",
            state.operationError
        )
    }

    @Test
    fun restoreFailureShowsError() = runBlocking {
        viewModel.onAction(
            HabitAction.RestoreHabit(MISSING_HABIT_ID)
        )

        val state =
            awaitState {
                it.operationError != null
            }

        assertEquals(
            "Habit could not be restored.",
            state.operationError
        )
    }

    @Test
    fun dismissClearsOperationError() = runBlocking {
        viewModel.onAction(
            HabitAction.ArchiveHabit(MISSING_HABIT_ID)
        )

        awaitState {
            it.operationError != null
        }

        viewModel.onAction(
            HabitAction.DismissOperationError
        )

        val state =
            awaitState {
                it.operationError == null
            }

        assertNull(state.operationError)
    }

    private suspend fun addHabit(): Long =
        repository.createHabit(
            HabitEntity(
                name = "Test habit",
                category = HabitCategoryDb.ANYTIME,
                displayOrder = 0,
                allowsMultipleCompletions = false,
                scheduleType = HabitScheduleTypeDb.DAILY,
                scheduleTarget = 1,
                createdAtEpochMillis =
                    System.currentTimeMillis()
            )
        )

    private suspend fun awaitState(
        condition: (HabitScreenUiState) -> Boolean
    ): HabitScreenUiState =
        withTimeout(5_000.milliseconds) {
            viewModel.uiState.first(condition)
        }

    private fun HabitScreenUiState.hasHabit(
        habitId: Long
    ): Boolean =
        categories.any { category ->
            category.habits.any { habit ->
                habit.id == habitId
            }
        }

    companion object {
        const val MISSING_HABIT_ID = 999L
    }
}