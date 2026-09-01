package com.prestonhill.questgiver.feature.habits

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.repository.HabitRepository
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
import com.prestonhill.questgiver.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.prestonhill.questgiver.core.time.BoundaryTimer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import com.prestonhill.questgiver.core.time.AppDayCalculator

@RunWith(AndroidJUnit4::class)
class HabitViewModelTest {
    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: HabitViewModel
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var clock: TestClock
    private lateinit var timer: TestTimer

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        clock =
            TestClock(
                initialInstant =
                    Instant.parse("2026-08-23T17:00:00Z"),
                zone = ZoneId.of("America/Chicago"),
            )

        timer = TestTimer()

        settings =
            MutableStateFlow(AppSettings())

        database =
            Room.inMemoryDatabaseBuilder<QuestGiverDatabase>(
                context
            )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        repository = HabitRepository(database)

        val factory =
            HabitViewModelFactory(
                repository = repository,
                settings = settings,
                clock = clock,
                timer = timer,
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

    @Test
    fun boundaryRecalculatesWithoutChangingLogs() = runBlocking {
        val habitId =
            addHabit(
                createdAt =
                    timestamp(
                        date = LocalDate.of(2026, 8, 22),
                        time = LocalTime.NOON,
                    ),
            )

        val completionTime =
            timestamp(
                date = LocalDate.of(2026, 8, 23),
                time = LocalTime.of(1, 0),
            )

        database.habitDao().insertHabitLog(
            HabitLogEntity(
                habitId = habitId,
                completionTimestampMillis = completionTime,
                recordedTimestampMillis = completionTime,
                delta = 1,
            ),
        )

        val before =
            awaitState {
                it.habit(habitId)?.completionCountToday == 1
            }

        with(requireNotNull(before.habit(habitId))) {
            assertEquals(1, completionCountToday)
            assertEquals(1, scheduleCompletions)
            assertEquals(
                HabitDueStatus.COMPLETED,
                dueStatus,
            )
        }

        val logsBefore =
            repository.observeAllHabitLogs().first()

        settings.value =
            settings.value.copy(
                dayBoundary = LocalTime.of(4, 0),
            )

        val after =
            awaitState {
                it.habit(habitId)?.completionCountToday == 0
            }

        with(requireNotNull(after.habit(habitId))) {
            assertEquals(0, completionCountToday)
            assertEquals(0, scheduleCompletions)
            assertEquals(
                HabitDueStatus.DUE,
                dueStatus,
            )
        }

        val logsAfter =
            repository.observeAllHabitLogs().first()

        assertEquals(logsBefore, logsAfter)
    }

    @Test
    fun weekStartRecalculatesWithoutChangingLogs() = runBlocking {
        val habitId =
            addHabit(
                scheduleType =
                    HabitScheduleTypeDb.WEEKLY_TARGET,
                createdAt =
                    timestamp(
                        date = LocalDate.of(2026, 8, 16),
                        time = LocalTime.NOON,
                    ),
            )

        val completionTime =
            timestamp(
                date = LocalDate.of(2026, 8, 17),
                time = LocalTime.NOON,
            )

        database.habitDao().insertHabitLog(
            HabitLogEntity(
                habitId = habitId,
                completionTimestampMillis = completionTime,
                recordedTimestampMillis = completionTime,
                delta = 1,
            ),
        )

        val before =
            awaitState {
                it.habit(habitId)?.scheduleCompletions == 1
            }

        with(requireNotNull(before.habit(habitId))) {
            assertEquals(1, scheduleCompletions)
            assertEquals(
                HabitDueStatus.COMPLETED,
                dueStatus,
            )
        }

        val logsBefore =
            repository.observeAllHabitLogs().first()

        settings.value =
            settings.value.copy(
                weekStart = DayOfWeek.SUNDAY,
            )

        val after =
            awaitState {
                it.habit(habitId)?.scheduleCompletions == 0
            }

        with(requireNotNull(after.habit(habitId))) {
            assertEquals(0, scheduleCompletions)
            assertEquals(
                HabitDueStatus.DUE,
                dueStatus,
            )
        }

        val logsAfter =
            repository.observeAllHabitLogs().first()

        assertEquals(logsBefore, logsAfter)
    }

    @Test
    fun boundaryTimerRefreshesDay() = runBlocking {
        val habitId = addHabit()

        awaitState {
            it.hasHabit(habitId)
        }

        viewModel.onAction(
            HabitAction.AddCompletion(habitId),
        )

        awaitState {
            it.habit(habitId)
                ?.completionCountToday == 1
        }

        val logsBefore =
            repository.observeAllHabitLogs().first()

        val wait =
            withTimeout(5_000.milliseconds) {
                timer.next()
            }

        val calculator =
            AppDayCalculator(
                dayBoundary = LocalTime.MIDNIGHT,
                zoneId = clock.zone,
            )

        val nextBoundary =
            calculator
                .containing(clock.millis())
                .endTimestampMillis

        assertEquals(
            nextBoundary - clock.millis(),
            wait.milliseconds,
        )

        clock.setTime(nextBoundary + 1L)
        wait.resume.complete(Unit)

        val state =
            awaitState {
                it.habit(habitId)
                    ?.completionCountToday == 0
            }

        with(requireNotNull(state.habit(habitId))) {
            assertEquals(0, completionCountToday)
            assertEquals(0, scheduleCompletions)
            assertEquals(
                HabitDueStatus.DUE,
                dueStatus,
            )
        }

        val logsAfter =
            repository.observeAllHabitLogs().first()

        assertEquals(logsBefore, logsAfter)
    }

    private suspend fun addHabit(
        scheduleType: HabitScheduleTypeDb =
            HabitScheduleTypeDb.DAILY,
        createdAt: Long = clock.millis(),
    ): Long =
        repository.createHabit(
            HabitEntity(
                name = "Test habit",
                category = HabitCategoryDb.ANYTIME,
                displayOrder = 0,
                allowsMultipleCompletions = false,
                scheduleType = scheduleType,
                scheduleTarget = 1,
                createdAtEpochMillis = createdAt,
            ),
        )

    private fun timestamp(
        date: LocalDate,
        time: LocalTime,
    ): Long =
        date.atTime(time)
            .atZone(clock.zone)
            .toInstant()
            .toEpochMilli()

    private fun HabitScreenUiState.habit(
        habitId: Long,
    ): HabitRowUiState? =
        categories
            .asSequence()
            .flatMap { category ->
                category.habits.asSequence()
            }
            .firstOrNull { habit ->
                habit.id == habitId
            }

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
    class TestClock(
        initialInstant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        private var currentInstant = initialInstant

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock =
            TestClock(
                initialInstant = currentInstant,
                zone = zone,
            )

        override fun instant(): Instant =
            currentInstant

        fun setTime(milliseconds: Long) {
            currentInstant =
                Instant.ofEpochMilli(milliseconds)
        }
    }

    class TestTimer : BoundaryTimer {
        private val waits =
            Channel<TimerWait>(Channel.UNLIMITED)

        override suspend fun pause(milliseconds: Long) {
            val wait =
                TimerWait(
                    milliseconds = milliseconds,
                    resume = CompletableDeferred(),
                )

            waits.send(wait)
            wait.resume.await()
        }

        suspend fun next(): TimerWait =
            waits.receive()
    }

    data class TimerWait(
        val milliseconds: Long,
        val resume: CompletableDeferred<Unit>,
    )
}