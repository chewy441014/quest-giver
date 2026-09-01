package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NutritionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysTotalsProgressAndLogs(): Unit {
        showScreen(
            state =
                screenState(
                    logs =
                        listOf(
                            logRow(
                                archived = true
                            )
                        )
                )
        )

        composeRule
            .onNodeWithText(
                "750 / 1500 kcal minimum"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "20 / 40 g minimum"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionTags
                    .CALORIE_PROGRESS
            )
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    current = 0.5f,
                    range = 0f..1f,
                )
            )

        composeRule
            .onNodeWithTag(
                NutritionTags
                    .PROTEIN_PROGRESS
            )
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    current = 0.5f,
                    range = 0f..1f,
                )
            )

        composeRule
            .onNodeWithText(
                "Chicken · Brand A"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "500 kcal · 60 g protein"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Archived item"
            )
            .assertIsDisplayed()
    }

    @Test
    fun emptyDayDisplaysMessage(): Unit {
        showScreen()

        composeRule
            .onNodeWithText(
                "No food logged for this day."
            )
            .assertIsDisplayed()
    }

    @Test
    fun dateButtonSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionTags.DATE
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .OpenDatePicker
            ),
            actions,
        )
    }

    @Test
    fun logRowSendsInspectAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(
            state =
                screenState(
                    logs =
                        listOf(logRow())
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionTags.log(LOG_ID)
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .InspectLog(LOG_ID)
            ),
            actions,
        )
    }

    @Test
    fun bottomButtonsSendActions(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionTags.MANAGE
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionTags.ADD
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction.OpenManage,
                NutritionAction.OpenAddLog,
            ),
            actions,
        )
    }

    @Test
    fun datePickerCancelSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(
            state =
                screenState(
                    showDatePicker = true
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionTags.DATE_CANCEL
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .DismissDatePicker
            ),
            actions,
        )
    }

    @Test
    fun datePickerConfirmSendsDate(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(
            state =
                screenState(
                    selectedDate =
                        PAST_DATE,
                    showDatePicker = true,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionTags.DATE_CONFIRM
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            ),
            actions,
        )
    }

    @Test
    fun operationErrorCanBeDismissed(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(
            state =
                screenState(
                    operationError =
                        "Test error"
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithText("Test error")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("OK")
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .DismissOperationError
            ),
            actions,
        )
    }

    private fun showScreen(
        state: NutritionScreenUiState =
            screenState(),
        actions:
        MutableList<NutritionAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                NutritionScreen(
                    state = state,
                    onAction = actions::add,
                )
            }
        }
    }

    private fun screenState(
        selectedDate:
        LocalDate = CURRENT_DATE,
        showDatePicker: Boolean = false,
        logs:
        List<NutritionLogRowUiState> =
            emptyList(),
        operationError: String? = null,
    ): NutritionScreenUiState =
        NutritionScreenUiState(
            selectedDate = selectedDate,
            currentDate = CURRENT_DATE,
            isCurrentDay =
                selectedDate ==
                        CURRENT_DATE,
            canSelectNextDay =
                selectedDate <
                        CURRENT_DATE,
            showDatePicker =
                showDatePicker,
            logs = logs,
            totalCalories = 750.0,
            totalProteinGrams = 20.0,
            calorieGoal = 1_500.0,
            proteinGoalGrams = 40.0,
            calorieProgress = 0.5f,
            proteinProgress = 0.5f,
            isLoading = false,
            operationError =
                operationError,
        )

    private fun logRow(
        archived: Boolean = false,
    ): NutritionLogRowUiState =
        NutritionLogRowUiState(
            logId = LOG_ID,
            itemId = 10L,
            itemName = "Chicken",
            itemVersion = 1,
            versionLabel = "Brand A",
            consumedTime =
                LocalTime.of(12, 30),
            weightGrams = 200.0,
            calories = 500.0,
            proteinGrams = 60.0,
            isItemArchived = archived,
        )

    private companion object {
        const val LOG_ID = 1L

        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                30,
            )

        val PAST_DATE:
                LocalDate =
            CURRENT_DATE.minusDays(1)
    }
}