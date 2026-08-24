package com.prestonhill.questgiver.feature.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsBothSections(): Unit {
        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = 1L,
                        name = "Today task"
                    )
                ),
                upcoming = listOf(
                    TaskDayUiState(
                        date = TEST_DATE.plusDays(1),
                        tasks = listOf(
                            taskRow(
                                id = 2L,
                                name = "Upcoming task",
                                date = TEST_DATE.plusDays(1)
                            )
                        )
                    )
                )
            )
        )

        composeRule
            .onNodeWithText("Today task")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Upcoming task")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(TaskTags.TODAY_LIST)
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(TaskTags.UPCOMING_LIST)
            .assertIsDisplayed()
    }

    @Test
    fun completeSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Complete me"
                    )
                )
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.check(TASK_ID))
            .performClick()

        assertEquals(
            listOf(
                TaskAction.Complete(
                    taskId = TASK_ID,
                    completionEpochDay = TEST_DATE.toEpochDay(),
                )
            ),
            actions
        )
    }

    @Test
    fun completedIsDisabled(): Unit {
        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Completed task",
                        canComplete = false,
                        isCompleted = true
                    )
                )
            )
        )

        composeRule
            .onNodeWithTag(TaskTags.check(TASK_ID))
            .assertIsNotEnabled()
    }

    @Test
    fun todayRowOpensDetails(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Inspect today"
                    )
                )
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.row(TASK_ID))
            .performClick()

        assertEquals(
            listOf(TaskAction.Inspect(TASK_ID)),
            actions
        )
    }

    @Test
    fun upcomingOpensDetails(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                upcoming = listOf(
                    TaskDayUiState(
                        date = TEST_DATE.plusDays(1),
                        tasks = listOf(
                            taskRow(
                                id = TASK_ID,
                                name = "Inspect upcoming",
                                date = TEST_DATE.plusDays(1)
                            )
                        )
                    )
                )
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.upcoming(TASK_ID))
            .performClick()

        assertEquals(
            listOf(TaskAction.Inspect(TASK_ID)),
            actions
        )
    }

    @Test
    fun hiddenToggleWorks(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                hasHiddenToday = true,
                showHiddenToday = false
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.HIDDEN_TOGGLE)
            .performClick()

        assertEquals(
            listOf(TaskAction.ToggleHidden),
            actions
        )
    }

    @Test
    fun hiddenToggleIsOmitted(): Unit {
        showScreen(
            state = TaskScreenUiState(
                hasHiddenToday = false
            )
        )

        composeRule
            .onNodeWithTag(TaskTags.HIDDEN_TOGGLE)
            .assertDoesNotExist()
    }

    @Test
    fun addSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.ADD)
            .performClick()

        assertEquals(
            listOf(TaskAction.Add),
            actions
        )
    }

    @Test
    fun taskAppearsOnce(): Unit {
        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Unique task"
                    )
                )
            )
        )

        composeRule
            .onAllNodesWithText("Unique task")
            .assertCountEquals(1)
    }

    @Test
    fun listsScrollSeparately(): Unit {
        val today = (0 until 20).map { index ->
            taskRow(
                id = index.toLong() + 1L,
                name = "Today $index",
                order = index
            )
        }

        val upcoming = (0 until 20).map { index ->
            val date = TEST_DATE.plusDays(index + 1L)

            TaskDayUiState(
                date = date,
                tasks = listOf(
                    taskRow(
                        id = index.toLong() + 100L,
                        name = "Upcoming $index",
                        date = date,
                        order = index
                    )
                )
            )
        }

        showScreen(
            state = TaskScreenUiState(
                today = today,
                upcoming = upcoming
            )
        )

        composeRule
            .onNodeWithTag(TaskTags.TODAY_LIST)
            .performScrollToIndex(19)

        composeRule
            .onNodeWithText("Today 19")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(TaskTags.UPCOMING_LIST)
            .performScrollToIndex(19)

        composeRule
            .onNodeWithText("Upcoming 19")
            .assertIsDisplayed()
    }

    private fun showScreen(
        state: TaskScreenUiState,
        actions: MutableList<TaskAction> =
            mutableListOf()
    ) {
        composeRule.setContent {
            MaterialTheme {
                TaskScreen(
                    state = state,
                    onAction = actions::add
                )
            }
        }
    }

    private fun taskRow(
        id: Long,
        name: String,
        date: LocalDate = TEST_DATE,
        dueTime: LocalTime? = null,
        canComplete: Boolean = true,
        isCompleted: Boolean = false,
        order: Int = 0
    ): TaskRowUiState =
        TaskRowUiState(
            id = id,
            name = name,
            category = null,
            scheduledDate = date,
            dueTime = dueTime,
            completionEpochDay = date.toEpochDay(),
            canComplete = canComplete,
            isCompleted = isCompleted,
            displayOrder = order
        )

    private companion object {
        const val TASK_ID = 42L
        val TEST_DATE: LocalDate =
            LocalDate.of(2026, 8, 24)
    }
}