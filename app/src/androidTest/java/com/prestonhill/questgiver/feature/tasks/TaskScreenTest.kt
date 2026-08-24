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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsEnabled
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailEditSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = detailState(),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.EDIT)
            .performClick()

        assertEquals(
            listOf(TaskAction.Edit(TASK_ID)),
            actions
        )
    }

    @Test
    fun detailDeleteSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = detailState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Delete")
            .performClick()

        assertEquals(
            listOf(TaskAction.RequestDelete(TASK_ID)),
            actions
        )
    }

    @Test
    fun detailCloseSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = detailState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Close")
            .performClick()

        assertEquals(
            listOf(TaskAction.DismissDetails),
            actions
        )
    }

    @Test
    fun deleteOnlySendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = deleteState(),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.DELETE_TASK)
            .performClick()

        assertEquals(
            listOf(TaskAction.DeleteTask),
            actions
        )
    }

    @Test
    fun deleteHistorySendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = deleteState(),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.DELETE_HISTORY)
            .performClick()

        assertEquals(
            listOf(TaskAction.DeleteTaskAndHistory),
            actions
        )
    }

    @Test
    fun deleteCancelSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = deleteState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Cancel")
            .performClick()

        assertEquals(
            listOf(TaskAction.DismissDelete),
            actions
        )
    }

    @Test
    fun deletingDisablesButtons(): Unit {
        showScreen(
            state = deleteState(isDeleting = true)
        )

        composeRule
            .onNodeWithTag(TaskTags.DELETE_TASK)
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(TaskTags.DELETE_HISTORY)
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Cancel")
            .assertIsNotEnabled()
    }

    @Test
    fun deleteErrorIsVisible(): Unit {
        showScreen(
            state = deleteState(
                errorMessage =
                    "Task could not be deleted."
            )
        )

        composeRule
            .onNodeWithText(
                "Task could not be deleted."
            )
            .assertIsDisplayed()
    }

    @Test
    fun editorSaveSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                editor = TaskEditorUiState(
                    name = "New task",
                    scheduledDate = TEST_DATE
                )
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.EDITOR_SAVE)
            .performClick()

        assertEquals(
            listOf(TaskAction.Save),
            actions
        )
    }

    @Test
    fun editorNameSendsUpdate(): Unit {
        val actions = mutableListOf<TaskAction>()
        val editor = TaskEditorUiState(
            scheduledDate = TEST_DATE
        )

        showScreen(
            state = TaskScreenUiState(
                editor = editor
            ),
            actions = actions
        )

        composeRule
            .onNodeWithTag(TaskTags.EDITOR_NAME)
            .performTextReplacement("New task")

        assertEquals(
            listOf(
                TaskAction.UpdateEditor(
                    editor.copy(name = "New task")
                )
            ),
            actions
        )
    }

    @Test
    fun invalidEditorDisablesSave(): Unit {
        showScreen(
            state = TaskScreenUiState(
                editor = TaskEditorUiState(
                    name = "",
                    scheduledDate = TEST_DATE
                )
            )
        )

        composeRule
            .onNodeWithTag(TaskTags.EDITOR_SAVE)
            .assertIsNotEnabled()
    }

    @Test
    fun editorCancelSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                editor = TaskEditorUiState(
                    scheduledDate = TEST_DATE
                )
            ),
            actions = actions
        )

        composeRule
            .onNodeWithText("Cancel")
            .performClick()

        assertEquals(
            listOf(TaskAction.DismissEditor),
            actions
        )
    }

    @Test
    fun errorDismisses(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                operationError =
                    "Task could not be opened."
            ),
            actions = actions
        )

        composeRule
            .onNodeWithText("OK")
            .performClick()

        assertEquals(
            listOf(TaskAction.DismissError),
            actions
        )
    }

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
                TaskAction.SetCompletion(
                    taskId = TASK_ID,
                    completionEpochDay =
                        TEST_DATE.toEpochDay(),
                    completed = true,
                )
            ),
            actions
        )
    }

    @Test
    fun completedCanBeUnchecked(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Completed task",
                        canComplete = true,
                        isCompleted = true,
                    )
                )
            ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                TaskTags.check(TASK_ID)
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                TaskAction.SetCompletion(
                    taskId = TASK_ID,
                    completionEpochDay =
                        TEST_DATE.toEpochDay(),
                    completed = false,
                )
            ),
            actions,
        )
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

    @Test
    fun changingDisablesCheckbox(): Unit {
        showScreen(
            state = TaskScreenUiState(
                today = listOf(
                    taskRow(
                        id = TASK_ID,
                        name = "Changing task",
                        isChanging = true,
                    )
                )
            )
        )

        composeRule
            .onNodeWithTag(
                TaskTags.check(TASK_ID)
            )
            .assertIsNotEnabled()
    }

    @Test
    fun detailCheckboxSendsAction(): Unit {
        val actions = mutableListOf<TaskAction>()

        showScreen(
            state = detailState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                TaskTags.detailsCheck(TASK_ID)
            )
            .performClick()

        assertEquals(
            listOf(
                TaskAction.SetCompletion(
                    taskId = TASK_ID,
                    completionEpochDay =
                        TEST_DATE.toEpochDay(),
                    completed = true,
                )
            ),
            actions,
        )
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

    private fun detailState(): TaskScreenUiState =
        TaskScreenUiState(
            today = listOf(
                taskRow(
                    id = TASK_ID,
                    name = "Test task"
                )
            ),
            inspectedTaskId = TASK_ID
        )

    private fun deleteState(
        isDeleting: Boolean = false,
        errorMessage: String? = null
    ): TaskScreenUiState =
        TaskScreenUiState(
            confirmation = TaskDeleteUiState(
                taskId = TASK_ID,
                taskName = "Test task",
                isDeleting = isDeleting,
                errorMessage = errorMessage
            )
        )

    private fun taskRow(
        id: Long,
        name: String,
        date: LocalDate = TEST_DATE,
        dueTime: LocalTime? = null,
        canComplete: Boolean = true,
        isCompleted: Boolean = false,
        order: Int = 0,
        isChanging: Boolean = false,
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
            displayOrder = order,
            isChanging = isChanging,
        )

    private companion object {
        const val TASK_ID = 42L
        val TEST_DATE: LocalDate =
            LocalDate.of(2026, 8, 24)
    }
}