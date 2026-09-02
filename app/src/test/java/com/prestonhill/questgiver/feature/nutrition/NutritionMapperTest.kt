package com.prestonhill.questgiver.feature.nutrition

import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.repository.NutritionDaySummary
import com.prestonhill.questgiver.data.repository.NutritionLogEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionUiMapperTest {
    private val mapper =
        NutritionUiMapper()

    private val zone =
        ZoneId.of("America/Chicago")

    private val dayCalculator =
        AppDayCalculator(
            dayBoundary =
                LocalTime.MIDNIGHT,
            zoneId = zone,
        )

    @Test
    fun mapsNutritionDay(): Unit {
        val later =
            entry(
                logId = 1L,
                itemId = 10L,
                name = "Chicken",
                version = 1,
                versionLabel = "Brand A",
                time = LocalTime.of(18, 30),
                weightGrams = 200.0,
                calories = 500.0,
                proteinGrams = 60.0,
                archived = true,
            )

        val earlier =
            entry(
                logId = 2L,
                itemId = 20L,
                name = "Oats",
                time = LocalTime.of(8, 15),
                weightGrams = 100.0,
                calories = 250.0,
                proteinGrams = 10.0,
            )

        val state =
            mapper.map(
                summary =
                    NutritionDaySummary(
                        entries =
                            listOf(
                                later,
                                earlier,
                            ),
                        totalCalories =
                            750.0,
                        totalProteinGrams =
                            70.0,
                    ),
                selectedDay =
                    dayCalculator.forDate(
                        SELECTED_DATE
                    ),
                currentDay =
                    dayCalculator.forDate(
                        CURRENT_DATE
                    ),
                zoneId = zone,
                settings =
                    AppSettings(
                        calorieGoal =
                            1_500.0,
                        proteinGoalGrams =
                            140.0,
                    ),
                showDatePicker = true,
                operationError =
                    "Test error",
            )

        assertEquals(
            SELECTED_DATE,
            state.selectedDate,
        )

        assertEquals(
            CURRENT_DATE,
            state.currentDate,
        )

        assertEquals(
            NutritionGoalStatus.BELOW_MINIMUM,
            state.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus.BELOW_MINIMUM,
            state.proteinGoalStatus,
        )

        assertFalse(state.isCurrentDay)
        assertTrue(state.canSelectNextDay)
        assertTrue(state.showDatePicker)
        assertFalse(state.isLoading)

        assertEquals(
            "Test error",
            state.operationError,
        )

        assertEquals(
            750.0,
            state.totalCalories,
            TOLERANCE,
        )

        assertEquals(
            70.0,
            state.totalProteinGrams,
            TOLERANCE,
        )

        assertEquals(
            0.5f,
            state.calorieProgress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            0.5f,
            state.proteinProgress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            listOf(2L, 1L),
            state.logs.map { it.logId },
        )

        val archived =
            state.logs.single {
                it.logId == 1L
            }

        assertEquals(
            "Chicken",
            archived.itemName,
        )

        assertEquals(
            1,
            archived.itemVersion,
        )

        assertEquals(
            "Brand A",
            archived.versionLabel,
        )

        assertEquals(
            LocalTime.of(18, 30),
            archived.consumedTime,
        )

        assertEquals(
            200.0,
            archived.weightGrams,
            TOLERANCE,
        )

        assertTrue(
            archived.isItemArchived
        )
    }

    @Test
    fun currentDayCannotAdvance(): Unit {
        val day =
            dayCalculator.forDate(
                CURRENT_DATE
            )

        val state =
            mapper.map(
                summary =
                    NutritionDaySummary(
                        entries = emptyList(),
                        totalCalories =
                            3_000.0,
                        totalProteinGrams =
                            100.0,
                    ),
                selectedDay = day,
                currentDay = day,
                zoneId = zone,
                settings =
                    AppSettings(
                        calorieGoal =
                            1_500.0,
                        proteinGoalGrams =
                            40.0,
                    ),
            )

        assertTrue(state.isCurrentDay)

        assertFalse(
            state.canSelectNextDay
        )

        assertEquals(
            1f,
            state.calorieProgress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            1f,
            state.proteinProgress,
            FLOAT_TOLERANCE,
        )
    }

    @Test
    fun goalRangeBoundariesAreInclusive(): Unit {
        val lower =
            mapTotals(
                calories = 1_500.0,
                protein = 40.0,
            )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            lower.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            lower.proteinGoalStatus,
        )

        val upper =
            mapTotals(
                calories = 2_200.0,
                protein = 160.0,
            )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            upper.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            upper.proteinGoalStatus,
        )
    }

    @Test
    fun totalsOutsideRangesAreIdentified(): Unit {
        val below =
            mapTotals(
                calories = 1_499.0,
                protein = 39.0,
            )

        assertEquals(
            NutritionGoalStatus
                .BELOW_MINIMUM,
            below.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus
                .BELOW_MINIMUM,
            below.proteinGoalStatus,
        )

        val above =
            mapTotals(
                calories = 2_201.0,
                protein = 161.0,
            )

        assertEquals(
            NutritionGoalStatus
                .ABOVE_MAXIMUM,
            above.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus
                .ABOVE_MAXIMUM,
            above.proteinGoalStatus,
        )
    }

    @Test
    fun minimumOnlyGoalHasNoUpperFailure(): Unit {
        val state =
            mapTotals(
                calories = 10_000.0,
                protein = 500.0,
                maximumCalories = null,
                maximumProtein = null,
            )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            state.calorieGoalStatus,
        )

        assertEquals(
            NutritionGoalStatus.WITHIN_GOAL,
            state.proteinGoalStatus,
        )

        assertEquals(
            1f,
            state.calorieProgress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            1f,
            state.proteinProgress,
            FLOAT_TOLERANCE,
        )
    }

    @Test
    fun emptyDayHasZeroProgress(): Unit {
        val state =
            mapper.map(
                summary =
                    NutritionDaySummary(
                        entries = emptyList(),
                        totalCalories = 0.0,
                        totalProteinGrams =
                            0.0,
                    ),
                selectedDay =
                    dayCalculator.forDate(
                        CURRENT_DATE
                    ),
                currentDay =
                    dayCalculator.forDate(
                        CURRENT_DATE
                    ),
                zoneId = zone,
                settings = AppSettings(),
            )

        assertTrue(state.logs.isEmpty())

        assertEquals(
            0f,
            state.calorieProgress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            0f,
            state.proteinProgress,
            FLOAT_TOLERANCE,
        )
    }

    private fun entry(
        logId: Long,
        itemId: Long,
        name: String,
        version: Int = 0,
        versionLabel: String? = null,
        time: LocalTime,
        weightGrams: Double,
        calories: Double,
        proteinGrams: Double,
        archived: Boolean = false,
    ): NutritionLogEntry {
        val consumedAt =
            SELECTED_DATE
                .atTime(time)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        return NutritionLogEntry(
            log =
                FoodLogEntity(
                    id = logId,
                    itemId = itemId,
                    consumedAtEpochMillis =
                        consumedAt,
                    weightGrams =
                        weightGrams,
                    createdAtEpochMillis =
                        consumedAt,
                    updatedAtEpochMillis =
                        consumedAt,
                ),
            item =
                NutritionItemEntity(
                    id = itemId,
                    name = name,
                    nameKey =
                        name.lowercase(),
                    version = version,
                    versionLabel =
                        versionLabel,
                    caloriesPer100g =
                        100.0,
                    proteinPer100g =
                        10.0,
                    createdAtEpochMillis =
                        consumedAt,
                    updatedAtEpochMillis =
                        consumedAt,
                    archivedAtEpochMillis =
                        if (archived) {
                            consumedAt
                        } else {
                            null
                        },
                ),
            calories = calories,
            proteinGrams =
                proteinGrams,
        )
    }

    private companion object {
        val SELECTED_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                29,
            )

        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                30,
            )

        const val TOLERANCE =
            0.000_001

        const val FLOAT_TOLERANCE =
            0.000_001f
    }

    private fun mapTotals(
        calories: Double,
        protein: Double,
        minimumCalories: Double =
            1_500.0,
        maximumCalories: Double? =
            2_200.0,
        minimumProtein: Double =
            40.0,
        maximumProtein: Double? =
            160.0,
    ): NutritionScreenUiState =
        mapper.map(
            summary =
                NutritionDaySummary(
                    entries = emptyList(),
                    totalCalories = calories,
                    totalProteinGrams =
                        protein,
                ),
            selectedDay =
                dayCalculator.forDate(
                    CURRENT_DATE
                ),
            currentDay =
                dayCalculator.forDate(
                    CURRENT_DATE
                ),
            zoneId = zone,
            settings =
                AppSettings(
                    calorieGoal =
                        minimumCalories,
                    maximumCalorieGoal =
                        maximumCalories,
                    proteinGoalGrams =
                        minimumProtein,
                    maximumProteinGoalGrams =
                        maximumProtein,
                ),
        )
}