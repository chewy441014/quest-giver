package com.prestonhill.questgiver.feature.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import java.time.YearMonth
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun taskDashboardShowsStampCalendar(): Unit {
        showScreen(
            state = taskCalendarScreenState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_STAMP_CALENDAR
            )
            .assertIsDisplayed()
    }

    @Test
    fun taskStampGroupSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskCalendarScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags.taskStampGroup(
                        "Categories"
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampGroup(
                    "Categories"
                )
            )
            .assertIsOn()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Categories",
                        selected = false,
                    )
            ),
            actions,
        )
    }

    @Test
    fun partialTaskStampGroupCanSelectAll(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state =
                taskCalendarScreenState(
                    selectedKeys =
                        setOf(
                            TASK_FILTER_A,
                            CATEGORY_FILTER,
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags.taskStampGroup(
                        "Recurring tasks"
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampGroup(
                    "Recurring tasks"
                )
            )
            .assertIsOff()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .SetTaskStampGroupSelected(
                        groupLabel =
                            "Recurring tasks",
                        selected = true,
                    )
            ),
            actions,
        )
    }

    @Test
    fun taskStampFilterSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskCalendarScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampFilter(
                    TASK_FILTER_A
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .ToggleTaskStampFilter(
                        TASK_FILTER_A
                    )
            ),
            actions,
        )
    }

    @Test
    fun taskStampedDaySendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskCalendarScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampDay(
                    CURRENT_DATE
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .OpenTaskCalendarDay(
                        CURRENT_DATE
                    )
            ),
            actions,
        )
    }

    @Test
    fun taskDayDialogUsesSelectedFilters(): Unit {
        showScreen(
            state =
                taskCalendarScreenState(
                    selectedKeys =
                        setOf(TASK_FILTER_A),
                    selectedDate =
                        CURRENT_DATE,
                )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .TASK_STAMP_DAY_DIALOG
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.taskDayStamp(
                    TASK_FILTER_A
                )
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.taskDayStamp(
                    TASK_FILTER_B
                )
            )
            .assertDoesNotExist()

        composeRule
            .onNodeWithTag(
                HistoryTags.taskDayStamp(
                    CATEGORY_FILTER
                )
            )
            .assertDoesNotExist()
    }

    @Test
    fun taskDayDialogCanClose(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state =
                taskCalendarScreenState(
                    selectedDate =
                        CURRENT_DATE
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .TASK_STAMP_DAY_CLOSE
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .DismissTaskCalendarDay
            ),
            actions,
        )
    }

    @Test
    fun nutritionDashboardShowsStatistics(): Unit {
        showScreen(
            state = nutritionScreenState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALORIE_STATS
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("1500 kcal")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("1200 kcal")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("1800 kcal")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_PROTEIN_STATS
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_PROTEIN_STATS
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("50 g")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("40 g")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("60 g")
            .assertIsDisplayed()
    }

    @Test
    fun nutritionChartsShowGoalRanges(): Unit {
        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            calorieGoal =
                                1_500.0,
                            maximumCalorieGoal =
                                2_200.0,
                            proteinGoalGrams =
                                40.0,
                            maximumProteinGoalGrams =
                                160.0,
                        )
                )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALORIE_CHART
                )
            )

        composeRule
            .onNodeWithText(
                "Goal: 1500–2200 kcal"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_PROTEIN_CHART
                )
            )

        composeRule
            .onNodeWithText(
                "Goal: 40–160 g"
            )
            .assertIsDisplayed()
    }

    @Test
    fun nutritionRangeSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = nutritionScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionRange(
                    NutritionHistoryRangePreset
                        .THIRTY_DAYS
                )
            )
            .assertIsSelected()

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionRange(
                    NutritionHistoryRangePreset
                        .SEVEN_DAYS
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .SelectNutritionRange(
                        NutritionHistoryRangePreset
                            .SEVEN_DAYS
                    )
            ),
            actions,
        )
    }

    @Test
    fun customNutritionRangeOpensPicker(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = nutritionScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_RANGE_LIST
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags.nutritionRange(
                        NutritionHistoryRangePreset
                            .CUSTOM
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionRange(
                    NutritionHistoryRangePreset
                        .CUSTOM
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .OpenNutritionCustomRange
            ),
            actions,
        )
    }

    @Test
    fun nutritionRangePickerConfirmsRange(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        val nutrition =
            nutritionState().copy(
                showCustomRangePicker = true
            )

        showScreen(
            state =
                nutritionScreenState(
                    nutrition = nutrition
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_RANGE_CONFIRM
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .SetNutritionCustomRange(
                        requireNotNull(
                            nutrition.customRange
                        )
                    )
            ),
            actions,
        )
    }

    @Test
    fun nutritionRangePickerCanCancel(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            showCustomRangePicker =
                                true
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_RANGE_CANCEL
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .DismissNutritionCustomRange
            ),
            actions,
        )
    }

    @Test
    fun habitTabSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = HistoryScreenUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.tab(
                    HistorySection.HABITS
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.SelectSection(
                    HistorySection.HABITS
                )
            ),
            actions,
        )
    }

    @Test
    fun nutritionTabSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = HistoryScreenUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.tab(
                    HistorySection.NUTRITION
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.SelectSection(
                    HistorySection.NUTRITION
                )
            ),
            actions,
        )
    }

    @Test
    fun allTasksSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = HistoryScreenUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithText("View all tasks")
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.OpenTaskPage(
                    TaskHistoryPage.ALL_TASKS
                )
            ),
            actions,
        )
    }

    @Test
    fun taskRowIsVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                tasks = TaskHistoryUiState(
                    page =
                        TaskHistoryPage.ALL_TASKS,
                    allTasks = listOf(
                        HistoryTaskUiState(
                            id = TASK_ID,
                            name = "Test task",
                            category = "General",
                            schedule = "Daily",
                        )
                    ),
                )
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.task(TASK_ID)
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Test task")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Daily")
            .assertIsDisplayed()
    }

    @Test
    fun backSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = HistoryScreenUiState(
                tasks = TaskHistoryUiState(
                    page =
                        TaskHistoryPage.ALL_TASKS
                )
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithText("Back")
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.BackToDashboard
            ),
            actions,
        )
    }

    @Test
    fun emptyTasksIsVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                tasks = TaskHistoryUiState(
                    page =
                        TaskHistoryPage.ALL_TASKS
                )
            )
        )

        composeRule
            .onNodeWithText(
                "No active tasks."
            )
            .assertIsDisplayed()
    }

    @Test
    fun archivedTaskCanRequestDelete(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                canChangeCompletion = false,
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.deleteTask(
                    TASK_ID
                )
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.RequestDeleteTask(
                    TASK_ID
                )
            ),
            actions,
        )
    }

    @Test
    fun activeTaskHasNoDelete(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = false,
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.deleteTask(
                    TASK_ID
                )
            )
            .assertDoesNotExist()
    }

    @Test
    fun deleteConfirmationSendsConfirm(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                deleteConfirmation =
                    HistoryDeleteUiState(
                        taskId = TASK_ID,
                        taskName = "Test task",
                    ),
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithText(
                "Permanently delete " +
                        "\"Test task\" and all of " +
                        "its history? This cannot " +
                        "be undone."
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.CONFIRM_DELETE
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.ConfirmDelete
            ),
            actions,
        )
    }

    @Test
    fun deleteConfirmationSendsDismiss(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                deleteConfirmation =
                    HistoryDeleteUiState(
                        taskId = TASK_ID,
                        taskName = "Test task",
                    ),
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.CANCEL_DELETE
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.DismissDelete
            ),
            actions,
        )
    }

    @Test
    fun deletingDisablesConfirmation(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                isChanging = true,
                deleteConfirmation =
                    HistoryDeleteUiState(
                        taskId = TASK_ID,
                        taskName = "Test task",
                        isDeleting = true,
                    ),
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.CONFIRM_DELETE
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                HistoryTags.CANCEL_DELETE
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Deleting...")
            .assertIsDisplayed()
    }

    @Test
    fun deleteErrorIsVisible(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                deleteConfirmation =
                    HistoryDeleteUiState(
                        taskId = TASK_ID,
                        taskName = "Test task",
                        errorMessage =
                            "Task could not be deleted.",
                    ),
            )
        )

        composeRule
            .onNodeWithText(
                "Task could not be deleted."
            )
            .assertIsDisplayed()
    }

    @Test
    fun habitTemplateIsVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                section = HistorySection.HABITS
            )
        )

        composeRule
            .onNodeWithText(
                "No habit history to show yet."
            )
            .assertIsDisplayed()
    }

    @Test
    fun emptyNutritionRangeShowsMessage(): Unit {
        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            calorieStatistics =
                                NutritionHistoryMetricUiState(),
                            proteinStatistics =
                                NutritionHistoryMetricUiState(),
                        )
                )
        )

        val emptyNutrition =
            nutritionState().copy(
                selectedDays = emptyList(),
                calorieStatistics =
                    NutritionHistoryMetricUiState(),
                proteinStatistics =
                    NutritionHistoryMetricUiState(),
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALORIE_CHART
            )
            .assertDoesNotExist()

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_PROTEIN_CHART
            )
            .assertDoesNotExist()

        composeRule
            .onNodeWithText(
                "No nutrition was logged " +
                        "during this range."
            )
            .assertIsDisplayed()
    }

    @Test
    fun nutritionChartSupportsOneLoggedDay(): Unit {
        val day =
            NutritionHistoryDayUiState(
                date = CURRENT_DATE,
                calories = 1_500.0,
                proteinGrams = 40.0,
                hasLogs = true,
                calorieGoalMet = true,
                proteinGoalMet = true,
            )

        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            selectedDays =
                                listOf(day),
                            calorieStatistics =
                                NutritionHistoryMetricUiState(
                                    loggedDays = 1,
                                    average = 1_500.0,
                                    minimumNonZero =
                                        1_500.0,
                                    maximum = 1_500.0,
                                ),
                            proteinStatistics =
                                NutritionHistoryMetricUiState(
                                    loggedDays = 1,
                                    average = 40.0,
                                    minimumNonZero =
                                        40.0,
                                    maximum = 40.0,
                                ),
                        )
                )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALORIE_CHART
                )
            )

        composeRule
            .onNodeWithContentDescription(
                "Daily calories chart with " +
                        "1 logged days"
            )
            .assertExists()
    }

    @Test
    fun taskRowSendsInspect(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = null
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.task(TASK_ID)
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.InspectTask(TASK_ID)
            ),
            actions,
        )
    }
    @Test
    fun taskCanClose(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithText("Close")
            .performClick()

        assertEquals(
            listOf(HistoryAction.DismissTask),
            actions,
        )
    }

    @Test
    fun nutritionChartsAreDisplayed(): Unit {
        showScreen(
            state = nutritionScreenState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALORIE_CHART
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALORIE_CHART
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription(
                "Daily calories chart with " +
                        "2 logged days"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Goal: 1500+ kcal"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_PROTEIN_CHART
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_PROTEIN_CHART
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription(
                "Daily protein chart with " +
                        "2 logged days"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Goal: 40+ g"
            )
            .assertIsDisplayed()
    }

    @Test
    fun taskCheckSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    TASK_ID
                )
            )
            .assertIsOff()
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.SetTaskCompletion(
                    taskId = TASK_ID,
                    scheduledEpochDay =
                        TASK_DAY,
                    completed = true,
                )
            ),
            actions,
        )
    }

    @Test
    fun taskUncheckSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isCompleted = true,
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    TASK_ID
                )
            )
            .assertIsOn()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.SetTaskCompletion(
                    taskId = TASK_ID,
                    scheduledEpochDay =
                        TASK_DAY,
                    completed = false,
                )
            ),
            actions,
        )
    }

    @Test
    fun unavailableTaskCheckIsDisabled(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                canChangeCompletion = false,
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    TASK_ID
                )
            )
            .assertIsNotEnabled()
    }

    @Test
    fun changingTaskCheckIsDisabled(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isChanging = true,
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    TASK_ID
                )
            )
            .assertIsNotEnabled()
    }

    @Test
    fun taskHistoryPlaceholderIsDisabled(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .TASK_HISTORY_PLACEHOLDER
            )
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun archivedToggleSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = null
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.ARCHIVED_TOGGLE
            )
            .assertIsOff()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.ShowArchivedTasks(
                    true
                )
            ),
            actions,
        )
    }

    @Test
    fun activeTaskCanArchive(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.archiveTask(
                    TASK_ID
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.ArchiveTask(
                    TASK_ID
                )
            ),
            actions,
        )
    }

    @Test
    fun nutritionGoalProgressIsDisplayed(): Unit {
        showScreen(
            state = nutritionScreenState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_GOAL_PROGRESS
                )
            )

        composeRule
            .onNodeWithText("1/2 · 50%")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("10/31 · 32%")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("2/2 · 100%")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("20/31 · 65%")
            .assertIsDisplayed()
    }

    @Test
    fun previousNutritionMonthSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = nutritionScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALENDAR_PREVIOUS
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .PreviousNutritionMonth
            ),
            actions,
        )
    }

    @Test
    fun currentNutritionMonthDisablesNext(): Unit {
        showScreen(
            state = nutritionScreenState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALENDAR_NEXT
            )
            .assertIsNotEnabled()
    }

    @Test
    fun earlierNutritionMonthCanMoveNext(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            calendarMonth =
                                YearMonth.from(
                                    CURRENT_DATE
                                ).minusMonths(1)
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .NUTRITION_CALENDAR_NEXT
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.NextNutritionMonth
            ),
            actions,
        )
    }

    @Test
    fun nutritionStampFiltersSendActions(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = nutritionScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionStampFilter(
                    NutritionStampType.CALORIES
                )
            )
            .assertIsSelected()
            .performClick()

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_STAMP_ALL
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.ToggleNutritionStamp(
                    NutritionStampType.CALORIES
                ),
                HistoryAction
                    .SelectAllNutritionStamps,
            ),
            actions,
        )
    }

    @Test
    fun stampedNutritionDaySendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = nutritionScreenState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .nutritionCalendarDay(
                        CURRENT_DATE
                    )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .OpenNutritionCalendarDay(
                        CURRENT_DATE
                    )
            ),
            actions,
        )
    }

    @Test
    fun nutritionDayDialogUsesSelectedFilters(): Unit {
        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            selectedStampTypes =
                                setOf(
                                    NutritionStampType
                                        .CALORIES
                                ),
                            selectedCalendarDate =
                                CURRENT_DATE,
                        )
                )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DAY_DIALOG
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionDayStamp(
                    NutritionStampType.CALORIES
                )
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Calories goal met"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.nutritionDayStamp(
                    NutritionStampType.PROTEIN
                )
            )
            .assertDoesNotExist()
    }

    @Test
    fun nutritionDayDialogCanClose(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state =
                nutritionScreenState(
                    nutrition =
                        nutritionState().copy(
                            selectedCalendarDate =
                                CURRENT_DATE
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.NUTRITION_DAY_CLOSE
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction
                    .DismissNutritionCalendarDay
            ),
            actions,
        )
    }

    @Test
    fun archivedTaskCanRestore(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                canChangeCompletion = false,
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.restoreTask(
                    TASK_ID
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.RestoreTask(
                    TASK_ID
                )
            ),
            actions,
        )
    }

    @Test
    fun archivedTaskHasNoCheckbox(): Unit {
        showScreen(
            state = taskState(
                inspectedTaskId = TASK_ID,
                isArchived = true,
                canChangeCompletion = false,
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    TASK_ID
                )
            )
            .assertDoesNotExist()
    }

    private fun nutritionScreenState(
        nutrition:
        NutritionHistoryUiState =
            nutritionState(),
    ): HistoryScreenUiState =
        HistoryScreenUiState(
            section =
                HistorySection.NUTRITION,
            nutrition = nutrition,
        )

    private fun taskCalendarScreenState(
        selectedKeys: Set<String> =
            setOf(
                TASK_FILTER_A,
                TASK_FILTER_B,
                CATEGORY_FILTER,
            ),
        selectedDate: LocalDate? = null,
    ): HistoryScreenUiState =
        HistoryScreenUiState(
            section = HistorySection.TASKS,
            tasks =
                TaskHistoryUiState(
                    stampCalendar =
                        HistoryStampCalendarUiState(
                            month =
                                YearMonth.from(
                                    CURRENT_DATE
                                ),
                            currentDate =
                                CURRENT_DATE,
                            availableFilters =
                                listOf(
                                    HistoryStampFilterUiState(
                                        key =
                                            TASK_FILTER_A,
                                        label =
                                            "Morning planning",
                                        groupLabel =
                                            "Recurring tasks",
                                        colors =
                                            HistoryStampColorsUiState(
                                                left = 0,
                                                middle = 1,
                                                right = 2,
                                            ),
                                    ),
                                    HistoryStampFilterUiState(
                                        key =
                                            TASK_FILTER_B,
                                        label =
                                            "Exercise",
                                        groupLabel =
                                            "Recurring tasks",
                                        colors =
                                            HistoryStampColorsUiState(
                                                left = 3,
                                                middle = 4,
                                                right = 5,
                                            ),
                                    ),
                                    HistoryStampFilterUiState(
                                        key =
                                            CATEGORY_FILTER,
                                        label = "Health",
                                        groupLabel =
                                            "Categories",
                                        colors =
                                            HistoryStampColorsUiState(
                                                left = 6,
                                                middle = 7,
                                                right = 8,
                                            ),
                                    ),
                                ),
                            selectedFilterKeys =
                                selectedKeys,
                            days =
                                listOf(
                                    HistoryStampCalendarDayUiState(
                                        date =
                                            CURRENT_DATE,
                                        stampKeys =
                                            listOf(
                                                TASK_FILTER_A,
                                                TASK_FILTER_B,
                                                CATEGORY_FILTER,
                                            ),
                                    )
                                ),
                            selectedDate =
                                selectedDate,
                        )
                ),
        )

    private fun nutritionState():
            NutritionHistoryUiState =
        NutritionHistoryUiState(
            rangePreset =
                NutritionHistoryRangePreset
                    .THIRTY_DAYS,
            selectedRange =
                NutritionHistoryDateRange(
                    startDate =
                        CURRENT_DATE
                            .minusDays(29),
                    endDate =
                        CURRENT_DATE,
                ),
            customRange =
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
            currentDate = CURRENT_DATE,
            calorieStatistics =
                NutritionHistoryMetricUiState(
                    loggedDays = 2,
                    average = 1_500.0,
                    minimumNonZero =
                        1_200.0,
                    maximum = 1_800.0,
                ),
            proteinStatistics =
                NutritionHistoryMetricUiState(
                    loggedDays = 2,
                    average = 50.0,
                    minimumNonZero = 40.0,
                    maximum = 60.0,
                ),
            calendarMonth =
                YearMonth.from(
                    CURRENT_DATE
                ),
            selectedDays =
                listOf(
                    NutritionHistoryDayUiState(
                        date =
                            CURRENT_DATE.minusDays(2),
                        calories = 1_200.0,
                        proteinGrams = 40.0,
                        hasLogs = true,
                        calorieGoalMet = false,
                        proteinGoalMet = true,
                    ),
                    NutritionHistoryDayUiState(
                        date =
                            CURRENT_DATE.minusDays(1),
                        calories = 0.0,
                        proteinGrams = 0.0,
                        hasLogs = false,
                        calorieGoalMet = false,
                        proteinGoalMet = false,
                    ),
                    NutritionHistoryDayUiState(
                        date = CURRENT_DATE,
                        calories = 1_800.0,
                        proteinGrams = 60.0,
                        hasLogs = true,
                        calorieGoalMet = true,
                        proteinGoalMet = true,
                    ),
                ),
            currentMonthCalories =
                NutritionGoalCompletionUiState(
                    metDays = 1,
                    totalDays = 2,
                    progress = 0.5f,
                ),

            customRangeCalories =
                NutritionGoalCompletionUiState(
                    metDays = 10,
                    totalDays = 31,
                    progress = 10f / 31f,
                ),

            currentMonthProtein =
                NutritionGoalCompletionUiState(
                    metDays = 2,
                    totalDays = 2,
                    progress = 1f,
                ),

            customRangeProtein =
                NutritionGoalCompletionUiState(
                    metDays = 20,
                    totalDays = 31,
                    progress = 20f / 31f,
                ),

            calendarDays =
                listOf(
                    NutritionHistoryDayUiState(
                        date =
                            LocalDate.of(
                                2026,
                                9,
                                1,
                            ),
                        calories = 1_200.0,
                        proteinGrams = 50.0,
                        hasLogs = true,
                        calorieGoalMet = false,
                        proteinGoalMet = true,
                    ),
                    NutritionHistoryDayUiState(
                        date = CURRENT_DATE,
                        calories = 1_800.0,
                        proteinGrams = 60.0,
                        hasLogs = true,
                        calorieGoalMet = true,
                        proteinGoalMet = true,
                    ),
                    NutritionHistoryDayUiState(
                        date =
                            CURRENT_DATE.plusDays(1),
                        calories = 0.0,
                        proteinGrams = 0.0,
                        hasLogs = false,
                        calorieGoalMet = false,
                        proteinGoalMet = false,
                        isFuture = true,
                    ),
                ),
        )

    private fun taskState(
        inspectedTaskId: Long?,
        completionEpochDay: Long? =
            TASK_DAY,
        isCompleted: Boolean = false,
        isArchived: Boolean = false,
        canChangeCompletion: Boolean = !isArchived,
        isChanging: Boolean = false,
        deleteConfirmation: HistoryDeleteUiState? = null,
    ): HistoryScreenUiState =
        HistoryScreenUiState(
            tasks = TaskHistoryUiState(
                page = TaskHistoryPage.ALL_TASKS,
                allTasks = listOf(
                    HistoryTaskUiState(
                        id = TASK_ID,
                        name = "Test task",
                        category = "General",
                        schedule = "Daily",
                        completionEpochDay =
                            completionEpochDay,
                        isCompleted = isCompleted,
                        canChangeCompletion =
                            canChangeCompletion,
                        isChanging = isChanging,
                        isArchived = isArchived,
                    )
                ),
                inspectedTaskId =
                    inspectedTaskId,
                deleteConfirmation =
                    deleteConfirmation,
            )
        )

    private fun showScreen(
        state: HistoryScreenUiState,
        actions: MutableList<HistoryAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                HistoryScreen(
                    state = state,
                    onAction = actions::add,
                )
            }
        }
    }

    private companion object {
        const val TASK_ID = 42L

        val TEST_DATE: LocalDate =
            LocalDate.of(2026, 8, 24)

        const val TASK_DAY = 20_000L

        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                9,
                2,
            )

        const val TASK_FILTER_A =
            "task:1"

        const val TASK_FILTER_B =
            "task:2"

        const val CATEGORY_FILTER =
            "category:health"
    }
}