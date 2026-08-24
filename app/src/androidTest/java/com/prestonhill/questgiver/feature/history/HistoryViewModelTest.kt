package com.prestonhill.questgiver.feature.history

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {
    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            TaskRepository

    private lateinit var viewModel:
            HistoryViewModel

    private lateinit var viewModelStore:
            ViewModelStore

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

        repository = TaskRepository(database)

        val factory =
            HistoryViewModelFactory(repository)

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory,
            )[HistoryViewModel::class.java]
    }

    @After
    fun close() {
        viewModelStore.clear()
        database.close()
    }

    @Test
    fun navigationChanges(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction.OpenTaskPage(
                    TaskHistoryPage.ALL_LOGS
                )
            )

            val logsState =
                awaitState {
                    it.tasks.page ==
                            TaskHistoryPage
                                .ALL_LOGS
                }

            assertEquals(
                TaskHistoryPage.ALL_LOGS,
                logsState.tasks.page,
            )

            viewModel.onAction(
                HistoryAction.SelectSection(
                    HistorySection.HABITS
                )
            )

            val habitsState =
                awaitState {
                    it.section ==
                            HistorySection.HABITS
                }

            assertEquals(
                TaskHistoryPage.DASHBOARD,
                habitsState.tasks.page,
            )
        }

    @Test
    fun taskAppears(): Unit =
        runBlocking {
            val taskId = addTask()

            val state =
                awaitState {
                    it.tasks.allTasks.any {
                            task ->
                        task.id == taskId
                    }
                }

            val task =
                state.tasks.allTasks.single {
                    it.id == taskId
                }

            assertEquals("Test task", task.name)
            assertEquals("General", task.category)
            assertEquals("Daily", task.schedule)
        }

    @Test
    fun correctionUpdatesLog(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val active =
                awaitState {
                    it.tasks.logDays
                        .flatMap { day ->
                            day.logs
                        }
                        .any { log ->
                            log.taskId == taskId &&
                                    !log.isCorrected
                        }
                }
                    .tasks
                    .logDays
                    .flatMap { it.logs }
                    .single()

            assertTrue(active.canCorrect)

            repository.correctCompletion(
                logId = active.id,
                recordedTimestampMillis =
                    COMPLETION_TIME + 1_000L,
            )

            val corrected =
                awaitState {
                    it.tasks.logDays
                        .flatMap { day ->
                            day.logs
                        }
                        .any { log ->
                            log.id == active.id &&
                                    log.isCorrected
                        }
                }
                    .tasks
                    .logDays
                    .flatMap { it.logs }
                    .single()

            assertTrue(corrected.isCorrected)
            assertFalse(corrected.canCorrect)
        }

    @Test
    fun deletionOrphansLog(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            awaitState {
                it.tasks.logDays
                    .flatMap { day ->
                        day.logs
                    }
                    .any { log ->
                        log.taskId == taskId
                    }
            }

            repository.deleteTask(
                taskId = taskId,
                deleteHistory = false,
            )

            val state =
                awaitState {
                    it.tasks.allTasks.none {
                            task ->
                        task.id == taskId
                    } &&
                            it.tasks.logDays
                                .flatMap { day ->
                                    day.logs
                                }
                                .any { log ->
                                    log.taskId == null
                                }
                }

            val log =
                state.tasks.logDays
                    .flatMap { it.logs }
                    .single()

            assertFalse(log.canOpenTask)
            assertFalse(log.canCorrect)
            assertTrue(log.canDelete)
        }

    private suspend fun addTask(): Long =
        repository.createTask(
            TaskEntity(
                name = "Test task",
                category = "General",
                displayOrder = 0,
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                recurrenceStartEpochDay = DAY,
                createdAtEpochMillis = 1_000L,
            )
        )

    private suspend fun awaitState(
        condition: (HistoryScreenUiState) ->
        Boolean,
    ): HistoryScreenUiState =
        withTimeout(5_000.milliseconds) {
            viewModel.uiState.first(condition)
        }

    private companion object {
        val DAY: Long =
            LocalDate.of(2026, 8, 24)
                .toEpochDay()

        const val COMPLETION_TIME =
            1_777_000_000_000L
    }
}