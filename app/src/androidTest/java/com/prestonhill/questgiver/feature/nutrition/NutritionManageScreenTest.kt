package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NutritionManageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysManageBrowser(): Unit {
        showScreen()

        composeRule
            .onNodeWithTag(
                NutritionManageTags.SCREEN
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Manage foods")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Milk")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "120 kcal · 8 g protein per 100 g"
            )
            .assertIsDisplayed()
    }

    @Test
    fun backSendsDismissAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionManageTags.BACK
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .DismissDestination
            ),
            actions,
        )
    }

    @Test
    fun searchSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionManageTags.SEARCH
            )
            .performTextReplacement("Oats")

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeManageSearch(
                        "Oats"
                    )
            ),
            actions,
        )
    }

    @Test
    fun sortSelectionSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionManageTags.SORT
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags.sort(
                    NutritionItemSort.PROTEIN
                )
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeManageSort(
                        NutritionItemSort.PROTEIN
                    )
            ),
            actions,
        )
    }

    @Test
    fun filtersSendActionsOnApply(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(actions = actions)

        composeRule
            .onNodeWithTag(
                NutritionManageTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_PROTEIN
            )
            .performTextReplacement("25")

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_RATIO
            )
            .performTextReplacement("12")

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .archiveFilter(
                        NutritionArchiveFilter
                            .ARCHIVED
                    )
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_APPLY
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ChangeManageMinimumProtein(
                        "25"
                    ),
                NutritionAction
                    .ChangeManageMinimumProteinRatio(
                        "12"
                    ),
                NutritionAction
                    .ChangeManageArchiveFilter(
                        NutritionArchiveFilter
                            .ARCHIVED
                    ),
            ),
            actions,
        )
    }

    @Test
    fun invalidFilterDisablesApply(): Unit {
        showScreen()

        composeRule
            .onNodeWithTag(
                NutritionManageTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_PROTEIN
            )
            .performTextReplacement("invalid")

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_APPLY
            )
            .assertIsNotEnabled()
    }

    @Test
    fun resetSendsAction(): Unit {
        val actions =
            mutableListOf<NutritionAction>()

        showScreen(
            state =
                manageState().copy(
                    minimumProteinText = "20",
                    archiveFilter =
                        NutritionArchiveFilter
                            .ARCHIVED,
                ),
            actions = actions,
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_RESET
            )
            .performClick()

        assertEquals(
            listOf(
                NutritionAction
                    .ResetManageFilters
            ),
            actions,
        )
    }

    @Test
    fun fullManageListCanReachPastTen(): Unit {
        val options =
            (1L..12L).map { id ->
                option(
                    id = id,
                    name = "$id",
                    createdAt = id,
                )
            }

        showScreen(
            state =
                NutritionManageUiState(
                    itemOptions = options
                )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.LIST
            )
            .performScrollToNode(
                hasTestTag(
                    NutritionManageTags.group(
                        "1"
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    "1"
                )
            )
            .assertIsDisplayed()
    }

    @Test
    fun archivedItemDisplaysStatus(): Unit {
        showScreen(
            state =
                NutritionManageUiState(
                    itemOptions =
                        listOf(
                            option(
                                id = 2L,
                                name = "Old food",
                                archived = true,
                            )
                        ),
                    archiveFilter =
                        NutritionArchiveFilter
                            .ARCHIVED,
                )
        )

        composeRule
            .onNodeWithText("Old food")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Archived")
            .assertIsDisplayed()
    }

    private fun showScreen(
        state: NutritionManageUiState =
            manageState(),
        actions:
        MutableList<NutritionAction> =
            mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                NutritionManageScreen(
                    state = state,
                    onAction = actions::add,
                )
            }
        }
    }

    private fun manageState():
            NutritionManageUiState =
        NutritionManageUiState(
            itemOptions =
                listOf(
                    option(
                        id = 1L,
                        name = "Milk",
                        calories = 120.0,
                        protein = 8.0,
                    )
                )
        )

    private fun option(
        id: Long,
        name: String,
        calories: Double = 100.0,
        protein: Double = 10.0,
        createdAt: Long = 1_000L,
        archived: Boolean = false,
    ): NutritionItemOptionUiState =
        NutritionItemOptionUiState(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            version = 0,
            versionLabel = null,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            createdAtEpochMillis =
                createdAt,
            lastConsumedAtEpochMillis =
                null,
            isArchived = archived,
        )
}