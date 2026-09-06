package com.prestonhill.questgiver.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.prestonhill.questgiver.feature.habits.HabitAction
import com.prestonhill.questgiver.feature.habits.HabitScreenUiState
import com.prestonhill.questgiver.feature.habits.HabitTags
import com.prestonhill.questgiver.feature.history.HistoryScreenUiState
import com.prestonhill.questgiver.feature.nutrition.NutritionScreenUiState
import com.prestonhill.questgiver.feature.tasks.TaskScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionsButtonSendsHabitAction(): Unit {
        val habitActions =
            mutableListOf<HabitAction>()

        composeRule.setContent {
            MaterialTheme {
                AppShell(
                    taskState =
                        TaskScreenUiState(),
                    onTaskAction = {},
                    habitState =
                        HabitScreenUiState(),
                    onHabitAction =
                        habitActions::add,
                    onOpenSettings = {},
                    historyState =
                        HistoryScreenUiState(),
                    onHistoryAction = {},
                    nutritionState =
                        NutritionScreenUiState(),
                    onNutritionAction = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                HabitTags.SECTIONS
            )
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(
                HabitAction.ShowSectionManager
            ),
            habitActions,
        )
    }
}