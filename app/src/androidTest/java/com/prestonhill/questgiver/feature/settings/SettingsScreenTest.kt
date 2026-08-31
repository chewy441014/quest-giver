package com.prestonhill.questgiver.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.prestonhill.questgiver.core.settings.AppSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nutritionGoalsRowSendsEditAction(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                SettingsTags.NUTRITION_GOALS
            )
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(
                SettingsAction.EditNutritionGoals
            ),
            actions,
        )
    }

    @Test
    fun nutritionGoalFieldsSendChanges(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showInteractiveEditor(
            actions = actions
        )

        composeRule
            .onNodeWithTag(
                SettingsTags.CALORIE_GOAL
            )
            .performTextReplacement("2250")

        composeRule
            .onNodeWithTag(
                SettingsTags.PROTEIN_GOAL
            )
            .performTextReplacement("100")

        assertEquals(
            listOf(
                SettingsAction
                    .ChangeCalorieGoal(
                        "2250"
                    ),
                SettingsAction
                    .ChangeProteinGoal(
                        "100"
                    ),
            ),
            actions,
        )
    }

    @Test
    fun decimalCalorieGoalInputIsIgnored(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showInteractiveEditor(
            actions = actions
        )

        composeRule
            .onNodeWithTag(
                SettingsTags.CALORIE_GOAL
            )
            .performTextReplacement(
                "1500.5"
            )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun decimalProteinGoalInputIsIgnored(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showInteractiveEditor(
            actions = actions
        )

        composeRule
            .onNodeWithTag(
                SettingsTags.PROTEIN_GOAL
            )
            .performTextReplacement(
                "40.5"
            )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun changedGoalsCanBeSaved(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showScreen(
            state =
                editorState(
                    calorieText = "2250",
                    proteinText = "100",
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .SAVE_NUTRITION_GOALS
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                SettingsAction.SaveNutritionGoals
            ),
            actions,
        )
    }

    @Test
    fun unchangedGoalsCannotBeSaved(): Unit {
        showScreen(
            state = editorState()
        )

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .SAVE_NUTRITION_GOALS
            )
            .assertIsNotEnabled()
    }

    @Test
    fun invalidGoalsCannotBeSaved(): Unit {
        showScreen(
            state =
                editorState(
                    calorieText = "0",
                    proteinText = "40",
                )
        )

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .SAVE_NUTRITION_GOALS
            )
            .assertIsNotEnabled()
    }

    @Test
    fun nutritionGoalEditorCanCancel(): Unit {
        val actions =
            mutableListOf<SettingsAction>()

        showScreen(
            state = editorState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .CANCEL_NUTRITION_GOALS
            )
            .performClick()

        assertEquals(
            listOf(
                SettingsAction
                    .DismissNutritionGoals
            ),
            actions,
        )
    }

    @Test
    fun savingDisablesNutritionGoalEditor(): Unit {
        showScreen(
            state =
                editorState(
                    calorieText = "2250",
                    proteinText = "100",
                    isSaving = true,
                )
        )

        composeRule
            .onNodeWithTag(
                SettingsTags.CALORIE_GOAL
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                SettingsTags.PROTEIN_GOAL
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .SAVE_NUTRITION_GOALS
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                SettingsTags
                    .CANCEL_NUTRITION_GOALS
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Saving...")
            .assertIsDisplayed()
    }

    private fun showScreen(
        state: SettingsUiState =
            SettingsUiState(
                isLoading = false
            ),
        actions:
        MutableList<SettingsAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }
    }

    private fun showInteractiveEditor(
        initialState: SettingsUiState =
            editorState(),
        actions:
        MutableList<SettingsAction>,
    ) {
        var currentState by
        mutableStateOf(initialState)

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = currentState,
                    onAction = { action ->
                        actions += action

                        val editor =
                            currentState
                                .nutritionGoalsEditor

                        currentState =
                            when (action) {
                                is SettingsAction
                                .ChangeCalorieGoal ->
                                    currentState.copy(
                                        nutritionGoalsEditor =
                                            editor?.copy(
                                                calorieGoalText =
                                                    action.value
                                            )
                                    )

                                is SettingsAction
                                .ChangeProteinGoal ->
                                    currentState.copy(
                                        nutritionGoalsEditor =
                                            editor?.copy(
                                                proteinGoalText =
                                                    action.value
                                            )
                                    )

                                else -> currentState
                            }
                    },
                    onBack = {},
                )
            }
        }
    }

    private fun editorState(
        calorieText: String = "1500",
        proteinText: String = "40",
        isSaving: Boolean = false,
    ): SettingsUiState =
        SettingsUiState(
            settings = AppSettings(),
            isLoading = false,
            isSaving = isSaving,
            nutritionGoalsEditor =
                NutritionGoalsEditorUiState(
                    originalCalorieGoal =
                        1_500.0,
                    originalProteinGoalGrams =
                        40.0,
                    calorieGoalText =
                        calorieText,
                    proteinGoalText =
                        proteinText,
                ),
        )
}