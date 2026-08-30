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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

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
                    TaskHistoryPage.ALL_TASKS
                )
            )

            val logsState =
                awaitState {
                    it.tasks.page ==
                            TaskHistoryPage
                                .ALL_TASKS
                }

            assertEquals(
                TaskHistoryPage.ALL_TASKS,
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
    fun deleteRequestCanBeDismissed(): Unit =
        runBlocking {
            val taskId = addTask()

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis =
                        COMPLETION_TIME,
                )
            )

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId &&
                            task.isArchived
                }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteTask(
                    taskId
                )
            )

            val requested =
                awaitState {
                    it.tasks
                        .deleteConfirmation
                        ?.taskId == taskId
                }

            assertEquals(
                "Test task",
                requested.tasks
                    .deleteConfirmation
                    ?.taskName,
            )

            viewModel.onAction(
                HistoryAction.DismissDelete
            )

            awaitState {
                it.tasks.deleteConfirmation == null
            }

            assertNotNull(
                repository.getTask(taskId)
            )
        }

    @Test
    fun activeTaskCannotRequestDeletion(): Unit =
        runBlocking {
            val taskId = addTask()

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId
                }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteTask(
                    taskId
                )
            )

            assertNull(
                viewModel.uiState.value
                    .tasks.deleteConfirmation
            )

            assertNotNull(
                repository.getTask(taskId)
            )
        }

    @Test
    fun deleteRemovesArchivedTaskAndHistory(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = DAY,
                completionTimestampMillis =
                    COMPLETION_TIME,
            )

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis =
                        COMPLETION_TIME + 1L,
                )
            )

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId &&
                            task.isArchived
                }
            }

            viewModel.onAction(
                HistoryAction.InspectTask(taskId)
            )

            viewModel.onAction(
                HistoryAction.RequestDeleteTask(
                    taskId
                )
            )

            awaitState {
                it.tasks.deleteConfirmation != null
            }

            viewModel.onAction(
                HistoryAction.ConfirmDelete
            )

            val deleted =
                awaitState {
                    it.tasks.allTasks.none { task ->
                        task.id == taskId
                    } &&
                            it.tasks
                                .deleteConfirmation == null &&
                            it.tasks
                                .inspectedTaskId == null
                }

            assertNull(
                deleted.tasks.operationError
            )

            assertNull(
                repository.getTask(taskId)
            )

            assertTrue(
                repository.observeLogs()
                    .first()
                    .none { it.taskId == taskId }
            )
        }

    @Test
    fun failedDeleteKeepsConfirmationOpen(): Unit =
        runBlocking {
            val taskId = addTask()

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis =
                        COMPLETION_TIME,
                )
            )

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId &&
                            task.isArchived
                }
            }

            viewModel.onAction(
                HistoryAction.RequestDeleteTask(
                    taskId
                )
            )

            awaitState {
                it.tasks.deleteConfirmation != null
            }

            // Makes the repository archive guard reject
            // the pending permanent deletion.
            assertTrue(
                repository.restoreTask(taskId)
            )

            viewModel.onAction(
                HistoryAction.ConfirmDelete
            )

            val failed =
                awaitState {
                    it.tasks
                        .deleteConfirmation
                        ?.errorMessage ==
                            "Task could not be deleted."
                }

            assertEquals(
                taskId,
                failed.tasks
                    .deleteConfirmation
                    ?.taskId,
            )

            assertNotNull(
                repository.getTask(taskId)
            )
        }

    @Test
    fun archivedTaskAppearsDisabled(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.archiveTask(
                taskId = taskId,
                timestampMillis =
                    COMPLETION_TIME,
            )

            val state =
                awaitState {
                    it.tasks.allTasks.any { task ->
                        task.id == taskId &&
                                task.isArchived
                    }
                }

            val task =
                state.tasks.allTasks
                    .single { it.id == taskId }

            assertTrue(task.isArchived)

            assertFalse(
                task.canChangeCompletion
            )

            assertTrue(
                repository.observeTasks()
                    .first()
                    .none { it.id == taskId }
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
    fun taskUncheckRemovesHistory(): Unit =
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
                HistoryAction.SetTaskCompletion(
                    taskId = taskId,
                    scheduledEpochDay =
                        log.date.toEpochDay(),
                    completed = false,
                )
            )

            val state =
                awaitState {
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
    fun taskInspectionChanges(): Unit =
        runBlocking {
            val taskId = addTask()

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId
                }
            }

            viewModel.onAction(
                HistoryAction.InspectTask(taskId)
            )

            val inspected =
                awaitState {
                    it.tasks.inspectedTaskId ==
                            taskId
                }

            assertEquals(
                taskId,
                inspected.tasks.inspectedTaskId,
            )

            viewModel.onAction(
                HistoryAction.DismissTask
            )

            awaitState {
                it.tasks.inspectedTaskId == null
            }
        }

    @Test
    fun archivedToggleChanges(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction.ShowArchivedTasks(
                    true
                )
            )

            val archived =
                awaitState {
                    it.tasks.showArchivedTasks
                }

            assertTrue(
                archived.tasks.showArchivedTasks
            )

            viewModel.onAction(
                HistoryAction.ShowArchivedTasks(
                    false
                )
            )

            val active =
                awaitState {
                    !it.tasks.showArchivedTasks
                }

            assertFalse(
                active.tasks.showArchivedTasks
            )
        }

    @Test
    fun archiveMovesTask(): Unit =
        runBlocking {
            val taskId = addTask()

            awaitState {
                it.tasks.visibleTasks.any {
                        task -> task.id == taskId
                }
            }

            viewModel.onAction(
                HistoryAction.InspectTask(taskId)
            )

            awaitState {
                it.tasks.inspectedTaskId ==
                        taskId
            }

            viewModel.onAction(
                HistoryAction.ArchiveTask(
                    taskId
                )
            )

            awaitState {
                it.tasks.inspectedTaskId == null &&
                        it.tasks.allTasks.any { task ->
                            task.id == taskId &&
                                    task.isArchived
                        } &&
                        it.tasks.visibleTasks.none {
                                task -> task.id == taskId
                        }
            }

            viewModel.onAction(
                HistoryAction.ShowArchivedTasks(
                    true
                )
            )

            val archived =
                awaitState {
                    it.tasks.visibleTasks.any {
                            task -> task.id == taskId
                    }
                }

            assertTrue(
                archived.tasks.visibleTasks
                    .single()
                    .isArchived
            )
        }

    @Test
    fun restoreMovesTask(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.archiveTask(
                taskId = taskId,
                timestampMillis =
                    COMPLETION_TIME,
            )

            viewModel.onAction(
                HistoryAction.ShowArchivedTasks(
                    true
                )
            )

            awaitState {
                it.tasks.visibleTasks.any {
                        task -> task.id == taskId
                }
            }

            viewModel.onAction(
                HistoryAction.RestoreTask(
                    taskId
                )
            )

            awaitState {
                it.tasks.allTasks.any { task ->
                    task.id == taskId &&
                            !task.isArchived
                } &&
                        it.tasks.visibleTasks.none {
                                task -> task.id == taskId
                        }
            }

            viewModel.onAction(
                HistoryAction.ShowArchivedTasks(
                    false
                )
            )

            val active =
                awaitState {
                    it.tasks.visibleTasks.any {
                            task -> task.id == taskId
                    }
                }

            assertFalse(
                active.tasks.visibleTasks
                    .single()
                    .isArchived
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