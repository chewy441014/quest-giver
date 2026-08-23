package com.prestonhill.questgiver.feature.habits

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HabitScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteButtonRequestsDelete() {
        val actions = mutableListOf<HabitAction>()

        showScreen(
            state = detailState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Delete habit")
            .performClick()

        assertEquals(
            listOf(HabitAction.RequestDeleteHabit(HABIT_ID)),
            actions
        )
    }

    @Test
    fun confirmationSendsDelete() {
        val actions = mutableListOf<HabitAction>()

        showScreen(
            state = confirmationState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Delete")
            .performClick()

        assertEquals(
            listOf(HabitAction.ConfirmDelete),
            actions
        )
    }

    @Test
    fun cancelDismissesConfirmation() {
        val actions = mutableListOf<HabitAction>()

        showScreen(
            state = confirmationState(),
            actions = actions
        )

        composeRule
            .onNodeWithText("Cancel")
            .performClick()

        assertEquals(
            listOf(HabitAction.DismissConfirmation),
            actions
        )
    }

    @Test
    fun loadingDisablesButtons() {
        showScreen(
            state = confirmationState(isDeleting = true),
            actions = mutableListOf()
        )

        composeRule
            .onNodeWithText("Deleting...")
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Cancel")
            .assertIsNotEnabled()
    }

    @Test
    fun errorMessageIsVisible() {
        showScreen(
            state = confirmationState(
                errorMessage =
                    "Habit could not be deleted."
            ),
            actions = mutableListOf()
        )

        composeRule
            .onNodeWithText(
                "Habit could not be deleted."
            )
            .assertIsDisplayed()
    }

    @Test
    fun confirmationTitleIsCorrect() {
        showScreen(
            state = confirmationState(),
            actions = mutableListOf()
        )

        composeRule
            .onNodeWithText("Delete habit?")
            .assertIsDisplayed()
    }

    @Test
    fun operationErrorIsVisible() {
        showScreen(
            state = HabitScreenUiState(
                operationError =
                    "Habit could not be archived."
            ),
            actions = mutableListOf()
        )

        composeRule
            .onNodeWithText("Something went wrong")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Habit could not be archived."
            )
            .assertIsDisplayed()
    }

    @Test
    fun operationErrorCanBeDismissed() {
        val actions = mutableListOf<HabitAction>()

        showScreen(
            state = HabitScreenUiState(
                operationError =
                    "Habit could not be restored."
            ),
            actions = actions
        )

        composeRule
            .onNodeWithText("OK")
            .performClick()

        assertEquals(
            listOf(
                HabitAction.DismissOperationError
            ),
            actions
        )
    }

    private fun showScreen(
        state: HabitScreenUiState,
        actions: MutableList<HabitAction>
    ) {
        composeRule.setContent {
            MaterialTheme {
                HabitScreen(
                    uiState = state,
                    onAction = actions::add
                )
            }
        }
    }

    private fun detailState(): HabitScreenUiState =
        HabitScreenUiState(
            categories = listOf(
                HabitCategoryUiState(
                    category = HabitCategory.ANYTIME,
                    habits = listOf(testHabit())
                )
            ),
            inspectedHabitId = HABIT_ID
        )

    private fun confirmationState(
        isDeleting: Boolean = false,
        errorMessage: String? = null
    ): HabitScreenUiState =
        HabitScreenUiState(
            confirmation =
                HabitConfirmationUiState.DeleteHabit(
                    habitId = HABIT_ID,
                    habitName = "Test habit",
                    isDeleting = isDeleting,
                    errorMessage = errorMessage
                )
        )

    private fun testHabit(): HabitRowUiState =
        HabitRowUiState(
            id = HABIT_ID,
            name = "Test habit",
            streakCount = 0,
            completionCountToday = 0,
            allowsMultipleCompletions = false,
            scheduleCompletions = 0,
            scheduleTarget = 1,
            dueStatus = HabitDueStatus.DUE
        )

    private companion object {
        const val HABIT_ID = 1L
    }
}