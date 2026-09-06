package com.prestonhill.questgiver.feature.habits

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun historyInclusionSendsChange(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        val editor =
            HabitEditorUiState(
                name = "Lift",
                displaySectionId = "ANYTIME",
                isVisibleInHistory = true,
            )

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            )
                        ),
                    editor = editor,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.INCLUDE_IN_HISTORY
            )
            .performClick()

        assertEquals(
            HabitAction.UpdateHabitEditor(
                editor.copy(
                    isVisibleInHistory = false
                )
            ),
            actions.last(),
        )
    }

    @Test
    fun savingDisablesHistoryInclusion(): Unit {
        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            )
                        ),
                    editor =
                        HabitEditorUiState(
                            name = "Lift",
                            displaySectionId =
                                "ANYTIME",
                            isSaving = true,
                        ),
                ),
            actions = mutableListOf(),
        )

        composeRule
            .onNodeWithTag(
                HabitTags.INCLUDE_IN_HISTORY
            )
            .assertIsNotEnabled()
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
    fun sectionHeaderSendsToggle(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "TRAINING",
                                name = "Training",
                            )
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithText("Training")
            .performClick()

        assertEquals(
            listOf(
                HabitAction.ToggleSection(
                    "TRAINING"
                )
            ),
            actions,
        )
    }

    @Test
    fun editorSectionSelectionSendsChange(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        val editor =
            HabitEditorUiState(
                name = "Lift",
                displaySectionId = "ANYTIME",
            )

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            ),
                            HabitDisplaySectionUiState(
                                id = "TRAINING",
                                name = "Training",
                            ),
                        ),
                    editor = editor,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.editorSection(
                    "TRAINING"
                )
            )
            .performClick()

        assertEquals(
            listOf(
                HabitAction.UpdateHabitEditor(
                    editor.copy(
                        displaySectionId =
                            "TRAINING"
                    )
                )
            ),
            actions,
        )
    }

    @Test
    fun historyCategorySendsChange(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        val editor =
            HabitEditorUiState(
                name = "Lift",
                displaySectionId = "ANYTIME",
            )

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            )
                        ),
                    editor = editor,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.HISTORY_CATEGORY
            )
            .performTextReplacement("Gym")

        assertEquals(
            listOf(
                HabitAction.UpdateHabitEditor(
                    editor.copy(
                        historyCategory = "Gym"
                    )
                )
            ),
            actions,
        )
    }

    @Test
    fun sectionManagerSendsActions(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state = sectionManagerState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.ADD_SECTION
            )
            .performClick()

        assertEquals(
            HabitAction.AddDisplaySection,
            actions.last(),
        )

        composeRule
            .onNodeWithTag(
                HabitTags.moveSectionDown(
                    "ANYTIME"
                )
            )
            .performClick()

        assertEquals(
            HabitAction.MoveDisplaySectionDown(
                "ANYTIME"
            ),
            actions.last(),
        )
    }

    @Test
    fun sectionLongPressSendsEdit(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state = sectionManagerState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.sectionRow(
                    "ANYTIME"
                )
            )
            .performTouchInput {
                longClick()
            }

        assertEquals(
            listOf(
                HabitAction.EditDisplaySection(
                    "ANYTIME"
                )
            ),
            actions,
        )
    }

    @Test
    fun uncategorizedLongPressDoesNothing(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state = sectionManagerState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.sectionRow(
                    "UNCATEGORIZED"
                )
            )
            .performTouchInput {
                longClick()
            }

        assertTrue(actions.isEmpty())
    }

    @Test
    fun sectionEditorSendsActions(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state =
                sectionManagerState(
                    editor =
                        HabitSectionEditorUiState(
                            sectionId = "ANYTIME",
                            name = "Anytime",
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.SECTION_NAME
            )
            .performTextReplacement(
                "Daytime"
            )

        assertEquals(
            HabitAction.ChangeDisplaySectionName(
                "Daytime"
            ),
            actions.last(),
        )

        composeRule
            .onNodeWithTag(
                HabitTags.SAVE_SECTION
            )
            .performClick()

        assertEquals(
            HabitAction.SaveDisplaySection,
            actions.last(),
        )

        composeRule
            .onNodeWithTag(
                HabitTags.deleteSection(
                    "ANYTIME"
                )
            )
            .performClick()

        assertEquals(
            HabitAction.RequestDeleteDisplaySection(
                "ANYTIME"
            ),
            actions.last(),
        )
    }

    @Test
    fun newSectionHasNoDeleteButton(): Unit {
        showScreen(
            state =
                sectionManagerState(
                    editor =
                        HabitSectionEditorUiState(
                            name = "Training"
                        )
                ),
            actions = mutableListOf(),
        )

        composeRule
            .onNodeWithTag(
                HabitTags.deleteSection(
                    "ANYTIME"
                )
            )
            .assertDoesNotExist()
    }

    @Test
    fun sectionDeleteConfirmationSendsAction(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        showScreen(
            state =
                sectionManagerState(
                    confirmation =
                        HabitSectionDeleteUiState(
                            sectionId = "TRAINING",
                            sectionName = "Training",
                        )
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.CONFIRM_SECTION_DELETE
            )
            .performClick()

        assertEquals(
            listOf(
                HabitAction
                    .ConfirmDeleteDisplaySection
            ),
            actions,
        )
    }

    @Test
    fun habitEditorCanSelectNewSection(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        val editor =
            HabitEditorUiState(
                name = "Lift",
                displaySectionId = "ANYTIME",
            )

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            )
                        ),
                    editor = editor,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.NEW_SECTION
            )
            .performClick()

        assertEquals(
            HabitAction.UpdateHabitEditor(
                editor.copy(
                    newDisplaySectionName = ""
                )
            ),
            actions.last(),
        )
    }

    @Test
    fun newSectionNameSendsChange(): Unit {
        val actions =
            mutableListOf<HabitAction>()

        val editor =
            HabitEditorUiState(
                name = "Lift",
                displaySectionId = "ANYTIME",
                newDisplaySectionName = "",
            )

        showScreen(
            state =
                HabitScreenUiState(
                    sections =
                        listOf(
                            HabitDisplaySectionUiState(
                                id = "ANYTIME",
                                name = "Anytime",
                            )
                        ),
                    editor = editor,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                HabitTags.NEW_SECTION_NAME
            )
            .performTextReplacement(
                "Training"
            )

        assertEquals(
            HabitAction.UpdateHabitEditor(
                editor.copy(
                    newDisplaySectionName =
                        "Training"
                )
            ),
            actions.last(),
        )
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

    private fun sectionManagerState(
        editor: HabitSectionEditorUiState? = null,
        confirmation:
        HabitSectionDeleteUiState? = null,
    ): HabitScreenUiState =
        HabitScreenUiState(
            sections =
                listOf(
                    HabitDisplaySectionUiState(
                        id = "ANYTIME",
                        name = "Anytime",
                        canMoveDown = true,
                    ),
                    HabitDisplaySectionUiState(
                        id = "UNCATEGORIZED",
                        name = "Uncategorized",
                        canMoveUp = true,
                        canEdit = false,
                    ),
                ),
            sectionManager =
                HabitSectionManagerUiState(
                    editor = editor,
                    confirmation = confirmation,
                ),
        )

    private fun detailState():
            HabitScreenUiState =
        HabitScreenUiState(
            sections =
                listOf(
                    HabitDisplaySectionUiState(
                        id = "ANYTIME",
                        name = "Anytime",
                        habits =
                            listOf(testHabit()),
                    )
                ),
            inspectedHabitId = HABIT_ID,
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