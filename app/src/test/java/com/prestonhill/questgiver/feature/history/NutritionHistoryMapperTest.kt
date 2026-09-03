package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.repository.NutritionDaySummary
import com.prestonhill.questgiver.data.repository.NutritionLogEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionHistoryMapperTest {
    private val mapper =
        NutritionHistoryMapper()

    private val zone =
        ZoneId.of("America/Chicago")

    private val calculator =
        AppDayCalculator(
            dayBoundary =
                LocalTime.MIDNIGHT,
            zoneId = zone,
        )

    @Test
    fun rangePresetsResolveDates(): Unit {
        val custom =
            defaultNutritionCustomRange(
                CURRENT_DATE
            )

        assertEquals(
            NutritionHistoryDateRange(
                startDate =
                    LocalDate.of(
                        2026,
                        8,
                        27,
                    ),
                endDate = CURRENT_DATE,
            ),
            NutritionHistoryRangePreset
                .SEVEN_DAYS
                .dateRange(
                    currentDate =
                        CURRENT_DATE,
                    customRange = custom,
                ),
        )

        assertEquals(
            NutritionHistoryDateRange(
                startDate =
                    LocalDate.of(
                        2026,
                        8,
                        4,
                    ),
                endDate = CURRENT_DATE,
            ),
            NutritionHistoryRangePreset
                .THIRTY_DAYS
                .dateRange(
                    currentDate =
                        CURRENT_DATE,
                    customRange = custom,
                ),
        )

        assertEquals(
            NutritionHistoryDateRange(
                startDate =
                    LocalDate.of(
                        2025,
                        9,
                        3,
                    ),
                endDate = CURRENT_DATE,
            ),
            NutritionHistoryRangePreset
                .ONE_YEAR
                .dateRange(
                    currentDate =
                        CURRENT_DATE,
                    customRange = custom,
                ),
        )
    }

    @Test
    fun customRangeDefaultsToPreviousMonth(): Unit {
        assertEquals(
            NutritionHistoryDateRange(
                startDate =
                    LocalDate.of(
                        2026,
                        8,
                        1,
                    ),
                endDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            ),
            defaultNutritionCustomRange(
                CURRENT_DATE
            ),
        )
    }

    @Test
    fun mapsStatisticsAndGoalCompletion(): Unit {
        val firstDate =
            CURRENT_DATE.minusDays(2)

        val secondDate =
            CURRENT_DATE.minusDays(1)

        val range =
            NutritionHistoryDateRange(
                startDate = firstDate,
                endDate = CURRENT_DATE,
            )

        val state =
            map(
                entries =
                    listOf(
                        entry(
                            id = 1L,
                            date = firstDate,
                            calories = 1_500.0,
                            protein = 40.0,
                        ),
                        entry(
                            id = 2L,
                            date = secondDate,
                            calories = 2_201.0,
                            protein = 160.0,
                        ),
                    ),
                customRange = range,
                settings =
                    AppSettings(
                        calorieGoal =
                            1_500.0,
                        maximumCalorieGoal =
                            2_200.0,
                        proteinGoalGrams =
                            40.0,
                        maximumProteinGoalGrams =
                            160.0,
                    ),
            )

        assertEquals(
            3,
            state.selectedDays.size,
        )

        assertFalse(
            state.selectedDays
                .single {
                    it.date == CURRENT_DATE
                }
                .hasLogs
        )

        assertEquals(
            2,
            state.calorieStatistics
                .loggedDays,
        )

        assertEquals(
            1_850.5,
            requireNotNull(
                state.calorieStatistics.average
            ),
            TOLERANCE,
        )

        assertEquals(
            1_500.0,
            requireNotNull(
                state.calorieStatistics
                    .minimumNonZero
            ),
            TOLERANCE,
        )

        assertEquals(
            2_201.0,
            requireNotNull(
                state.calorieStatistics.maximum
            ),
            TOLERANCE,
        )

        assertEquals(
            100.0,
            requireNotNull(
                state.proteinStatistics.average
            ),
            TOLERANCE,
        )

        val firstDay =
            state.selectedDays.single {
                it.date == firstDate
            }

        assertTrue(firstDay.calorieGoalMet)
        assertTrue(firstDay.proteinGoalMet)

        val secondDay =
            state.selectedDays.single {
                it.date == secondDate
            }

        assertFalse(
            secondDay.calorieGoalMet
        )

        assertTrue(
            secondDay.proteinGoalMet
        )

        assertEquals(
            1,
            state.customRangeCalories
                .metDays,
        )

        assertEquals(
            3,
            state.customRangeCalories
                .totalDays,
        )

        assertEquals(
            1f / 3f,
            state.customRangeCalories
                .progress,
            FLOAT_TOLERANCE,
        )

        assertEquals(
            2,
            state.customRangeProtein
                .metDays,
        )

        assertEquals(
            3,
            state.customRangeProtein
                .totalDays,
        )

        assertEquals(
            1_500.0,
            state.calorieGoal,
            TOLERANCE,
        )

        assertEquals(
            2_200.0,
            requireNotNull(
                state.maximumCalorieGoal
            ),
            TOLERANCE,
        )

        assertEquals(
            40.0,
            state.proteinGoalGrams,
            TOLERANCE,
        )

        assertEquals(
            160.0,
            requireNotNull(
                state.maximumProteinGoalGrams
            ),
            TOLERANCE,
        )
    }

    @Test
    fun statisticsExcludeUnloggedAndNonzeroMinimumExcludesZero(): Unit {
        val firstDate =
            CURRENT_DATE.minusDays(1)

        val range =
            NutritionHistoryDateRange(
                startDate = firstDate,
                endDate = CURRENT_DATE,
            )

        val state =
            map(
                entries =
                    listOf(
                        entry(
                            id = 1L,
                            date = firstDate,
                            calories = 0.0,
                            protein = 0.0,
                        ),
                        entry(
                            id = 2L,
                            date = CURRENT_DATE,
                            calories = 500.0,
                            protein = 10.0,
                        ),
                    ),
                customRange = range,
            )

        assertEquals(
            250.0,
            requireNotNull(
                state.calorieStatistics.average
            ),
            TOLERANCE,
        )

        assertEquals(
            500.0,
            requireNotNull(
                state.calorieStatistics
                    .minimumNonZero
            ),
            TOLERANCE,
        )

        assertEquals(
            500.0,
            requireNotNull(
                state.calorieStatistics.maximum
            ),
            TOLERANCE,
        )

        assertEquals(
            5.0,
            requireNotNull(
                state.proteinStatistics.average
            ),
            TOLERANCE,
        )

        assertEquals(
            10.0,
            requireNotNull(
                state.proteinStatistics
                    .minimumNonZero
            ),
            TOLERANCE,
        )
    }

    @Test
    fun emptyRangeHasNoStatistics(): Unit {
        val state =
            map(
                entries = emptyList(),
                customRange =
                    NutritionHistoryDateRange(
                        startDate =
                            CURRENT_DATE
                                .minusDays(2),
                        endDate =
                            CURRENT_DATE,
                    ),
            )

        assertNull(
            state.calorieStatistics.average
        )

        assertNull(
            state.calorieStatistics
                .minimumNonZero
        )

        assertNull(
            state.calorieStatistics.maximum
        )

        assertEquals(
            0,
            state.customRangeCalories
                .metDays,
        )

        assertEquals(
            3,
            state.customRangeCalories
                .totalDays,
        )

        assertEquals(
            0f,
            state.customRangeCalories
                .progress,
            FLOAT_TOLERANCE,
        )
    }

    @Test
    fun appDayBoundaryControlsGrouping(): Unit {
        val boundaryCalculator =
            AppDayCalculator(
                dayBoundary =
                    LocalTime.of(4, 0),
                zoneId = zone,
            )

        val timestamp =
            boundaryCalculator.timestampFor(
                appDate = CURRENT_DATE,
                time = LocalTime.of(2, 0),
            )

        val state =
            map(
                entries =
                    listOf(
                        entry(
                            id = 1L,
                            timestampMillis =
                                timestamp,
                            calories = 1_500.0,
                            protein = 40.0,
                        )
                    ),
                customRange =
                    NutritionHistoryDateRange(
                        startDate =
                            CURRENT_DATE,
                        endDate =
                            CURRENT_DATE,
                    ),
                calculator =
                    boundaryCalculator,
            )

        val day =
            state.selectedDays.single()

        assertEquals(
            CURRENT_DATE,
            day.date,
        )

        assertTrue(day.hasLogs)
        assertTrue(day.calorieGoalMet)
        assertTrue(day.proteinGoalMet)
    }

    @Test
    fun calendarIncludesFutureDatesWithoutGoals(): Unit {
        val state =
            map(
                entries = emptyList(),
                customRange =
                    NutritionHistoryDateRange(
                        startDate =
                            CURRENT_DATE,
                        endDate =
                            CURRENT_DATE,
                    ),
            )

        assertEquals(
            30,
            state.calendarDays.size,
        )

        val future =
            state.calendarDays.single {
                it.date ==
                        CURRENT_DATE.plusDays(1)
            }

        assertTrue(future.isFuture)

        assertFalse(
            future.calorieGoalMet
        )

        assertFalse(
            future.proteinGoalMet
        )

        assertEquals(
            CURRENT_DATE.dayOfMonth,
            state.currentMonthCalories
                .totalDays,
        )
    }

    private fun map(
        entries: List<NutritionLogEntry>,
        customRange:
        NutritionHistoryDateRange,
        calculator:
        AppDayCalculator = this.calculator,
        settings:
        AppSettings = AppSettings(),
    ): NutritionHistoryUiState =
        mapper.map(
            summary =
                NutritionDaySummary(
                    entries = entries,
                    totalCalories =
                        entries.sumOf {
                            it.calories
                        },
                    totalProteinGrams =
                        entries.sumOf {
                            it.proteinGrams
                        },
                ),
            rangePreset =
                NutritionHistoryRangePreset
                    .CUSTOM,
            customRange = customRange,
            calendarMonth =
                YearMonth.from(
                    CURRENT_DATE
                ),
            currentDate = CURRENT_DATE,
            calculator = calculator,
            settings = settings,
        )

    private fun entry(
        id: Long,
        date: LocalDate,
        calories: Double,
        protein: Double,
    ): NutritionLogEntry =
        entry(
            id = id,
            timestampMillis =
                date.atTime(12, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
            calories = calories,
            protein = protein,
        )

    private fun entry(
        id: Long,
        timestampMillis: Long,
        calories: Double,
        protein: Double,
    ): NutritionLogEntry =
        NutritionLogEntry(
            log =
                FoodLogEntity(
                    id = id,
                    itemId = id,
                    consumedAtEpochMillis =
                        timestampMillis,
                    weightGrams = 100.0,
                    createdAtEpochMillis =
                        timestampMillis,
                    updatedAtEpochMillis =
                        timestampMillis,
                ),
            item =
                NutritionItemEntity(
                    id = id,
                    name = "Food $id",
                    nameKey = "food_$id",
                    caloriesPer100g =
                        calories,
                    proteinPer100g =
                        protein,
                    createdAtEpochMillis =
                        timestampMillis,
                    updatedAtEpochMillis =
                        timestampMillis,
                ),
            calories = calories,
            proteinGrams = protein,
        )

    private companion object {
        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                9,
                2,
            )

        const val TOLERANCE =
            0.0001

        const val FLOAT_TOLERANCE =
            0.0001f
    }
}