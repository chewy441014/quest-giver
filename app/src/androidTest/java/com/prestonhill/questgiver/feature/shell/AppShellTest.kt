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
import androidx.compose.ui.test.assertIsSelected
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsGearOpensSettings(): Unit {
        var opened = false

        showShell(
            onOpenSettings = {
                opened = true
            }
        )

        composeRule
            .onNodeWithTag(
                AppShellTags.SETTINGS
            )
            .performClick()

        assertTrue(opened)
    }

    @Test
    fun selectedPageIsRestored(): Unit {
        showShell(
            selectedPage =
                AppPage.NUTRITION
        )

        composeRule
            .onNodeWithTag(
                AppShellTags.page(
                    AppPage.NUTRITION
                )
            )
            .assertIsSelected()
    }

    @Test
    fun pageChangeIsReported(): Unit {
        val pages =
            mutableListOf<AppPage>()

        showShell(
            onPageChanged = pages::add
        )

        composeRule.waitForIdle()
        pages.clear()

        composeRule
            .onNodeWithTag(
                AppShellTags.page(
                    AppPage.NUTRITION
                )
            )
            .performClick()

        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            pages.lastOrNull() ==
                    AppPage.NUTRITION
        }

        assertEquals(
            listOf(AppPage.NUTRITION),
            pages,
        )
    }

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
    private fun showShell(
        selectedPage: AppPage =
            AppPage.HABITS,
        habitActions:
        MutableList<HabitAction> =
            mutableListOf(),
        onOpenSettings: () -> Unit = {},
        onPageChanged: (AppPage) -> Unit = {},
    ) {
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
                    onOpenSettings =
                        onOpenSettings,
                    historyState =
                        HistoryScreenUiState(),
                    onHistoryAction = {},
                    nutritionState =
                        NutritionScreenUiState(),
                    onNutritionAction = {},
                    selectedPage = selectedPage,
                    onPageChanged =
                        onPageChanged,
                )
            }
        }
    }
}