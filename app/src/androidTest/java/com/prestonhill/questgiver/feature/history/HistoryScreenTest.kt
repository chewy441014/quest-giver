package com.prestonhill.questgiver.feature.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun allLogsSendsAction(): Unit {
        val actions =
            mutableListOf<HistoryAction>()

        showScreen(
            state = HistoryScreenUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithText("View all logs")
            .performClick()

        assertEquals(
            listOf(
                HistoryAction.OpenTaskPage(
                    TaskHistoryPage.ALL_LOGS
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
    fun logRowsAreVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                tasks = TaskHistoryUiState(
                    page =
                        TaskHistoryPage.ALL_LOGS,
                    logDays = listOf(
                        HistoryTaskDayUiState(
                            date = TEST_DATE,
                            logs = listOf(
                                taskLog(
                                    id = 1L,
                                    taskId = TASK_ID,
                                    name = "Corrected task",
                                    corrected = true,
                                ),
                                taskLog(
                                    id = 2L,
                                    taskId = null,
                                    name = "Deleted task",
                                ),
                            ),
                        )
                    ),
                )
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.log(1L)
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.log(2L)
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Corrected")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Task deleted")
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
                "No tasks to show yet."
            )
            .assertIsDisplayed()
    }

    @Test
    fun emptyLogsIsVisible(): Unit {
        showScreen(
            state = HistoryScreenUiState(
                tasks = TaskHistoryUiState(
                    page =
                        TaskHistoryPage.ALL_LOGS
                )
            )
        )

        composeRule
            .onNodeWithText(
                "No task logs to show yet."
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

    private fun taskLog(
        id: Long,
        taskId: Long?,
        name: String,
        corrected: Boolean = false,
    ): HistoryTaskLogUiState =
        HistoryTaskLogUiState(
            id = id,
            taskId = taskId,
            taskName = name,
            category = "General",
            date = TEST_DATE,
            completedAtMillis = 1_000L,
            isCorrected = corrected,
        )

    private companion object {
        const val TASK_ID = 42L

        val TEST_DATE: LocalDate =
            LocalDate.of(2026, 8, 24)
    }
}