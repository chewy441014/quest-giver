package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.feature.habits.HabitHistoryDateRange
import com.prestonhill.questgiver.feature.habits.HabitHistoryPerformanceUiState
import com.prestonhill.questgiver.feature.habits.HabitHistoryRangePreset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class HabitHistoryMapper {
    fun stampCalendar(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        month: YearMonth,
        currentDate: LocalDate,
        weekStart: DayOfWeek,
        calculator: AppDayCalculator,
        showArchivedHabits: Boolean,
    ): HistoryStampCalendarUiState {
        val eligibleHabits =
            habits.filter { habit ->
                habit.isVisibleInHistory &&
                        (
                                habit.archivedAtEpochMillis !=
                                        null
                                ) ==
                        showArchivedHabits
            }

        val habitsById =
            eligibleHabits.associateBy {
                it.id
            }

        val uncategorizedFilters =
            eligibleHabits
                .filter {
                    it.cleanedHistoryCategory() ==
                            null
                }
                .sortedWith(
                    compareByDescending<HabitEntity> {
                        it.createdAtEpochMillis
                    }
                        .thenByDescending {
                            it.id
                        }
                )
                .map { habit ->
                    val key =
                        habitStampKey(habit.id)

                    HistoryStampFilterUiState(
                        key = key,
                        label = habit.name,
                        groupLabel =
                            UNCATEGORIZED_HABIT_GROUP,
                        colors =
                            historyStampColors(key),
                    )
                }

        val categoryLabels =
            linkedMapOf<String, String>()

        eligibleHabits
            .sortedWith(
                compareByDescending<HabitEntity> {
                    it.createdAtEpochMillis
                }
                    .thenByDescending {
                        it.id
                    }
            )
            .forEach { habit ->
                habit.cleanedHistoryCategory()
                    ?.let { category ->
                        categoryLabels.putIfAbsent(
                            habitCategoryStampKey(
                                category
                            ),
                            category,
                        )
                    }
            }

        val categoryFilters =
            categoryLabels.entries
                .sortedBy {
                    it.value.lowercase(
                        Locale.ROOT
                    )
                }
                .map { (key, label) ->
                    HistoryStampFilterUiState(
                        key = key,
                        label = label,
                        groupLabel =
                            HABIT_CATEGORY_GROUP,
                        colors =
                            historyStampColors(key),
                    )
                }

        val filters =
            uncategorizedFilters +
                    categoryFilters

        val filterOrder =
            filters.mapIndexed {
                    index,
                    filter,
                ->
                filter.key to index
            }
                .toMap()

        val logsByDate =
            activeHabitLogs(logs)
                .filter {
                    it.habitId in habitsById
                }
                .groupBy { log ->
                    calculator
                        .containing(
                            log.completionTimestampMillis
                        )
                        .date
                }

        val days =
            (1..month.lengthOfMonth())
                .map { dayOfMonth ->
                    val date =
                        month.atDay(dayOfMonth)

                    val stampKeys =
                        logsByDate[date]
                            .orEmpty()
                            .mapNotNull { log ->
                                val habit =
                                    habitsById[
                                        log.habitId
                                    ]
                                        ?: return@mapNotNull null

                                habit.cleanedHistoryCategory()
                                    ?.let(
                                        ::habitCategoryStampKey
                                    )
                                    ?: habitStampKey(
                                        habit.id
                                    )
                            }
                            .distinct()
                            .sortedBy {
                                filterOrder[it]
                                    ?: Int.MAX_VALUE
                            }

                    HistoryStampCalendarDayUiState(
                        date = date,
                        stampKeys = stampKeys,
                        isFuture =
                            date.isAfter(
                                currentDate
                            ),
                    )
                }

        return HistoryStampCalendarUiState(
            month = month,
            currentDate = currentDate,
            weekStart = weekStart,
            availableFilters = filters,
            selectedFilterKeys =
                filters.mapTo(
                    linkedSetOf()
                ) {
                    it.key
                },
            days = days,
        )
    }

    fun performance(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        range: HabitHistoryDateRange,
        currentDate: LocalDate,
        calculator:
        HabitHistoryPerformanceCalculator,
        showArchivedHabits: Boolean,
    ): List<HabitHistoryPerformanceUiState> =
        habits.asSequence()
            .filter { habit ->
                habit.isVisibleInHistory &&
                        (
                                habit.archivedAtEpochMillis !=
                                        null
                                ) ==
                        showArchivedHabits
            }
            .sortedWith(
                compareByDescending<HabitEntity> {
                    it.createdAtEpochMillis
                }
                    .thenByDescending {
                        it.id
                    }
            )
            .map { habit ->
                val result =
                    calculator.calculate(
                        habit = habit,
                        logs = logs,
                        range = range,
                        currentDate = currentDate,
                    )

                HabitHistoryPerformanceUiState(
                    habitId = habit.id,
                    name = habit.name,
                    schedule =
                        habit.historyScheduleText(),
                    completedPeriods =
                        result.completedPeriods,
                    totalPeriods =
                        result.totalPeriods,
                    completionRate =
                        result.completionRate,
                )
            }
            .toList()
}

