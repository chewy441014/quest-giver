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
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.data.repository.FoodLogDraft
import com.prestonhill.questgiver.data.repository.NutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.repository.NutritionValuesInput
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
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

    private lateinit var nutritionRepository:
            NutritionRepository

    private lateinit var settings:
            MutableStateFlow<AppSettings>

    private lateinit var clock: Clock

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

        nutritionRepository =
            NutritionRepository(database)

        settings =
            MutableStateFlow(
                AppSettings()
            )

        clock =
            Clock.fixed(
                CURRENT_DATE
                    .atTime(12, 0)
                    .atZone(ZONE)
                    .toInstant(),
                ZONE,
            )

        val factory =
            HistoryViewModelFactory(
                repository = repository,
                nutritionRepository =
                    nutritionRepository,
                settings = settings,
                clock = clock,
            )

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
    fun nutritionHistoryUsesDefaultRanges(): Unit =
        runBlocking {
            val state =
                awaitState {
                    it.nutrition
                        .selectedRange != null &&
                            it.nutrition
                                .customRange != null
                }
                    .nutrition

            assertEquals(
                NutritionHistoryRangePreset
                    .THIRTY_DAYS,
                state.rangePreset,
            )

            assertEquals(
                NutritionHistoryDateRange(
                    startDate =
                        CURRENT_DATE
                            .minusDays(29),
                    endDate = CURRENT_DATE,
                ),
                state.selectedRange,
            )

            assertEquals(
                NutritionHistoryDateRange(
                    startDate =
                        LocalDate.of(
                            2026,
                            8,
                            1,
                        ),
                    endDate =
                        LocalDate.of(
                            2026,
                            8,
                            31,
                        ),
                ),
                state.customRange,
            )

            assertEquals(
                java.time.YearMonth.of(
                    2026,
                    9,
                ),
                state.calendarMonth,
            )
        }

    @Test
    fun nutritionRangeCanChange(): Unit =
        runBlocking {
            awaitState {
                it.nutrition
                    .selectedRange != null
            }

            viewModel.onAction(
                HistoryAction
                    .SelectNutritionRange(
                        NutritionHistoryRangePreset
                            .SEVEN_DAYS
                    )
            )

            val preset =
                awaitState {
                    it.nutrition.rangePreset ==
                            NutritionHistoryRangePreset
                                .SEVEN_DAYS
                }
                    .nutrition

            assertEquals(
                NutritionHistoryDateRange(
                    startDate =
                        CURRENT_DATE
                            .minusDays(6),
                    endDate = CURRENT_DATE,
                ),
                preset.selectedRange,
            )

            val customRange =
                NutritionHistoryDateRange(
                    startDate =
                        LocalDate.of(
                            2026,
                            8,
                            10,
                        ),
                    endDate =
                        LocalDate.of(
                            2026,
                            8,
                            20,
                        ),
                )

            viewModel.onAction(
                HistoryAction
                    .SetNutritionCustomRange(
                        customRange
                    )
            )

            val custom =
                awaitState {
                    it.nutrition.rangePreset ==
                            NutritionHistoryRangePreset
                                .CUSTOM &&
                            it.nutrition.selectedRange ==
                            customRange
                }
                    .nutrition

            assertEquals(
                customRange,
                custom.customRange,
            )
        }

    @Test
    fun nutritionCalendarMonthCanChange(): Unit =
        runBlocking {
            awaitState {
                it.nutrition.calendarMonth ==
                        java.time.YearMonth.of(
                            2026,
                            9,
                        )
            }

            viewModel.onAction(
                HistoryAction
                    .PreviousNutritionMonth
            )

            awaitState {
                it.nutrition.calendarMonth ==
                        java.time.YearMonth.of(
                            2026,
                            8,
                        )
            }

            viewModel.onAction(
                HistoryAction.NextNutritionMonth
            )

            awaitState {
                it.nutrition.calendarMonth ==
                        java.time.YearMonth.of(
                            2026,
                            9,
                        )
            }

            viewModel.onAction(
                HistoryAction.NextNutritionMonth
            )

            val clamped =
                awaitState {
                    it.nutrition.calendarMonth ==
                            java.time.YearMonth.of(
                                2026,
                                9,
                            )
                }

            assertEquals(
                java.time.YearMonth.of(
                    2026,
                    9,
                ),
                clamped.nutrition
                    .calendarMonth,
            )
        }

    @Test
    fun nutritionLogsUpdateHistory(): Unit =
        runBlocking {
            val itemId =
                addNutritionItem(
                    calories = 1_500.0,
                    protein = 40.0,
                )

            addNutritionLog(
                itemId = itemId,
                date = CURRENT_DATE,
            )

            val state =
                awaitState {
                    it.nutrition
                        .selectedDays
                        .any { day ->
                            day.date ==
                                    CURRENT_DATE &&
                                    day.hasLogs
                        }
                }
                    .nutrition

            val today =
                state.selectedDays.single {
                    it.date == CURRENT_DATE
                }

            assertEquals(
                1_500.0,
                today.calories,
                0.0,
            )

            assertEquals(
                40.0,
                today.proteinGrams,
                0.0,
            )

            assertTrue(today.calorieGoalMet)
            assertTrue(today.proteinGoalMet)

            assertEquals(
                1,
                state.currentMonthCalories
                    .metDays,
            )

            assertEquals(
                CURRENT_DATE.dayOfMonth,
                state.currentMonthCalories
                    .totalDays,
            )
        }

    @Test
    fun currentGoalsRecalculateHistory(): Unit =
        runBlocking {
            val itemId =
                addNutritionItem(
                    calories = 1_500.0,
                    protein = 40.0,
                )

            addNutritionLog(
                itemId = itemId,
                date = CURRENT_DATE,
            )

            awaitState {
                it.nutrition
                    .selectedDays
                    .any { day ->
                        day.date ==
                                CURRENT_DATE &&
                                day.calorieGoalMet &&
                                day.proteinGoalMet
                    }
            }

            settings.value =
                AppSettings(
                    calorieGoal = 1_000.0,
                    maximumCalorieGoal =
                        1_400.0,
                    proteinGoalGrams = 20.0,
                    maximumProteinGoalGrams =
                        35.0,
                )

            val outside =
                awaitState {
                    it.nutrition
                        .selectedDays
                        .any { day ->
                            day.date ==
                                    CURRENT_DATE &&
                                    !day.calorieGoalMet &&
                                    !day.proteinGoalMet
                        }
                }

            val today =
                outside.nutrition
                    .selectedDays
                    .single {
                        it.date ==
                                CURRENT_DATE
                    }

            assertFalse(today.calorieGoalMet)
            assertFalse(today.proteinGoalMet)
        }

    @Test
    fun nutritionCustomRangePickerChanges(): Unit =
        runBlocking {
            awaitState {
                it.nutrition.currentDate != null
            }

            viewModel.onAction(
                HistoryAction
                    .OpenNutritionCustomRange
            )

            awaitState {
                it.nutrition
                    .showCustomRangePicker
            }

            viewModel.onAction(
                HistoryAction
                    .DismissNutritionCustomRange
            )

            val dismissed =
                awaitState {
                    !it.nutrition
                        .showCustomRangePicker
                }

            assertFalse(
                dismissed.nutrition
                    .showCustomRangePicker
            )
        }

    @Test
    fun settingCustomRangeClosesPicker(): Unit =
        runBlocking {
            val range =
                NutritionHistoryDateRange(
                    startDate =
                        LocalDate.of(
                            2026,
                            8,
                            10,
                        ),
                    endDate =
                        LocalDate.of(
                            2026,
                            8,
                            20,
                        ),
                )

            viewModel.onAction(
                HistoryAction
                    .OpenNutritionCustomRange
            )

            awaitState {
                it.nutrition
                    .showCustomRangePicker
            }

            viewModel.onAction(
                HistoryAction
                    .SetNutritionCustomRange(
                        range
                    )
            )

            val state =
                awaitState {
                    !it.nutrition
                        .showCustomRangePicker &&
                            it.nutrition
                                .rangePreset ==
                            NutritionHistoryRangePreset
                                .CUSTOM &&
                            it.nutrition
                                .selectedRange ==
                            range
                }

            assertEquals(
                range,
                state.nutrition.customRange,
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

    private suspend fun addNutritionItem(
        calories: Double,
        protein: Double,
    ): Long =
        nutritionRepository.createItem(
            draft =
                NutritionItemDraft(
                    name =
                        "History food " +
                                System.nanoTime(),
                    nutrition =
                        NutritionValuesInput
                            .Per100Grams(
                                calories = calories,
                                proteinGrams =
                                    protein,
                            ),
                ),
            timestampMillis =
                clock.millis(),
        )

    private suspend fun addNutritionLog(
        itemId: Long,
        date: LocalDate,
    ): Long =
        requireNotNull(
            nutritionRepository.createLog(
                draft =
                    FoodLogDraft(
                        itemId = itemId,
                        consumedAtEpochMillis =
                            date.atTime(12, 0)
                                .atZone(ZONE)
                                .toInstant()
                                .toEpochMilli(),
                        weightGrams = 100.0,
                    ),
                timestampMillis =
                    clock.millis(),
            )
        )

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

        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                9,
                2,
            )

        val ZONE: ZoneId =
            ZoneId.of(
                "America/Chicago"
            )
    }
}