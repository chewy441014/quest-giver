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
    fun nutritionTemplateIsVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                section =
                    HistorySection.NUTRITION
            )
        )

        composeRule
            .onNodeWithText(
                "No nutrition history to show yet."
            )
            .assertIsDisplayed()
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

    private fun taskState(
        inspectedTaskId: Long?,
        completionEpochDay: Long? =
            TASK_DAY,
        isCompleted: Boolean = false,
        isArchived: Boolean = false,
        canChangeCompletion: Boolean = !isArchived,
        isChanging: Boolean = false,
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
    }
}