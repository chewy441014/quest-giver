package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.repository.NutritionDaySummary
import java.time.LocalDate
import java.time.YearMonth

class NutritionHistoryMapper {
    fun map(
        summary: NutritionDaySummary,
        rangePreset:
        NutritionHistoryRangePreset,
        customRange:
        NutritionHistoryDateRange,
        calendarMonth: YearMonth,
        currentDate: LocalDate,
        calculator: AppDayCalculator,
        settings: AppSettings,
    ): NutritionHistoryUiState {
        require(
            !customRange.endDate
                .isAfter(currentDate)
        )

        require(
            !calendarMonth.isAfter(
                YearMonth.from(currentDate)
            )
        )

        val selectedRange =
            rangePreset.dateRange(
                currentDate = currentDate,
                customRange = customRange,
            )

        val entriesByDate =
            summary.entries.groupBy { entry ->
                calculator.containing(
                    entry.log
                        .consumedAtEpochMillis
                ).date
            }

        val selectedDays =
            mapDays(
                range = selectedRange,
                entriesByDate = entriesByDate,
                currentDate = currentDate,
                settings = settings,
            )

        val currentMonthRange =
            NutritionHistoryDateRange(
                startDate =
                    currentDate
                        .withDayOfMonth(1),
                endDate = currentDate,
            )

        val currentMonthDays =
            mapDays(
                range = currentMonthRange,
                entriesByDate = entriesByDate,
                currentDate = currentDate,
                settings = settings,
            )

        val customDays =
            mapDays(
                range = customRange,
                entriesByDate = entriesByDate,
                currentDate = currentDate,
                settings = settings,
            )

        val calendarRange =
            NutritionHistoryDateRange(
                startDate =
                    calendarMonth.atDay(1),
                endDate =
                    calendarMonth
                        .atEndOfMonth(),
            )

        val calendarDays =
            mapDays(
                range = calendarRange,
                entriesByDate = entriesByDate,
                currentDate = currentDate,
                settings = settings,
            )

        return NutritionHistoryUiState(
            rangePreset = rangePreset,
            selectedRange = selectedRange,
            customRange = customRange,
            selectedDays = selectedDays,
            currentDate = currentDate,
            calorieStatistics =
                statistics(
                    days = selectedDays,
                    value = {
                        it.calories
                    },
                ),
            proteinStatistics =
                statistics(
                    days = selectedDays,
                    value = {
                        it.proteinGrams
                    },
                ),
            currentMonthCalories =
                completion(
                    days =
                        currentMonthDays,
                    met = {
                        it.calorieGoalMet
                    },
                ),
            customRangeCalories =
                completion(
                    days = customDays,
                    met = {
                        it.calorieGoalMet
                    },
                ),
            currentMonthProtein =
                completion(
                    days =
                        currentMonthDays,
                    met = {
                        it.proteinGoalMet
                    },
                ),
            customRangeProtein =
                completion(
                    days = customDays,
                    met = {
                        it.proteinGoalMet
                    },
                ),
            calendarMonth = calendarMonth,
            calendarDays = calendarDays,
            calorieGoal =
                settings.calorieGoal,

            maximumCalorieGoal =
                settings.maximumCalorieGoal,

            proteinGoalGrams =
                settings.proteinGoalGrams,

            maximumProteinGoalGrams =
                settings.maximumProteinGoalGrams,
        )
    }

    private fun mapDays(
        range: NutritionHistoryDateRange,
        entriesByDate:
        Map<
                LocalDate,
                List<
                        com.prestonhill.questgiver
                        .data.repository
                        .NutritionLogEntry
                        >
                >,
        currentDate: LocalDate,
        settings: AppSettings,
    ): List<NutritionHistoryDayUiState> =
        dates(range).map { date ->
            val entries =
                entriesByDate[date]
                    .orEmpty()

            val calories =
                entries.sumOf {
                    it.calories
                }

            val protein =
                entries.sumOf {
                    it.proteinGrams
                }

            val hasLogs =
                entries.isNotEmpty()

            val isFuture =
                date.isAfter(currentDate)

            NutritionHistoryDayUiState(
                date = date,
                calories = calories,
                proteinGrams = protein,
                hasLogs = hasLogs,
                calorieGoalMet =
                    !isFuture &&
                            hasLogs &&
                            goalMet(
                                value = calories,
                                minimum =
                                    settings
                                        .calorieGoal,
                                maximum =
                                    settings
                                        .maximumCalorieGoal,
                            ),
                proteinGoalMet =
                    !isFuture &&
                            hasLogs &&
                            goalMet(
                                value = protein,
                                minimum =
                                    settings
                                        .proteinGoalGrams,
                                maximum =
                                    settings
                                        .maximumProteinGoalGrams,
                            ),
                isFuture = isFuture,
            )
        }

