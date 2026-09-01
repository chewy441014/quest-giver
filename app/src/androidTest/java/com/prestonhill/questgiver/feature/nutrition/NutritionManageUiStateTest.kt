package com.prestonhill.questgiver.feature.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionManageUiStateTest {
    @Test
    fun defaultViewShowsAllActiveItems(): Unit {
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
                    ) +
                    option(
                        id = 200L,
                        name = "Archived",
                        archived = true,
                    )

        val state =
            NutritionManageUiState(
                itemOptions = options
            )

        assertEquals(
            13,
            state.visibleFoodGroups.size,
        )

        assertEquals(
            "Recently eaten",
            state.visibleFoodGroups
                .first()
                .name,
        )

        assertTrue(
            state.visibleFoodGroups.none {
                it.name == "Archived"
            }
        )
    }

    @Test
    fun archiveFilterControlsVisibleItems(): Unit {
        val state =
            NutritionManageUiState(
                itemOptions =
                    listOf(
                        option(
                            id = 1L,
                            name = "Active",
                        ),
                        option(
                            id = 2L,
                            name = "Archived",
                            archived = true,
                        ),
                    )
            )

        assertEquals(
            listOf("Active"),
            state.visibleFoodGroups
                .map { it.name },
        )

        val archived =
            state.copy(
                archiveFilter =
                    NutritionArchiveFilter
                        .ARCHIVED
            )

        assertEquals(
            listOf("Archived"),
            archived.visibleFoodGroups
                .map { it.name },
        )

        val all =
            state.copy(
                archiveFilter =
                    NutritionArchiveFilter.ALL
            )

        assertEquals(
            setOf("Active", "Archived"),
            all.visibleFoodGroups
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun searchSortAndProteinFiltersApply(): Unit {
        val state =
            NutritionManageUiState(
                itemOptions =
                    listOf(
                        option(
                            id = 1L,
                            name = "Beta",
                            calories = 300.0,
                            protein = 10.0,
                        ),
                        option(
                            id = 2L,
                            name = "Alpha",
                            calories = 100.0,
                            protein = 30.0,
                        ),
                    ),
                itemSort =
                    NutritionItemSort.NAME,
                minimumProteinText = "20",
            )

        assertTrue(state.filtersValid)

        assertEquals(
            listOf("Alpha"),
            state.visibleFoodGroups
                .map { it.name },
        )

        val searched =
            state.copy(
                minimumProteinText = "",
                itemSearch = "Beta",
            )

        assertEquals(
            listOf("Beta"),
            searched.visibleFoodGroups
                .map { it.name },
        )

        val invalid =
            state.copy(
                minimumProteinText =
                    "invalid"
            )

        assertFalse(invalid.filtersValid)

        assertTrue(
            invalid.visibleFoodGroups
                .isEmpty()
        )
    }

    private fun option(
        id: Long,
        name: String,
        calories: Double = 100.0,
        protein: Double = 10.0,
        createdAt: Long = 1_000L,
        lastConsumedAt: Long? = null,
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
                lastConsumedAt,
            isArchived = archived,
        )
}