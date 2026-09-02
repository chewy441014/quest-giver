package com.prestonhill.questgiver.feature.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionItemEditorUiStateTest {
    @Test
    fun perHundredGramEntryValidates(): Unit {

        val invalid =
            NutritionItemEditorUiState(
                nameText = "Milk",
                caloriesPer100gText = "",
                proteinPer100gText = "8",
                entryMode =
                    NutritionEntryMode.PER_100_GRAMS,
            )

        assertFalse(invalid.canSave)

        val valid =
            invalid.copy(
                caloriesPer100gText = "120"
            )

        assertTrue(
            valid.manualNutritionValid
        )

        assertTrue(valid.canSave)
    }

    @Test
    fun servingEntryRequiresPositiveWeight(): Unit {
        val invalid =
            NutritionItemEditorUiState(
                nameText = "Milk",
                entryMode =
                    NutritionEntryMode.SERVING,
                servingWeightText = "0",
                servingCaloriesText = "180",
                servingProteinText = "12",
            )

        assertFalse(
            invalid.manualNutritionValid
        )

        val valid =
            invalid.copy(
                servingWeightText = "250"
            )

        assertTrue(
            valid.manualNutritionValid
        )

        assertTrue(valid.canSave)
    }

    @Test
    fun negativeNutritionValuesAreInvalid(): Unit {
        val state =
            NutritionItemEditorUiState(
                nameText = "Test",
                caloriesPer100gText = "-1",
                proteinPer100gText = "10",
            )

        assertFalse(
            state.manualNutritionValid
        )

        assertFalse(state.canSave)
    }

    @Test
    fun componentsMustTotalOneHundredGrams(): Unit {
        val first =
            option(
                id = 1L,
                name = "First",
                calories = 200.0,
                protein = 20.0,
            )

        val second =
            option(
                id = 2L,
                name = "Second",
                calories = 100.0,
                protein = 10.0,
            )

        val incomplete =
            NutritionItemEditorUiState(
                nameText = "Combination",
                components =
                    listOf(
                        NutritionItemComponentUiState(
                            item = first,
                            gramsText = "25",
                        ),
                        NutritionItemComponentUiState(
                            item = second,
                            gramsText = "65",
                        ),
                    ),
            )

        assertFalse(
            incomplete.componentsValid
        )

        assertEquals(
            10.0,
            incomplete
                .componentWeightRemaining,
            TOLERANCE,
        )

        assertFalse(incomplete.canSave)

        val complete =
            incomplete.copy(
                components =
                    listOf(
                        NutritionItemComponentUiState(
                            item = first,
                            gramsText = "25",
                        ),
                        NutritionItemComponentUiState(
                            item = second,
                            gramsText = "75",
                        ),
                    )
            )

        assertTrue(
            complete.componentsValid
        )

        assertEquals(
            125.0,
            complete
                .calculatedCaloriesPer100g,
            TOLERANCE,
        )

        assertEquals(
            12.5,
            complete
                .calculatedProteinPer100g,
            TOLERANCE,
        )

        assertTrue(complete.canSave)
    }

    @Test
    fun hiddenManualFieldsDoNotDirtyComposedItem(): Unit {
        val component =
            NutritionItemComponentUiState(
                item =
                    option(
                        id = 2L,
                        name = "Component",
                    ),
                gramsText = "100",
            )

        val original =
            NutritionItemEditorUiState(
                itemId = 1L,
                originalNameKey =
                    "combination",
                nameText = "Combination",
                caloriesPer100gText = "100",
                proteinPer100gText = "10",
                components =
                    listOf(component),
            )

        val clean =
            original.copy(
                initialSnapshot =
                    original.currentSnapshot
            )

        assertFalse(clean.isDirty)
        assertFalse(clean.canSave)

        val hiddenFieldChanged =
            clean.copy(
                caloriesPer100gText = "500"
            )

        assertFalse(
            hiddenFieldChanged.isDirty
        )
    }

    @Test
    fun saveAsVersionRequiresAnEditedItemAndChanges(): Unit {
        val original =
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
                        ),
                        option(
                            id = 2L,
                            name = "Milk",
                            version = 1,
                        ),
                    ),
                nameText = "Milk",
                entryMode = NutritionEntryMode.PER_100_GRAMS,
                caloriesPer100gText = "120",
                proteinPer100gText = "8",
            )

        val clean =
            original.copy(
                initialSnapshot =
                    original.currentSnapshot
            )

        assertFalse(clean.isDirty)

        assertFalse(
            clean.canSaveAsVersion
        )

        val changed =
            clean.copy(
                proteinPer100gText = "9"
            )

        assertTrue(changed.isDirty)
        assertTrue(changed.canSave)
        assertTrue(changed.canSaveAsVersion)

        assertEquals(
            "Save as v2",
            changed.saveAsVersionText,
        )
    }

    @Test
    fun nextVersionUsesEditedName(): Unit {
        val state =
            NutritionItemEditorUiState(
                itemId = 1L,
                originalNameKey = "milk",
                knownItems =
                    listOf(
                        option(
                            id = 1L,
                            name = "Milk",
                            version = 0,
                        ),
                        option(
                            id = 2L,
                            name = "Oats",
                            version = 0,
                        ),
                        option(
                            id = 3L,
                            name = "Oats",
                            version = 2,
                        ),
                    ),
                nameText = "  OATS  ",
                caloriesPer100gText = "100",
                proteinPer100gText = "10",
            )

        assertEquals(
            "oats",
            state.normalizedNameKey,
        )

        assertEquals(
            3,
            state.nextVersion,
        )
    }

    @Test
    fun componentChoicesExcludeUnavailableItems(): Unit {
        val ownItem =
            option(
                id = 1L,
                name = "Own item",
            )

        val available =
            option(
                id = 2L,
                name = "Available",
            )

        val archived =
            option(
                id = 3L,
                name = "Archived",
                archived = true,
            )

        val alreadySelected =
            option(
                id = 4L,
                name = "Selected",
            )

        val state =
            NutritionItemEditorUiState(
                itemId = ownItem.id,
                knownItems =
                    listOf(
                        ownItem,
                        available,
                        archived,
                        alreadySelected,
                    ),
                components =
                    listOf(
                        NutritionItemComponentUiState(
                            item =
                                alreadySelected,
                            gramsText = "100",
                        )
                    ),
            )

        assertEquals(
            listOf(available.id),
            state
                .selectableComponentOptions
                .map { it.id },
        )
    }

    @Test
    fun versionOptionsIncludeArchivedVersions(): Unit {
        val state =
            NutritionItemEditorUiState(
                itemId = 1L,
                originalNameKey = "milk",
                knownItems =
                    listOf(
                        option(
                            id = 2L,
                            name = "Milk",
                            version = 1,
                            archived = true,
                        ),
                        option(
                            id = 1L,
                            name = "Milk",
                            version = 0,
                        ),
                        option(
                            id = 3L,
                            name = "Oats",
                        ),
                    ),
            )

        assertEquals(
            listOf(0, 1),
            state.versionOptions
                .map { it.version },
        )

        assertTrue(
            state.versionOptions
                .last()
                .isArchived
        )
    }

    private fun option(
        id: Long,
        name: String,
        version: Int = 0,
        calories: Double = 100.0,
        protein: Double = 10.0,
        archived: Boolean = false,
    ): NutritionItemOptionUiState =
        NutritionItemOptionUiState(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            version = version,
            versionLabel = null,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            createdAtEpochMillis =
                1_000L,
            lastConsumedAtEpochMillis =
                null,
            isArchived = archived,
        )

    private companion object {
        const val TOLERANCE = 0.000_001
    }
}