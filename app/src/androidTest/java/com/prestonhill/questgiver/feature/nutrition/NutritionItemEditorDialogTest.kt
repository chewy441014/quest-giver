package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test

class NutritionItemEditorDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newItemDefaultsToServingMode(): Unit {
        showEditor(
            NutritionItemEditorUiState()
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.EDITOR
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_WEIGHT
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_CALORIES
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_PROTEIN
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .CALORIES_PER_100
            )
            .assertDoesNotExist()
    }

    @Test
    fun itemFieldsSendActions(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showInteractiveEditor(
            initial =
                NutritionItemEditorUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.NAME
            )
            .performTextReplacement("Milk")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .VERSION_LABEL
            )
            .performTextReplacement(
                "Brand A"
            )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_WEIGHT
            )
            .performTextReplacement("250")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_CALORIES
            )
            .performTextReplacement("300")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_PROTEIN
            )
            .performTextReplacement("20")

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeItemName("Milk"),
                NutritionAction
                    .ChangeItemVersionLabel(
                        "Brand A"
                    ),
                NutritionAction
                    .ChangeItemServingWeight(
                        "250"
                    ),
                NutritionAction
                    .ChangeItemServingCalories(
                        "300"
                    ),
                NutritionAction
                    .ChangeItemServingProtein(
                        "20"
                    ),
            ),
            actions,
        )
    }

    @Test
    fun perHundredModeSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                NutritionItemEditorUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .PER_100_GRAMS
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeItemEntryMode(
                        NutritionEntryMode
                            .PER_100_GRAMS
                    )
            ),
            actions,
        )
    }

    @Test
    fun invalidItemCannotSave(): Unit {
        showEditor(
            NutritionItemEditorUiState()
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.SAVE
            )
            .assertIsNotEnabled()
    }

    @Test
    fun validItemSendsSave(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                NutritionItemEditorUiState(
                    nameText = "Milk",
                    servingWeightText = "250",
                    servingCaloriesText =
                        "300",
                    servingProteinText = "20",
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                NutritionAction.SaveItem
            ),
            actions,
        )
    }

    @Test
    fun dirtyExistingItemCanSaveAsVersion(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                existingEditor().copy(
                    proteinPer100gText =
                        "9"
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SAVE_AS_VERSION
            )
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .SaveItemAsVersion
            ),
            actions,
        )
    }

    @Test
    fun cleanExistingItemHidesSaveAsVersion(): Unit {
        showEditor(
            existingEditor()
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SAVE_AS_VERSION
            )
            .assertDoesNotExist()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.SAVE
            )
            .assertIsNotEnabled()
    }

    @Test
    fun versionSelectorSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                existingEditor(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .VERSION_SELECTOR
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .version(2L)
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .SelectItemEditorVersion(
                        2L
                    )
            ),
            actions,
        )
    }

    @Test
    fun dirtyItemDisablesVersionSelector(): Unit {
        showEditor(
            existingEditor().copy(
                proteinPer100gText = "9"
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .VERSION_SELECTOR
            )
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText(
                "Save or cancel changes before switching versions."
            )
            .assertIsDisplayed()
    }

    @Test
    fun componentWeightAndRemovalSendActions(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        val component =
            option(
                id = 3L,
                name = "Oats",
            )

        showEditor(
            editor =
                NutritionItemEditorUiState(
                    nameText = "Oat meal",
                    knownItems =
                        listOf(component),
                    components =
                        listOf(
                            NutritionItemComponentUiState(
                                item = component,
                                gramsText = "100",
                            )
                        ),
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .componentWeight(3L)
            )
            .performScrollTo()
            .performTextReplacement("75")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .removeComponent(3L)
            )
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeItemComponentWeight(
                        itemId = 3L,
                        value = "75",
                    ),
                NutritionAction
                    .RemoveItemComponent(
                        3L
                    ),
            ),
            actions,
        )
    }

    @Test
    fun componentPickerSendsSelection(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                NutritionItemEditorUiState(
                    knownItems =
                        listOf(
                            option(
                                id = 3L,
                                name = "Oats",
                            )
                        ),
                    showComponentPicker = true,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .component(3L)
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .AddItemComponent(
                        3L
                    )
            ),
            actions,
        )
    }

    @Test
    fun cancelSendsDismissAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showEditor(
            editor =
                NutritionItemEditorUiState(),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.CANCEL
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .DismissItemEditor
            ),
            actions,
        )
    }

    private fun showEditor(
        editor: NutritionItemEditorUiState,
        actions:
        MutableList<NutritionAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                NutritionItemEditorDialog(
                    editor = editor,
                    onAction = actions::add,
                )
            }
        }
    }

    private fun showInteractiveEditor(
        initial:
        NutritionItemEditorUiState,
        actions:
        MutableList<NutritionAction>,
    ) {
        var editor by mutableStateOf(initial)

        composeRule.setContent {
            MaterialTheme {
                NutritionItemEditorDialog(
                    editor = editor,
                    onAction = { action ->
                        actions += action

                        editor =
                            when (action) {
                                is NutritionAction
                                .ChangeItemName ->
                                    editor.copy(
                                        nameText =
                                            action.value
                                    )

                                is NutritionAction
                                .ChangeItemVersionLabel ->
                                    editor.copy(
                                        versionLabelText =
                                            action.value
                                    )

                                is NutritionAction
                                .ChangeItemServingWeight ->
                                    editor.copy(
                                        servingWeightText =
                                            action.value
                                    )

                                is NutritionAction
                                .ChangeItemServingCalories ->
                                    editor.copy(
                                        servingCaloriesText =
                                            action.value
                                    )

                                is NutritionAction
                                .ChangeItemServingProtein ->
                                    editor.copy(
                                        servingProteinText =
                                            action.value
                                    )

                                else -> editor
                            }
                    },
                )
            }
        }
    }

    private fun existingEditor():
            NutritionItemEditorUiState {
        val state =
            NutritionItemEditorUiState(
                itemId = 1L,
                originalNameKey = "milk",
                version = 0,
                knownItems =
                    listOf(
                        option(
                            id = 1L,
                            name = "Milk",
                            version = 0,
                            versionLabel =
                                "Original",
                        ),
                        option(
                            id = 2L,
                            name = "Milk",
                            version = 1,
                            versionLabel =
                                "New",
                        ),
                    ),
                nameText = "Milk",
                versionLabelText =
                    "Original",
                entryMode =
                    NutritionEntryMode
                        .PER_100_GRAMS,
                caloriesPer100gText =
                    "120",
                proteinPer100gText = "8",
            )

        return state.copy(
            initialSnapshot =
                state.currentSnapshot
        )
    }

    private fun option(
        id: Long,
        name: String,
        version: Int = 0,
        versionLabel: String? = null,
    ): NutritionItemOptionUiState =
        NutritionItemOptionUiState(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            version = version,
            versionLabel = versionLabel,
            caloriesPer100g = 100.0,
            proteinPer100g = 10.0,
            createdAtEpochMillis =
                1_000L,
            lastConsumedAtEpochMillis =
                null,
            isArchived = false,
        )
}