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
    fun correctionHidesLog(): Unit =
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
                            log.taskId == taskId
                        }
                }
                    .tasks
                    .logDays
                    .flatMap { it.logs }
                    .single()

            assertTrue(
                active.canChangeTaskCompletion
            )

            repository.correctCompletion(
                logId = active.id,
                recordedTimestampMillis =
                    COMPLETION_TIME + 1_000L,
            )

            val state =
                awaitState {
                    it.tasks.logDays
                        .flatMap { day ->
                            day.logs
                        }
                        .none { log ->
                            log.id == active.id
                        }
                }

            assertTrue(
                state.tasks.logDays
                    .flatMap { it.logs }
                    .none { it.id == active.id }
            )
        }

    @Test
    fun taskCompletionChanges(): Unit =
        runBlocking {
            val taskId = addTask()

            val initial =
                awaitState {
                    it.tasks.allTasks.any { task ->
                        task.id == taskId &&
                                task.completionEpochDay !=
                                null
                    }
                }

            val day =
                requireNotNull(
                    initial.tasks.allTasks
                        .single {
                            it.id == taskId
                        }
                        .completionEpochDay
                )

            viewModel.onAction(
                HistoryAction.SetTaskCompletion(
                    taskId = taskId,
                    scheduledEpochDay = day,
                    completed = true,
                )
            )

            val completed =
                awaitState {
                    it.tasks.allTasks
                        .single { task ->
                            task.id == taskId
                        }
                        .let { task ->
                            task.isCompleted &&
                                    !task.isChanging
                        }
                }

            assertTrue(
                completed.tasks.allTasks
                    .single { it.id == taskId }
                    .isCompleted
            )

            val positive =
                repository.observeLogs()
                    .first { logs ->
                        logs.any {
                            it.taskId == taskId &&
                                    it.delta == 1
                        }
                    }
                    .single {
                        it.taskId == taskId &&
                                it.delta == 1
                    }

            assertEquals(
                day,
                positive.scheduledEpochDay,
            )
        }

    @Test
    fun logUncheckRemovesHistory(): Unit =
        runBlocking {
            val taskId = addTask()

            val task =
                awaitState {
                    it.tasks.allTasks.any { row ->
                        row.id == taskId &&
                                row.completionEpochDay != null
                    }
                }
                    .tasks
                    .allTasks
                    .single { it.id == taskId }

            val day =
                requireNotNull(
                    task.completionEpochDay
                )

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = day,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val log =
                awaitState {
                    it.tasks.logDays
                        .flatMap { dayState ->
                            dayState.logs
                        }
                        .any {
                            it.taskId == taskId
                        }
                }
                    .tasks
                    .logDays
                    .flatMap { it.logs }
                    .single {
                        it.taskId == taskId
                    }

            viewModel.onAction(
                HistoryAction.InspectLog(log.id)
            )

            awaitState {
                it.tasks.inspectedLogId ==
                        log.id
            }

            viewModel.onAction(
                HistoryAction.SetTaskCompletion(
                    taskId = taskId,
                    scheduledEpochDay =
                        log.date.toEpochDay(),
                    completed = false,
                )
            )

            val state =
                awaitState {
                    it.tasks.inspectedLogId == null &&
                            it.tasks.logDays
                                .flatMap { dayState ->
                                    dayState.logs
                                }
                                .none {
                                    it.id == log.id
                                }
                }

            assertFalse(
                state.tasks.allTasks
                    .single { it.id == taskId }
                    .isCompleted
            )

            val storedLogs =
                repository.observeLogs()
                    .first { it.size == 2 }

            assertEquals(
                1,
                storedLogs.count {
                    it.delta == -1
                },
            )
        }

    @Test
    fun repeatedChangeIsIgnored(): Unit =
        runBlocking {
            val taskId = addTask()

            val task =
                awaitState {
                    it.tasks.allTasks.any { row ->
                        row.id == taskId &&
                                row.completionEpochDay != null
                    }
                }
                    .tasks
                    .allTasks
                    .single { it.id == taskId }

            val day =
                requireNotNull(
                    task.completionEpochDay
                )

            viewModel.onAction(
                HistoryAction.SetTaskCompletion(
                    taskId = taskId,
                    scheduledEpochDay = day,
                    completed = true,
                )
            )

            viewModel.onAction(
                HistoryAction.SetTaskCompletion(
                    taskId = taskId,
                    scheduledEpochDay = day,
                    completed = false,
                )
            )

            awaitState {
                it.tasks.allTasks
                    .single { row ->
                        row.id == taskId
                    }
                    .let { row ->
                        row.isCompleted &&
                                !row.isChanging
                    }
            }

            val logs =
                repository.observeLogs()
                    .first()

            assertEquals(1, logs.size)
            assertEquals(1, logs.single().delta)
        }

    @Test
    fun missingTaskShowsError(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction.SetTaskCompletion(
                    taskId = Long.MAX_VALUE,
                    scheduledEpochDay = DAY,
                    completed = true,
                )
            )

            val state =
                awaitState {
                    it.tasks.operationError != null
                }

            assertEquals(
                "Task completion could not be changed.",
                state.tasks.operationError,
            )
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
            assertFalse(log.canChangeTaskCompletion)
            assertTrue(log.canDelete)
        }

    @Test
    fun inspectionChanges(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val logId =
                repository.observeLogs()
                    .first { it.isNotEmpty() }
                    .single()
                    .id

            awaitState {
                it.tasks.logDays
                    .flatMap { day ->
                        day.logs
                    }
                    .any { it.id == logId }
            }

            viewModel.onAction(
                HistoryAction.InspectLog(logId)
            )

            val logState =
                awaitState {
                    it.tasks.inspectedLogId ==
                            logId
                }

            assertEquals(
                logId,
                logState.tasks.inspectedLogId,
            )

            viewModel.onAction(
                HistoryAction.DismissLog
            )

            awaitState {
                it.tasks.inspectedLogId == null
            }

            viewModel.onAction(
                HistoryAction.InspectTask(taskId)
            )

            val taskState =
                awaitState {
                    it.tasks.inspectedTaskId ==
                            taskId
                }

            assertEquals(
                taskId,
                taskState.tasks.inspectedTaskId,
            )

            assertEquals(
                null,
                taskState.tasks.inspectedLogId,
            )

            viewModel.onAction(
                HistoryAction.DismissTask
            )

            awaitState {
                it.tasks.inspectedTaskId == null
            }
        }

    @Test
    fun orphanHistoryDeletes(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val logId =
                repository.observeLogs()
                    .first { it.isNotEmpty() }
                    .single()
                    .id

            repository.deleteTask(
                taskId = taskId,
                deleteHistory = false,
            )

            awaitState {
                it.tasks.logDays
                    .flatMap { day ->
                        day.logs
                    }
                    .any { log ->
                        log.id == logId &&
                                log.canDelete
                    }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteLog(
                    logId
                )
            )

            awaitState {
                it.tasks.confirmation?.logId ==
                        logId
            }

            viewModel.onAction(
                HistoryAction.ConfirmDeleteLog
            )

            val state =
                awaitState {
                    it.tasks.confirmation == null &&
                            it.tasks.logDays.isEmpty()
                }

            assertTrue(state.tasks.logDays.isEmpty())

            assertTrue(
                repository.observeLogs()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun attachedHistoryCannotDelete(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val logId =
                repository.observeLogs()
                    .first { it.isNotEmpty() }
                    .single()
                    .id

            awaitState {
                it.tasks.logDays
                    .flatMap { day ->
                        day.logs
                    }
                    .any { it.id == logId }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteLog(
                    logId
                )
            )

            val state =
                awaitState {
                    it.tasks.operationError != null
                }

            assertEquals(
                "History cannot be deleted.",
                state.tasks.operationError,
            )

            assertEquals(
                null,
                state.tasks.confirmation,
            )
        }

    @Test
    fun failedHistoryDeleteStaysOpen(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            val logId =
                repository.observeLogs()
                    .first { it.isNotEmpty() }
                    .single()
                    .id

            repository.deleteTask(
                taskId = taskId,
                deleteHistory = false,
            )

            awaitState {
                it.tasks.logDays
                    .flatMap { day ->
                        day.logs
                    }
                    .any { it.id == logId }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteLog(
                    logId
                )
            )

            awaitState {
                it.tasks.confirmation?.logId ==
                        logId
            }

            repository.deleteHistory(logId)

            viewModel.onAction(
                HistoryAction.ConfirmDeleteLog
            )

            val confirmation =
                awaitState {
                    it.tasks.confirmation
                        ?.errorMessage != null
                }
                    .tasks
                    .confirmation

            assertEquals(
                "History could not be deleted.",
                confirmation?.errorMessage,
            )

            assertEquals(
                false,
                confirmation?.isDeleting,
            )
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