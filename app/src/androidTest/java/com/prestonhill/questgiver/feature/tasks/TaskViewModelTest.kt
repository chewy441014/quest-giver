package com.prestonhill.questgiver.feature.tasks

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.core.time.BoundaryTimer
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.TaskRepository
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import org.junit.Assert.assertFalse
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class TaskViewModelTest {
    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: TaskRepository
    private lateinit var settings:
            MutableStateFlow<AppSettings>

    private lateinit var clock: TestClock
    private lateinit var timer: TestTimer
    private lateinit var viewModel: TaskViewModel
    private lateinit var viewModelStore: ViewModelStore

    private val currentDate =
        LocalDate.of(2026, 8, 23)

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
        settings = MutableStateFlow(AppSettings())

        clock =
            TestClock(
                initialInstant =
                    Instant.parse(
                        "2026-08-23T17:00:00Z"
                    ),
                testZone = ZoneId.of("America/Chicago"),
            )

        timer = TestTimer()

        val factory =
            TaskViewModelFactory(
                repository = repository,
                settings = settings,
                clock = clock,
                timer = timer,
            )

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory,
            )[TaskViewModel::class.java]
    }

    @After
    fun close() {
        viewModelStore.clear()
        database.close()
    }

    @Test
    fun taskAppearsToday() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        val state =
            awaitState {
                it.today.any { task ->
                    task.id == taskId
                }
            }

        val row =
            state.today.single {
                it.id == taskId
            }

        assertTrue(row.canComplete)

        assertEquals(
            currentDate.toEpochDay(),
            row.completionEpochDay,
        )
    }

    @Test
    fun completionIsHiddenToday() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        awaitState {
            it.today.any { task ->
                task.id == taskId
            }
        }

        repository.complete(
            taskId = taskId,
            scheduledEpochDay =
                currentDate.toEpochDay(),
            completionTimestampMillis =
                clock.millis(),
            recordedTimestampMillis =
                clock.millis(),
        )

        val state =
            awaitState {
                it.today.none { task ->
                    task.id == taskId
                } &&
                        it.hasHiddenToday
            }

        assertTrue(
            state.upcoming.all { day ->
                day.tasks.none {
                    it.id == taskId
                }
            }
        )
    }

    @Test
    fun boundarySettingRecalculatesDate() =
        runBlocking {
            val taskId =
                addDailyTask(
                    startDate =
                        currentDate.minusDays(1)
                )

            awaitState {
                it.row(taskId)
                    ?.completionEpochDay ==
                        currentDate.toEpochDay()
            }

            settings.value =
                settings.value.copy(
                    dayBoundary =
                        LocalTime.of(13, 0)
                )

            val state =
                awaitState {
                    it.row(taskId)
                        ?.completionEpochDay ==
                            currentDate
                                .minusDays(1)
                                .toEpochDay()
                }

            assertEquals(
                currentDate
                    .minusDays(1)
                    .toEpochDay(),
                state.row(taskId)
                    ?.completionEpochDay,
            )
        }

    @Test
    fun timerRefreshesDate() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        awaitState {
            it.row(taskId)
                ?.completionEpochDay ==
                    currentDate.toEpochDay()
        }

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
                it.row(taskId)
                    ?.completionEpochDay ==
                        currentDate
                            .plusDays(1)
                            .toEpochDay()
            }

        assertEquals(
            currentDate
                .plusDays(1)
                .toEpochDay(),
            state.row(taskId)
                ?.completionEpochDay,
        )
    }

    @Test
    fun cleanupPreservesLogs() = runBlocking {
        val expiredDate =
            currentDate.minusDays(7)

        val recentDate =
            currentDate.minusDays(6)

        val expiredId =
            addOneTimeTask(
                name = "Expired",
                scheduledDate = expiredDate,
            )

        val recentId =
            addOneTimeTask(
                name = "Recent",
                scheduledDate = recentDate,
            )

        completeOneTime(
            taskId = expiredId,
            date = expiredDate,
        )

        completeOneTime(
            taskId = recentId,
            date = recentDate,
        )

        viewModel.refresh()

        withTimeout(5_000.milliseconds) {
            repository.observeTasks().first { tasks ->
                tasks.none { it.id == expiredId } &&
                        tasks.any {
                            it.id == recentId
                        }
            }
        }

        assertNull(
            repository.getTask(expiredId)
        )

        assertNotNull(
            repository.getTask(recentId)
        )

        val logs =
            repository.observeLogs().first {
                it.size == 2
            }

        assertNull(
            logs.single {
                it.taskNameSnapshot == "Expired"
            }.taskId
        )

        assertEquals(
            recentId,
            logs.single {
                it.taskNameSnapshot == "Recent"
            }.taskId,
        )
    }

    @Test
    fun actionCompletesTask() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        val row =
            awaitState {
                it.today.any { task ->
                    task.id == taskId
                }
            }.row(taskId)

        assertNotNull(row)

        viewModel.onAction(
            TaskAction.Inspect(taskId)
        )

        awaitState {
            it.inspectedTaskId == taskId
        }

        viewModel.onAction(
            TaskAction.Complete(
                taskId = taskId,
                completionEpochDay =
                    requireNotNull(row)
                        .completionEpochDay,
            )
        )

        val state =
            awaitState {
                it.inspectedTaskId == null &&
                        it.today.none { task ->
                            task.id == taskId
                        } &&
                        it.hasHiddenToday
            }

        assertNull(state.inspectedTaskId)

        assertEquals(
            1,
            repository.observeLogs()
                .first()
                .count { it.delta == 1 },
        )
    }

    @Test
    fun inspectionCanBeDismissed() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        awaitState {
            it.today.any { task ->
                task.id == taskId
            }
        }

        viewModel.onAction(
            TaskAction.Inspect(taskId)
        )

        awaitState {
            it.inspectedTaskId == taskId
        }

        viewModel.onAction(
            TaskAction.DismissDetails
        )

        val state =
            awaitState {
                it.inspectedTaskId == null
            }

        assertNull(state.inspectedTaskId)
    }

    @Test
    fun deleteRequestCanBeDismissed() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        awaitState {
            it.today.any { task ->
                task.id == taskId
            }
        }

        viewModel.onAction(
            TaskAction.RequestDelete(taskId)
        )

        val confirmation =
            awaitState {
                it.confirmation?.taskId == taskId
            }.confirmation

        assertEquals(
            "Daily task",
            confirmation?.taskName,
        )

        viewModel.onAction(
            TaskAction.DismissDelete
        )

        awaitState {
            it.confirmation == null
        }

        assertNotNull(
            repository.getTask(taskId)
        )
    }

    @Test
    fun deletionKeepsTaskHistory() = runBlocking {
        val taskId =
            addOneTimeTask(
                name = "Keep history",
                scheduledDate = currentDate,
            )

        completeOneTime(
            taskId = taskId,
            date = currentDate,
        )

        viewModel.onAction(
            TaskAction.RequestDelete(taskId)
        )

        awaitState {
            it.confirmation?.taskId == taskId
        }

        viewModel.onAction(
            TaskAction.DeleteTask
        )

        withTimeout(5_000.milliseconds) {
            repository.observeTasks().first { tasks ->
                tasks.none { task ->
                    task.id == taskId
                }
            }
        }

        awaitState {
            it.confirmation == null
        }

        val log =
            repository.observeLogs()
                .first()
                .single()

        assertNull(log.taskId)
        assertEquals(
            "Keep history",
            log.taskNameSnapshot,
        )
    }

    @Test
    fun deletionCanRemoveTaskHistory() = runBlocking {
        val taskId =
            addOneTimeTask(
                name = "Remove history",
                scheduledDate = currentDate,
            )

        completeOneTime(
            taskId = taskId,
            date = currentDate,
        )

        viewModel.onAction(
            TaskAction.RequestDelete(taskId)
        )

        awaitState {
            it.confirmation?.taskId == taskId
        }

        viewModel.onAction(
            TaskAction.DeleteTaskAndHistory
        )

        withTimeout(5_000.milliseconds) {
            repository.observeTasks().first { tasks ->
                tasks.none { task ->
                    task.id == taskId
                }
            }
        }

        awaitState {
            it.confirmation == null
        }

        assertTrue(
            repository.observeLogs()
                .first()
                .isEmpty()
        )
    }

    @Test
    fun repeatedDeletionIsIgnored() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        viewModel.onAction(
            TaskAction.RequestDelete(taskId)
        )

        awaitState {
            it.confirmation?.taskId == taskId
        }

        viewModel.onAction(
            TaskAction.DeleteTask
        )

        viewModel.onAction(
            TaskAction.DeleteTask
        )

        withTimeout(5_000.milliseconds) {
            repository.observeTasks().first { tasks ->
                tasks.none { task ->
                    task.id == taskId
                }
            }
        }

        val state =
            awaitState {
                it.confirmation == null
            }

        assertNull(state.operationError)
    }

    @Test
    fun failedDeletionStaysOpen() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        viewModel.onAction(
            TaskAction.RequestDelete(taskId)
        )

        awaitState {
            it.confirmation?.taskId == taskId
        }

        repository.deleteTask(
            taskId = taskId,
            deleteHistory = false,
        )

        viewModel.onAction(
            TaskAction.DeleteTask
        )

        val confirmation =
            awaitState {
                it.confirmation
                    ?.errorMessage != null
            }.confirmation

        assertEquals(
            "Task could not be deleted.",
            confirmation?.errorMessage,
        )

        assertEquals(
            false,
            confirmation?.isDeleting,
        )
    }

    @Test
    fun newEditorUsesCurrentDate() = runBlocking<Unit> {
        viewModel.onAction(TaskAction.Add)

        val editor =
            awaitState {
                it.editor != null
            }.editor

        assertNotNull(editor)

        assertEquals(
            TaskScheduleType.ONE_TIME,
            editor?.scheduleType,
        )

        assertEquals(
            currentDate,
            editor?.scheduledDate,
        )

        assertEquals(
            currentDate,
            editor?.recurrenceStartDate,
        )

        viewModel.onAction(
            TaskAction.DismissEditor
        )

        awaitState {
            it.editor == null
        }
    }

    @Test
    fun editorCreatesOneTask() = runBlocking {
        viewModel.onAction(TaskAction.Add)

        val editor =
            requireNotNull(
                awaitState {
                    it.editor != null
                }.editor
            )

        viewModel.onAction(
            TaskAction.UpdateEditor(
                editor.copy(
                    name = "New task",
                    category = "Personal",
                )
            )
        )

        // The second save must be ignored while
        // the first save is running.
        viewModel.onAction(TaskAction.Save)
        viewModel.onAction(TaskAction.Save)

        val state =
            awaitState {
                it.editor == null &&
                        it.today.any { task ->
                            task.name == "New task"
                        }
            }

        assertTrue(
            state.today.any {
                it.name == "New task"
            }
        )

        val tasks =
            repository.observeTasks().first {
                it.isNotEmpty()
            }

        assertEquals(1, tasks.size)
        assertEquals("New task", tasks.single().name)
        assertEquals(
            "Personal",
            tasks.single().category,
        )
    }

    @Test
    fun invalidEditorDoesNotSave() = runBlocking {
        viewModel.onAction(TaskAction.Add)

        val editor =
            requireNotNull(
                awaitState {
                    it.editor != null
                }.editor
            )

        assertFalse(editor.canSave)

        viewModel.onAction(TaskAction.Save)

        val state =
            awaitState {
                it.editor != null
            }

        assertFalse(
            requireNotNull(state.editor).isSaving
        )

        assertTrue(
            repository.observeTasks()
                .first()
                .isEmpty()
        )
    }

    @Test
    fun scheduleEditPreservesLogs() = runBlocking {
        val previousDate =
            currentDate.minusDays(1)

        val taskId =
            addDailyTask(
                startDate = previousDate
            )

        val completionTime =
            timestamp(
                date = previousDate,
                hour = 12,
            )

        repository.complete(
            taskId = taskId,
            scheduledEpochDay =
                previousDate.toEpochDay(),
            completionTimestampMillis =
                completionTime,
            recordedTimestampMillis =
                completionTime,
        )

        val logsBefore =
            repository.observeLogs().first {
                it.isNotEmpty()
            }

        viewModel.onAction(
            TaskAction.Edit(taskId)
        )

        val editor =
            requireNotNull(
                awaitState {
                    it.editor?.taskId == taskId
                }.editor
            )

        assertEquals(
            TaskScheduleType.DAILY,
            editor.scheduleType,
        )

        assertEquals(
            previousDate,
            editor.recurrenceStartDate,
        )

        viewModel.onAction(
            TaskAction.UpdateEditor(
                editor.copy(
                    name = "Changed task",
                    category = "Work",
                    scheduleType =
                        TaskScheduleType.INTERVAL,
                    recurrenceStartDate =
                        currentDate,
                    intervalDays = "4",
                    intervalBasis =
                        TaskIntervalBasis
                            .FROM_COMPLETION,
                    dueTime =
                        LocalTime.of(8, 30),
                    remainsVisibleAfterDue = true,
                )
            )
        )

        viewModel.onAction(TaskAction.Save)

        awaitState {
            it.editor == null &&
                    it.row(taskId)?.name ==
                    "Changed task"
        }

        val updated =
            requireNotNull(
                repository.getTask(taskId)
            )

        assertEquals(
            "Changed task",
            updated.name,
        )

        assertEquals("Work", updated.category)

        assertEquals(
            TaskScheduleTypeDb.INTERVAL,
            updated.scheduleType,
        )

        assertEquals(4, updated.intervalDays)

        assertEquals(
            TaskIntervalBasisDb.FROM_COMPLETION,
            updated.intervalBasis,
        )

        assertEquals(
            8 * 60 + 30,
            updated.dueMinuteOfDay,
        )

        assertTrue(
            updated.remainsVisibleAfterDue
        )

        val logsAfter =
            repository.observeLogs().first {
                it.isNotEmpty()
            }

        assertEquals(logsBefore, logsAfter)

        // Snapshot data remains unchanged.
        assertEquals(
            "Daily task",
            logsAfter.single()
                .taskNameSnapshot,
        )
    }

    @Test
    fun missingTaskShowsSaveError() = runBlocking {
        val taskId =
            addDailyTask(
                startDate = currentDate
            )

        viewModel.onAction(
            TaskAction.Edit(taskId)
        )

        val editor =
            requireNotNull(
                awaitState {
                    it.editor?.taskId == taskId
                }.editor
            )

        repository.deleteTask(
            taskId = taskId,
            deleteHistory = false,
        )

        viewModel.onAction(
            TaskAction.UpdateEditor(
                editor.copy(
                    name = "Changed task"
                )
            )
        )

        viewModel.onAction(TaskAction.Save)

        val failedEditor =
            requireNotNull(
                awaitState {
                    it.editor?.errorMessage != null
                }.editor
            )

        assertFalse(failedEditor.isSaving)

        assertEquals(
            "Task could not be saved.",
            failedEditor.errorMessage,
        )
    }

    @Test
    fun actionCorrectsTask(): Unit =
        runBlocking {
            val taskId =
                addDailyTask(
                    startDate = currentDate
                )

            val initial =
                requireNotNull(
                    awaitState {
                        it.today.any { task ->
                            task.id == taskId
                        }
                    }.row(taskId)
                )

            viewModel.onAction(
                TaskAction.SetCompletion(
                    taskId = taskId,
                    completionEpochDay =
                        initial.completionEpochDay,
                    completed = true,
                )
            )

            awaitState {
                it.hasHiddenToday &&
                        it.today.none { task ->
                            task.id == taskId
                        }
            }

            viewModel.onAction(
                TaskAction.ToggleHidden
            )

            val completed =
                requireNotNull(
                    awaitState {
                        it.today.any { task ->
                            task.id == taskId &&
                                    task.isCompleted
                        }
                    }.row(taskId)
                )

            assertTrue(completed.canComplete)

            viewModel.onAction(
                TaskAction.SetCompletion(
                    taskId = taskId,
                    completionEpochDay =
                        completed
                            .completionEpochDay,
                    completed = false,
                )
            )

            val corrected =
                requireNotNull(
                    awaitState {
                        it.today.any { task ->
                            task.id == taskId &&
                                    !task.isCompleted
                        }
                    }.row(taskId)
                )

            assertTrue(corrected.canComplete)

            val logs =
                repository.observeLogs()
                    .first { it.size == 2 }

            assertEquals(
                1,
                logs.count { it.delta == 1 },
            )

            assertEquals(
                1,
                logs.count { it.delta == -1 },
            )
        }

    @Test
    fun repeatedChangesAreIgnored(): Unit =
        runBlocking {
            val taskId =
                addDailyTask(
                    startDate = currentDate
                )

            val row =
                requireNotNull(
                    awaitState {
                        it.today.any { task ->
                            task.id == taskId
                        }
                    }.row(taskId)
                )

            val complete =
                TaskAction.SetCompletion(
                    taskId = taskId,
                    completionEpochDay =
                        row.completionEpochDay,
                    completed = true,
                )

            viewModel.onAction(complete)
            viewModel.onAction(complete)

            awaitState {
                it.hasHiddenToday
            }

            assertEquals(
                1,
                repository.observeLogs()
                    .first {
                            logs ->
                        logs.any {
                            it.delta == 1
                        }
                    }
                    .count { it.delta == 1 },
            )

            viewModel.onAction(
                TaskAction.ToggleHidden
            )

            val completed =
                requireNotNull(
                    awaitState {
                        it.today.any { task ->
                            task.id == taskId &&
                                    task.isCompleted
                        }
                    }.row(taskId)
                )

            val correct =
                TaskAction.SetCompletion(
                    taskId = taskId,
                    completionEpochDay =
                        completed
                            .completionEpochDay,
                    completed = false,
                )

            viewModel.onAction(correct)
            viewModel.onAction(correct)

            awaitState {
                it.today.any { task ->
                    task.id == taskId &&
                            !task.isCompleted
                }
            }

            val logs =
                repository.observeLogs()
                    .first { it.size == 2 }

            assertEquals(2, logs.size)
            assertEquals(
                1,
                logs.count { it.delta == -1 },
            )
        }
    private suspend fun addDailyTask(
        startDate: LocalDate,
    ): Long =
        repository.createTask(
            TaskEntity(
                name = "Daily task",
                category = "General",
                displayOrder = 0,
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                recurrenceStartEpochDay =
                    startDate.toEpochDay(),
                createdAtEpochMillis =
                    clock.millis(),
            )
        )

    private suspend fun addOneTimeTask(
        name: String,
        scheduledDate: LocalDate,
    ): Long =
        repository.createTask(
            TaskEntity(
                name = name,
                category = "General",
                displayOrder = 0,
                scheduleType =
                    TaskScheduleTypeDb.ONE_TIME,
                scheduledEpochDay =
                    scheduledDate.toEpochDay(),
                createdAtEpochMillis =
                    timestamp(
                        scheduledDate,
                        12,
                    ),
            )
        )

    private suspend fun completeOneTime(
        taskId: Long,
        date: LocalDate,
    ) {
        val timestamp =
            timestamp(
                date = date,
                hour = 12,
            )

        repository.complete(
            taskId = taskId,
            scheduledEpochDay =
                date.toEpochDay(),
            completionTimestampMillis =
                timestamp,
            recordedTimestampMillis =
                timestamp,
        )
    }

    private suspend fun awaitState(
        condition: (TaskScreenUiState) -> Boolean,
    ): TaskScreenUiState =
        withTimeout(5_000.milliseconds) {
            viewModel.uiState.first(condition)
        }

    private fun TaskScreenUiState.row(
        taskId: Long,
    ): TaskRowUiState? =
        today.firstOrNull {
            it.id == taskId
        }
            ?: upcoming
                .asSequence()
                .flatMap { day ->
                    day.tasks.asSequence()
                }
                .firstOrNull {
                    it.id == taskId
                }

    private fun timestamp(
        date: LocalDate,
        hour: Int,
    ): Long =
        date.atTime(hour, 0)
            .atZone(clock.zone)
            .toInstant()
            .toEpochMilli()

    private class TestClock(
        initialInstant: Instant,
        private val testZone: ZoneId,
    ) : Clock() {
        private var currentInstant =
            initialInstant

        override fun getZone(): ZoneId =
            testZone

        override fun withZone(
            zone: ZoneId,
        ): Clock =
            TestClock(
                initialInstant =
                    currentInstant,
                testZone = zone,
            )

        override fun instant(): Instant =
            currentInstant

        fun setTime(milliseconds: Long) {
            currentInstant =
                Instant.ofEpochMilli(milliseconds)
        }
    }

    private class TestTimer : BoundaryTimer {
        private val waits =
            Channel<TimerWait>(
                Channel.UNLIMITED
            )

        override suspend fun pause(
            milliseconds: Long,
        ) {
            val wait =
                TimerWait(
                    milliseconds = milliseconds,
                    resume =
                        CompletableDeferred(),
                )

            waits.send(wait)
            wait.resume.await()
        }

        suspend fun next(): TimerWait =
            waits.receive()
    }

    private data class TimerWait(
        val milliseconds: Long,
        val resume: CompletableDeferred<Unit>,
    )
}