private fun activeHabitLogs(
    logs: List<HabitLogEntity>,
): List<HabitLogEntity> {
    val correctedIds =
        logs.asSequence()
            .filter {
                it.delta == -1
            }
            .mapNotNull {
                it.reversesLogId
            }
            .toSet()

    return logs.filter { log ->
        log.delta == 1 &&
                log.id !in correctedIds
    }
}

private fun HabitEntity.cleanedHistoryCategory():
        String? =
    historyCategory
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }

private fun habitStampKey(
    habitId: Long,
): String =
    "habit:$habitId"

private fun habitCategoryStampKey(
    category: String,
): String =
    "habit-category:" +
            category
                .trim()
                .lowercase(Locale.ROOT)

private const val UNCATEGORIZED_HABIT_GROUP =
    "Uncategorized habits"

private const val HABIT_CATEGORY_GROUP =
    "Habit categories"

fun HabitHistoryRangePreset.dateRange(
    currentDate: LocalDate,
    customRange: HabitHistoryDateRange,
): HabitHistoryDateRange =
    when (this) {
        HabitHistoryRangePreset
            .THIRTY_DAYS ->
            habitTrailingRange(
                currentDate = currentDate,
                days = 30,
            )

        HabitHistoryRangePreset
            .SIXTY_DAYS ->
            habitTrailingRange(
                currentDate = currentDate,
                days = 60,
            )

        HabitHistoryRangePreset
            .NINETY_DAYS ->
            habitTrailingRange(
                currentDate = currentDate,
                days = 90,
            )

        HabitHistoryRangePreset
            .SIX_MONTHS ->
            HabitHistoryDateRange(
                startDate =
                    currentDate
                        .minusMonths(6)
                        .plusDays(1),
                endDate = currentDate,
            )

        HabitHistoryRangePreset
            .ONE_YEAR ->
            HabitHistoryDateRange(
                startDate =
                    currentDate
                        .minusYears(1)
                        .plusDays(1),
                endDate = currentDate,
            )

        HabitHistoryRangePreset.CUSTOM ->
            customRange
    }

fun defaultHabitCustomRange(
    currentDate: LocalDate,
): HabitHistoryDateRange {
    val previousMonth =
        YearMonth.from(currentDate)
            .minusMonths(1)

    return HabitHistoryDateRange(
        startDate =
            previousMonth.atDay(1),
        endDate =
            previousMonth.atEndOfMonth(),
    )
}

private fun HabitEntity.historyScheduleText():
        String =
    when (scheduleType) {
        HabitScheduleTypeDb.DAILY ->
            if (scheduleTarget == 1) {
                "Daily"
            } else {
                "$scheduleTarget per day"
            }

        HabitScheduleTypeDb.WEEKLY_TARGET ->
            if (scheduleTarget == 1) {
                "Once per week"
            } else {
                "$scheduleTarget per week"
            }

        HabitScheduleTypeDb.INTERVAL -> {
            val days =
                intervalDays ?: 1

            when (intervalBasis) {
                HabitIntervalBasisDb
                    .FROM_COMPLETION ->
                    if (days == 1) {
                        "Daily after completion"
                    } else {
                        "Every $days days after completion"
                    }

                HabitIntervalBasisDb
                    .FIXED_SCHEDULE,
                null,
                    ->
                    if (scheduleTarget == 1) {
                        if (days == 1) {
                            "Daily"
                        } else {
                            "Every $days days"
                        }
                    } else {
                        "$scheduleTarget every " +
                                "$days days"
                    }
            }
        }
    }

private fun habitTrailingRange(
    currentDate: LocalDate,
    days: Long,
): HabitHistoryDateRange =
    HabitHistoryDateRange(
        startDate =
            currentDate.minusDays(
                days - 1L
            ),
        endDate = currentDate,
    )