    private fun statistics(
        days:
        List<NutritionHistoryDayUiState>,
        value:
            (NutritionHistoryDayUiState) ->
        Double,
    ): NutritionHistoryMetricUiState {
        val loggedValues =
            days
                .filter {
                    it.hasLogs
                }
                .map(value)

        if (loggedValues.isEmpty()) {
            return NutritionHistoryMetricUiState()
        }

        return NutritionHistoryMetricUiState(
            loggedDays = loggedValues.size,
            average =
                loggedValues.average(),
            minimumNonZero =
                loggedValues
                    .filter {
                        it > 0.0
                    }
                    .minOrNull(),
            maximum =
                loggedValues.maxOrNull(),
        )
    }

    private fun completion(
        days:
        List<NutritionHistoryDayUiState>,
        met:
            (NutritionHistoryDayUiState) ->
        Boolean,
    ): NutritionGoalCompletionUiState {
        val totalDays =
            days.count {
                !it.isFuture
            }

        val metDays =
            days.count {
                !it.isFuture &&
                        met(it)
            }

        return NutritionGoalCompletionUiState(
            metDays = metDays,
            totalDays = totalDays,
            progress =
                if (totalDays == 0) {
                    0f
                } else {
                    (
                            metDays.toFloat() /
                                    totalDays
                            )
                        .coerceIn(0f, 1f)
                },
        )
    }

    private fun goalMet(
        value: Double,
        minimum: Double,
        maximum: Double?,
    ): Boolean =
        value >= minimum &&
                (
                        maximum == null ||
                                value <= maximum
                        )

    private fun dates(
        range: NutritionHistoryDateRange,
    ): List<LocalDate> =
        generateSequence(
            range.startDate
        ) { date ->
            date.plusDays(1)
                .takeUnless {
                    it.isAfter(
                        range.endDate
                    )
                }
        }
            .toList()
}

fun NutritionHistoryRangePreset.dateRange(
    currentDate: LocalDate,
    customRange:
    NutritionHistoryDateRange,
): NutritionHistoryDateRange =
    when (this) {
        NutritionHistoryRangePreset
            .SEVEN_DAYS ->
            trailingRange(
                currentDate = currentDate,
                days = 7,
            )

        NutritionHistoryRangePreset
            .THIRTY_DAYS ->
            trailingRange(
                currentDate = currentDate,
                days = 30,
            )

        NutritionHistoryRangePreset
            .NINETY_DAYS ->
            trailingRange(
                currentDate = currentDate,
                days = 90,
            )

        NutritionHistoryRangePreset
            .ONE_YEAR ->
            NutritionHistoryDateRange(
                startDate =
                    currentDate
                        .minusYears(1)
                        .plusDays(1),
                endDate = currentDate,
            )

        NutritionHistoryRangePreset.CUSTOM ->
            customRange
    }

fun defaultNutritionCustomRange(
    currentDate: LocalDate,
): NutritionHistoryDateRange {
    val currentMonth =
        YearMonth.from(currentDate)

    val previousMonth =
        currentMonth.minusMonths(1)

    return NutritionHistoryDateRange(
        startDate =
            previousMonth.atDay(1),
        endDate =
            previousMonth
                .atEndOfMonth(),
    )
}

private fun trailingRange(
    currentDate: LocalDate,
    days: Long,
): NutritionHistoryDateRange =
    NutritionHistoryDateRange(
        startDate =
            currentDate.minusDays(
                days - 1L
            ),
        endDate = currentDate,
    )