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
    fun dashboardShowsGraphs(): Unit {
        showScreen(
            state = HistoryScreenUiState()
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.CATEGORY_GRAPH
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.PINNED_GRAPHS
            )
            .assertIsDisplayed()
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
    }
}