package com.prestonhill.questgiver.feature.nutrition

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLogEditorUiStateTest {
    @Test
    fun defaultResultsUseActivityAndLimitTen(): Unit {
        val options =
            (1L..12L).map { id ->
                option(
                    id = id,
                    name = "Item $id",
                    createdAt = id * 100L,
                )
            } +
                    option(
                        id = 100L,
                        name = "Recently eaten",
                        createdAt = 1L,
                        lastConsumedAt = 2_000L,
                    )

        val groups =
            editor(options)
                .visibleFoodGroups

        assertEquals(10, groups.size)

        assertEquals(
            "Recently eaten",
            groups.first().name,
        )

        assertEquals(
            listOf(
                "Recently eaten",
                "Item 12",
                "Item 11",
                "Item 10",
                "Item 9",
                "Item 8",
                "Item 7",
                "Item 6",
                "Item 5",
                "Item 4",
            ),
            groups.map { it.name },
        )
    }

    @Test
    fun searchIsNotLimitedToDefaultTen(): Unit {
        val state =
            editor(
                options =
                    (1L..12L).map { id ->
                        option(
                            id = id,
                            name = "Item $id",
                            createdAt = id,
                        )
                    },
                search = "Item",
            )

        assertEquals(
            12,
            state.visibleFoodGroups.size,
        )
    }

    @Test
    fun searchMatchesVersionLabelAndNumber(): Unit {
        val options =
            listOf(
                option(
                    id = 1L,
                    name = "Milk",
                    version = 0,
                    versionLabel = "Store",
                ),
                option(
                    id = 2L,
                    name = "Milk",
                    version = 1,
                    versionLabel = "Brand A",
                ),
                option(
                    id = 3L,
                    name = "Oats",
                    version = 2,
                ),
            )

        val brandResults =
            editor(
                options = options,
                search = "Brand A",
            )
                .visibleFoodGroups

        assertEquals(
            listOf("Milk"),
            brandResults.map { it.name },
        )

        assertEquals(
            listOf(2L),
            brandResults.single()
                .versions
                .map { it.id },
        )

        val versionResults =
            editor(
                options = options,
                search = "v2",
            )
                .visibleFoodGroups

        assertEquals(
            listOf("Oats"),
            versionResults.map { it.name },
        )
    }

    @Test
    fun proteinFiltersAreApplied(): Unit {
        val options =
            listOf(
                option(
                    id = 1L,
                    name = "Low protein",
                    calories = 100.0,
                    protein = 10.0,
                ),
                option(
                    id = 2L,
                    name = "High protein",
                    calories = 200.0,
                    protein = 40.0,
                ),
            )

        val proteinFiltered =
            editor(options).copy(
                minimumProteinText = "30"
            )

        assertEquals(
            listOf("High protein"),
            proteinFiltered
                .visibleFoodGroups
                .map { it.name },
        )

        val ratioFiltered =
            editor(options).copy(
                minimumProteinRatioText =
                    "15"
            )

        assertEquals(
            listOf("High protein"),
            ratioFiltered
                .visibleFoodGroups
                .map { it.name },
        )
    }

    @Test
    fun invalidFilterProducesNoResults(): Unit {
        val state =
            editor(
                listOf(option())
            ).copy(
                minimumProteinText =
                    "invalid"
            )

        assertFalse(state.filtersValid)

        assertTrue(
            state.visibleFoodGroups
                .isEmpty()
        )
    }

    @Test
    fun sortOptionsChangeGroupOrder(): Unit {
        val options =
            listOf(
                option(
                    id = 1L,
                    name = "Beta",
                    createdAt = 100L,
                    calories = 300.0,
                    protein = 10.0,
                ),
                option(
                    id = 2L,
                    name = "Alpha",
                    createdAt = 200L,
                    calories = 100.0,
                    protein = 30.0,
                ),
            )

        val byName =
            editor(options).copy(
                itemSort =
                    NutritionItemSort.NAME
            )

        assertEquals(
            listOf("Alpha", "Beta"),
            byName.visibleFoodGroups
                .map { it.name },
        )

        val byCalories =
            editor(options).copy(
                itemSort =
                    NutritionItemSort
                        .CALORIES
            )

        assertEquals(
            listOf("Beta", "Alpha"),
            byCalories.visibleFoodGroups
                .map { it.name },
        )

        val byProtein =
            editor(options).copy(
                itemSort =
                    NutritionItemSort
                        .PROTEIN
            )

        assertEquals(
            listOf("Alpha", "Beta"),
            byProtein.visibleFoodGroups
                .map { it.name },
        )
    }

    @Test
    fun versionChoicesIncludeAllEligibleVersions(): Unit {
        val state =
            editor(
                listOf(
                    option(
                        id = 2L,
                        name = "Milk",
                        version = 1,
                    ),
                    option(
                        id = 1L,
                        name = "Milk",
                        version = 0,
                    ),
                )
            )
                .copy(
                    versionGroupNameKey =
                        "milk"
                )

        assertEquals(
            listOf(0, 1),
            state.versionChoices
                .map { it.version },
        )
    }

    private fun editor(
        options:
        List<NutritionItemOptionUiState>,
        search: String = "",
    ): NutritionLogEditorUiState =
        NutritionLogEditorUiState(
            date = TEST_DATE,
            itemOptions = options,
            itemSearch = search,
            time = LocalTime.NOON,
        )

    private fun option(
        id: Long = 1L,
        name: String = "Test",
        version: Int = 0,
        versionLabel: String? = null,
        calories: Double = 100.0,
        protein: Double = 10.0,
        createdAt: Long = 1_000L,
        lastConsumedAt: Long? = null,
    ): NutritionItemOptionUiState =
        NutritionItemOptionUiState(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            version = version,
            versionLabel = versionLabel,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            createdAtEpochMillis =
                createdAt,
            lastConsumedAtEpochMillis =
                lastConsumedAt,
            isArchived = false,
        )

    private companion object {
        val TEST_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                30,
            )
    }
}