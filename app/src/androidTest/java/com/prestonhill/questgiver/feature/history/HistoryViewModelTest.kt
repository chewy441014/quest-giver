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
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import java.time.DayOfWeek
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.YearMonth
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
                YearMonth.of(
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
                        YearMonth.of(
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
                        YearMonth.of(
                            2026,
                            8,
                        )
            }

            viewModel.onAction(
                HistoryAction.NextNutritionMonth
            )

            awaitState {
                it.nutrition.calendarMonth ==
                        YearMonth.of(
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
                            YearMonth.of(
                                2026,
                                9,
                            )
                }

            assertEquals(
                YearMonth.of(
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
    fun nutritionStampFiltersAreMultiSelect(): Unit =
        runBlocking {
            awaitState {
                it.nutrition.selectedStampTypes ==
                        NutritionStampType
                            .entries
                            .toSet()
            }

            viewModel.onAction(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.CALORIES
                )
            )

            awaitState {
                it.nutrition.selectedStampTypes ==
                        setOf(
                            NutritionStampType.PROTEIN
                        )
            }

            viewModel.onAction(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.CALORIES
                )
            )

            awaitState {
                it.nutrition.selectedStampTypes ==
                        NutritionStampType
                            .entries
                            .toSet()
            }

            viewModel.onAction(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.PROTEIN
                )
            )

            awaitState {
                it.nutrition.selectedStampTypes ==
                        setOf(
                            NutritionStampType.CALORIES
                        )
            }

            /*
             * Attempting to remove the final filter
             * must leave it selected.
             */
            viewModel.onAction(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.CALORIES
                )
            )

            viewModel.onAction(
                HistoryAction.OpenNutritionCalendarDay(
                    CURRENT_DATE
                )
            )

            val state =
                awaitState {
                    it.nutrition
                        .selectedCalendarDate ==
                            CURRENT_DATE
                }

            assertEquals(
                setOf(
                    NutritionStampType.CALORIES
                ),
                state.nutrition
                    .selectedStampTypes,
            )
        }

    @Test
    fun selectAllNutritionStampsRestoresFilters(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.CALORIES
                )
            )

            awaitState {
                it.nutrition.selectedStampTypes ==
                        setOf(
                            NutritionStampType.PROTEIN
                        )
            }

            viewModel.onAction(
                HistoryAction
                    .SelectAllNutritionStamps
            )

            val state =
                awaitState {
                    it.nutrition.selectedStampTypes ==
                            NutritionStampType
                                .entries
                                .toSet()
                }

            assertEquals(
                NutritionStampType
                    .entries
                    .toSet(),
                state.nutrition
                    .selectedStampTypes,
            )
        }

    @Test
    fun changingNutritionMonthClosesDay(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction
                    .OpenNutritionCalendarDay(
                        CURRENT_DATE
                    )
            )

            awaitState {
                it.nutrition
                    .selectedCalendarDate ==
                        CURRENT_DATE
            }

            viewModel.onAction(
                HistoryAction
                    .PreviousNutritionMonth
            )

            val state =
                awaitState {
                    it.nutrition.calendarMonth ==
                            YearMonth.of(2026, 8) &&
                            it.nutrition
                                .selectedCalendarDate ==
                            null
                }

            assertEquals(
                null,
                state.nutrition
                    .selectedCalendarDate,
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

            assertEquals(
                1_000.0,
                outside.nutrition.calorieGoal,
                0.0,
            )

            assertEquals(
                1_400.0,
                requireNotNull(
                    outside.nutrition
                        .maximumCalorieGoal
                ),
                0.0,
            )

            assertEquals(
                20.0,
                outside.nutrition
                    .proteinGoalGrams,
                0.0,
            )

            assertEquals(
                35.0,
                requireNotNull(
                    outside.nutrition
                        .maximumProteinGoalGrams
                ),
                0.0,
            )

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
    fun taskCalendarShowsTaskAndCategoryStamps(): Unit =
        runBlocking {
            val taskId = addTask()

            completeTask(taskId)

            val calendar =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .availableFilters
                        .size == 2 &&
                            it.tasks
                                .stampCalendar
                                .days
                                .any { day ->
                                    day.date ==
                                            CURRENT_DATE &&
                                            day.stampKeys
                                                .size == 2
                                }
                }
                    .tasks
                    .stampCalendar

            assertEquals(
                YearMonth.from(CURRENT_DATE),
                calendar.month,
            )

            assertEquals(
                setOf(
                    "Recurring tasks",
                    "Categories",
                ),
                calendar.availableFilters
                    .map {
                        it.groupLabel
                    }
                    .toSet(),
            )

            assertEquals(
                calendar.availableFilters
                    .mapTo(linkedSetOf()) {
                        it.key
                    },
                calendar.selectedFilterKeys,
            )
        }

    @Test
    fun taskStampFiltersAreMultiSelect(): Unit =
        runBlocking {
            val taskId = addTask()

            completeTask(taskId)

            val initial =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .availableFilters
                        .size == 2
                }
                    .tasks
                    .stampCalendar

            val taskFilter =
                initial.availableFilters.single {
                    it.groupLabel ==
                            "Recurring tasks"
                }

            val categoryFilter =
                initial.availableFilters.single {
                    it.groupLabel ==
                            "Categories"
                }

            viewModel.onAction(
                HistoryAction
                    .ToggleTaskStampFilter(
                        taskFilter.key
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedFilterKeys ==
                        setOf(categoryFilter.key)
            }

            /*
             * The last selected filter cannot
             * be removed.
             */
            viewModel.onAction(
                HistoryAction
                    .ToggleTaskStampFilter(
                        categoryFilter.key
                    )
            )

            /*
             * Opening a day provides a subsequent
             * state change so the assertion cannot
             * accidentally inspect stale state.
             */
            viewModel.onAction(
                HistoryAction
                    .OpenTaskCalendarDay(
                        CURRENT_DATE
                    )
            )

            val protected =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .selectedDate ==
                            CURRENT_DATE
                }
                    .tasks
                    .stampCalendar

            assertEquals(
                setOf(categoryFilter.key),
                protected.selectedFilterKeys,
            )

            viewModel.onAction(
                HistoryAction
                    .SelectAllTaskStamps
            )

            val restored =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .selectedFilterKeys
                        .size == 2
                }
                    .tasks
                    .stampCalendar

            assertEquals(
                restored.availableFilters
                    .mapTo(linkedSetOf()) {
                        it.key
                    },
                restored.selectedFilterKeys,
            )
        }

    @Test
    fun taskCalendarMonthCanChange(): Unit =
        runBlocking {
            awaitState {
                it.tasks
                    .stampCalendar
                    .month ==
                        YearMonth.of(2026, 9)
            }

            viewModel.onAction(
                HistoryAction
                    .OpenTaskCalendarDay(
                        CURRENT_DATE
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedDate ==
                        CURRENT_DATE
            }

            viewModel.onAction(
                HistoryAction
                    .PreviousTaskCalendarMonth
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .month ==
                        YearMonth.of(2026, 8) &&
                        it.tasks
                            .stampCalendar
                            .selectedDate == null
            }

            viewModel.onAction(
                HistoryAction
                    .NextTaskCalendarMonth
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .month ==
                        YearMonth.of(2026, 9)
            }

            viewModel.onAction(
                HistoryAction
                    .NextTaskCalendarMonth
            )

            val clamped =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .month ==
                            YearMonth.of(2026, 9)
                }

            assertEquals(
                YearMonth.of(2026, 9),
                clamped.tasks
                    .stampCalendar
                    .month,
            )
        }

    @Test
    fun taskCalendarDayCanBeDismissed(): Unit =
        runBlocking {
            viewModel.onAction(
                HistoryAction
                    .OpenTaskCalendarDay(
                        CURRENT_DATE
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedDate ==
                        CURRENT_DATE
            }

            viewModel.onAction(
                HistoryAction
                    .DismissTaskCalendarDay
            )

            val dismissed =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .selectedDate == null
                }

            assertNull(
                dismissed.tasks
                    .stampCalendar
                    .selectedDate
            )
        }

    @Test
    fun taskCalendarUsesConfiguredWeekStart(): Unit =
        runBlocking {
            settings.value =
                AppSettings(
                    weekStart =
                        DayOfWeek.SUNDAY
                )

            val state =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .weekStart ==
                            DayOfWeek.SUNDAY
                }

            assertEquals(
                DayOfWeek.SUNDAY,
                state.tasks
                    .stampCalendar
                    .weekStart,
            )
        }

    @Test
    fun taskStampGroupsCanBeSelectedTogether(): Unit =
        runBlocking {
            val firstId =
                addTask(
                    name = "Planning",
                    category = "General",
                    displayOrder = 0,
                )

            val secondId =
                addTask(
                    name = "Exercise",
                    category = "Health",
                    displayOrder = 1,
                )

            completeTask(firstId)
            completeTask(secondId)

            val initial =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .availableFilters
                        .size == 4
                }
                    .tasks
                    .stampCalendar

            val recurringKeys =
                initial.availableFilters
                    .filter {
                        it.groupLabel ==
                                "Recurring tasks"
                    }
                    .mapTo(linkedSetOf()) {
                        it.key
                    }

            val categoryKeys =
                initial.availableFilters
                    .filter {
                        it.groupLabel ==
                                "Categories"
                    }
                    .mapTo(linkedSetOf()) {
                        it.key
                    }

            viewModel.onAction(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Recurring tasks",
                        selected = false,
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedFilterKeys ==
                        categoryKeys
            }

            viewModel.onAction(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Recurring tasks",
                        selected = true,
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedFilterKeys ==
                        recurringKeys +
                        categoryKeys
            }

            viewModel.onAction(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Categories",
                        selected = false,
                    )
            )

            awaitState {
                it.tasks
                    .stampCalendar
                    .selectedFilterKeys ==
                        recurringKeys
            }

            /*
             * The remaining group cannot be cleared.
             */
            viewModel.onAction(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Recurring tasks",
                        selected = false,
                    )
            )

            viewModel.onAction(
                HistoryAction
                    .OpenTaskCalendarDay(
                        CURRENT_DATE
                    )
            )

            val protected =
                awaitState {
                    it.tasks
                        .stampCalendar
                        .selectedDate ==
                            CURRENT_DATE
                }
                    .tasks
                    .stampCalendar

            assertEquals(
                recurringKeys,
                protected.selectedFilterKeys,
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

    private suspend fun addTask(
        name: String = "Test task",
        category: String? = "General",
        displayOrder: Int = 0,
    ): Long =
        repository.createTask(
            TaskEntity(
                name = name,
                category = category,
                displayOrder = displayOrder,
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                recurrenceStartEpochDay = DAY,
                createdAtEpochMillis =
                    1_000L + displayOrder,
            )
        )

    private suspend fun completeTask(
        taskId: Long,
        date: LocalDate = CURRENT_DATE,
    ) {
        assertEquals(
            TaskCompletionResult.SUCCESS,
            repository.complete(
                taskId = taskId,
                scheduledEpochDay =
                    date.toEpochDay(),
                completionTimestampMillis =
                    clock.millis(),
                recordedTimestampMillis =
                    clock.millis(),
            ),
        )
    }

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