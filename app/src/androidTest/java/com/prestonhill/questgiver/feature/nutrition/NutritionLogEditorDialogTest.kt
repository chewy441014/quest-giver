package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NutritionLogEditorDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addEditorDisplaysExpectedFields(): Unit {
        showDialog()

        composeRule
            .onNodeWithText("Add food log")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Sun, Aug 30, 2026"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SEARCH
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.WEIGHT
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.TIME
            )
            .assertIsDisplayed()
    }

    @Test
    fun searchSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SEARCH
            )
            .performTextReplacement("Milk")

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeLogItemSearch(
                        "Milk"
                    )
            ),
            actions,
        )
    }

    @Test
    fun foodResultSendsSelection(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.food(
                    "milk"
                )
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .SelectLogFood("milk")
            ),
            actions,
        )
    }

    @Test
    fun versionChoiceSendsItemSelection(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(
            editor =
                editorState(
                    options =
                        milkVersions(),
                    versionGroupNameKey =
                        "milk",
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .version(2L)
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .SelectLogItem(2L)
            ),
            actions,
        )
    }

    @Test
    fun weightChangeSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.WEIGHT
            )
            .performTextReplacement("125.5")

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeLogWeight(
                        "125.5"
                    )
            ),
            actions,
        )
    }

    @Test
    fun sortSelectionSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SORT
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.sort(
                    NutritionItemSort.PROTEIN
                )
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeLogItemSort(
                        NutritionItemSort
                            .PROTEIN
                    )
            ),
            actions,
        )
    }

    @Test
    fun filtersCanBeApplied(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .FILTER_PROTEIN
            )
            .performTextReplacement("30")

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .FILTER_RATIO
            )
            .performTextReplacement("15")

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .FILTER_APPLY
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeLogMinimumProtein(
                        "30"
                    ),
                NutritionAction
                    .ChangeLogMinimumProteinRatio(
                        "15"
                    ),
            ),
            actions,
        )
    }

    @Test
    fun filtersCanBeReset(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(
            editor =
                editorState().copy(
                    minimumProteinText =
                        "30"
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .FILTER_RESET
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ResetLogItemFilters
            ),
            actions,
        )
    }

    @Test
    fun timeConfirmSendsCurrentPickerTime(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.TIME
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .TIME_CONFIRM
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeLogTime(
                        LocalTime.of(12, 30)
                    )
            ),
            actions,
        )
    }

    @Test
    fun validAddCanSave(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                NutritionAction.SaveLog
            ),
            actions,
        )
    }

    @Test
    fun invalidAddCannotSave(): Unit {
        showDialog(
            editor =
                editorState().copy(
                    weightText = "0"
                )
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SAVE
            )
            .assertIsNotEnabled()
    }

    @Test
    fun cancelSendsDismissAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.CANCEL
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .DismissLogEditor
            ),
            actions,
        )
    }

    @Test
    fun editModeCanRequestDeletion(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(
            editor =
                editorState(
                    logId = LOG_ID
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithText("Edit food log")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.DELETE
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .RequestDeleteLog
            ),
            actions,
        )
    }

    @Test
    fun deleteConfirmationSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showDialog(
            editor =
                editorState(
                    logId = LOG_ID,
                    showDeleteConfirmation =
                        true,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags
                    .DELETE_CONFIRM
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction.DeleteLog
            ),
            actions,
        )
    }

    @Test
    fun archivedCurrentFoodIsMarked(): Unit {
        showDialog(
            editor =
                editorState(
                    options =
                        listOf(
                            option(
                                archived = true
                            )
                        )
                )
        )

        composeRule
            .onNodeWithText(
                "Archived food"
            )
            .assertIsDisplayed()
    }

    @Test
    fun savingDisablesEditorControls(): Unit {
        showDialog(
            editor =
                editorState().copy(
                    isSaving = true
                )
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SEARCH
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.WEIGHT
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.TIME
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SAVE
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.CANCEL
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Saving...")
            .assertIsDisplayed()
    }

    private fun showDialog(
        editor:
        NutritionLogEditorUiState =
            editorState(),
        actions:
        MutableList<NutritionAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                NutritionLogEditorDialog(
                    editor = editor,
                    onAction = actions::add,
                )
            }
        }
    }

    private fun editorState(
        logId: Long? = null,
        options:
        List<NutritionItemOptionUiState> =
            listOf(option()),
        versionGroupNameKey:
        String? = null,
        showDeleteConfirmation:
        Boolean = false,
    ): NutritionLogEditorUiState =
        NutritionLogEditorUiState(
            logId = logId,
            date = TEST_DATE,
            itemOptions = options,
            selectedItemId =
                options.first().id,
            weightText = "100",
            time =
                LocalTime.of(12, 30),
            versionGroupNameKey =
                versionGroupNameKey,
            showDeleteConfirmation =
                showDeleteConfirmation,
        )

    private fun milkVersions():
            List<NutritionItemOptionUiState> =
        listOf(
            option(
                id = 1L,
                version = 0,
                versionLabel = "Store",
            ),
            option(
                id = 2L,
                version = 1,
                versionLabel = "Brand",
            ),
        )

    private fun option(
        id: Long = 1L,
        version: Int = 0,
        versionLabel: String? = null,
        archived: Boolean = false,
    ): NutritionItemOptionUiState =
        NutritionItemOptionUiState(
            id = id,
            name = "Milk",
            nameKey = "milk",
            version = version,
            versionLabel = versionLabel,
            caloriesPer100g = 100.0,
            proteinPer100g = 10.0,
            createdAtEpochMillis =
                1_000L + id,
            lastConsumedAtEpochMillis =
                null,
            isArchived = archived,
        )

    private companion object {
        const val LOG_ID = 10L

        val TEST_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                30,
            )
    }
}