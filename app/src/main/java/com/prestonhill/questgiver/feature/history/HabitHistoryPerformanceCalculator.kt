package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import java.time.DayOfWeek
import java.time.LocalDate

data class HabitHistoryPerformanceResult(
    val completedPeriods: Int,
    val totalPeriods: Int,
) {
    val completionRate: Float
        get() =
            if (totalPeriods == 0) {
                0f
            } else {
                (
                        completedPeriods.toFloat() /
                                totalPeriods
                        )
                    .coerceIn(0f, 1f)
            }
}

class HabitHistoryPerformanceCalculator(
    private val appDayCalculator:
    AppDayCalculator,
    private val weekStart: DayOfWeek,
) {
    fun calculate(
        habit: HabitEntity,
        logs: List<HabitLogEntity>,
        range: HabitHistoryDateRange,
        currentDate: LocalDate,
    ): HabitHistoryPerformanceResult {
        require(
            !range.endDate.isAfter(currentDate)
        )

        val createdDate =
            appDayCalculator
                .containing(
                    habit.createdAtEpochMillis
                )
                .date

        val cutoffExclusive =
            habit.archivedAtEpochMillis
                ?.let {
                    appDayCalculator
                        .containing(it)
                        .date
                        .plusDays(1)
                }
                ?.let { archiveCutoff ->
                    minOf(
                        archiveCutoff,
                        currentDate,
                    )
                }
                ?: currentDate

        val completionDates =
            activeCompletionDates(
                habitId = habit.id,
                logs = logs,
            )
                .filter {
                    !it.isBefore(createdDate) &&
                            it.isBefore(
                                cutoffExclusive
                            )
                }
                .sorted()

        val outcomes =
            when (habit.scheduleType) {
                HabitScheduleTypeDb.DAILY ->
                    dailyOutcomes(
                        habit = habit,
                        completionDates =
                            completionDates,
                        range = range,
                        createdDate =
                            createdDate,
                        cutoffExclusive =
                            cutoffExclusive,
                    )

                HabitScheduleTypeDb.WEEKLY_TARGET ->
                    weeklyOutcomes(
                        habit = habit,
                        completionDates =
                            completionDates,
                        range = range,
                        createdDate =
                            createdDate,
                        cutoffExclusive =
                            cutoffExclusive,
                    )

                HabitScheduleTypeDb.INTERVAL ->
                    intervalOutcomes(
                        habit = habit,
                        completionDates =
                            completionDates,
                        range = range,
                        createdDate =
                            createdDate,
                        cutoffExclusive =
                            cutoffExclusive,
                    )
            }

        return HabitHistoryPerformanceResult(
            completedPeriods =
                outcomes.count {
                    it.completed
                },
            totalPeriods = outcomes.size,
        )
    }

    private fun dailyOutcomes(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        range: HabitHistoryDateRange,
        createdDate: LocalDate,
        cutoffExclusive: LocalDate,
    ): List<PeriodOutcome> {
        val firstDate =
            maxOf(
                range.startDate,
                createdDate,
            )

        val finalDate =
            minOf(
                range.endDate,
                cutoffExclusive.minusDays(1),
            )

        if (firstDate.isAfter(finalDate)) {
            return emptyList()
        }

        val counts =
            completionDates
                .groupingBy {
                    it
                }
                .eachCount()

        return historyDates(
            start = firstDate,
            end = finalDate,
        )
            .map { date ->
                PeriodOutcome(
                    date = date,
                    completed =
                        counts.getOrDefault(
                            date,
                            0,
                        ) >=
                                habit.scheduleTarget,
                )
            }
    }

    private fun weeklyOutcomes(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        range: HabitHistoryDateRange,
        createdDate: LocalDate,
        cutoffExclusive: LocalDate,
    ): List<PeriodOutcome> {
        val outcomes =
            mutableListOf<PeriodOutcome>()

        var periodStart =
            weekStartFor(range.startDate)

        while (
            !periodStart.isAfter(
                range.endDate
            )
        ) {
            val endExclusive =
                periodStart.plusWeeks(1)

            val resultDate =
                endExclusive.minusDays(1)

            val isEligible =
                !resultDate.isBefore(
                    range.startDate
                ) &&
                        !resultDate.isAfter(
                            range.endDate
                        ) &&
                        !resultDate.isBefore(
                            createdDate
                        ) &&
                        !endExclusive.isAfter(
                            cutoffExclusive
                        )

            if (isEligible) {
                val countStart =
                    maxOf(
                        periodStart,
                        createdDate,
                    )

                val count =
                    completionDates.count {
                        !it.isBefore(countStart) &&
                                it.isBefore(
                                    endExclusive
                                )
                    }

                outcomes +=
                    PeriodOutcome(
                        date = resultDate,
                        completed =
                            count >=
                                    habit.scheduleTarget,
                    )
            }

            periodStart =
                periodStart.plusWeeks(1)
        }

        return outcomes
    }

    private fun intervalOutcomes(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        range: HabitHistoryDateRange,
        createdDate: LocalDate,
        cutoffExclusive: LocalDate,
    ): List<PeriodOutcome> {
        val intervalDays =
            requireNotNull(
                habit.intervalDays
            )

        return when (habit.intervalBasis) {
            HabitIntervalBasisDb.FIXED_SCHEDULE ->
                fixedIntervalOutcomes(
                    habit = habit,
                    completionDates =
                        completionDates,
                    range = range,
                    createdDate =
                        createdDate,
                    cutoffExclusive =
                        cutoffExclusive,
                    intervalDays =
                        intervalDays,
                )

            HabitIntervalBasisDb.FROM_COMPLETION ->
                completionIntervalOutcomes(
                    habit = habit,
                    completionDates =
                        completionDates,
                    range = range,
                    cutoffExclusive =
                        cutoffExclusive,
                    intervalDays =
                        intervalDays,
                )

            null ->
                error(
                    "Interval habit requires " +
                            "an interval basis."
                )
        }
    }

    private fun fixedIntervalOutcomes(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        range: HabitHistoryDateRange,
        createdDate: LocalDate,
        cutoffExclusive: LocalDate,
        intervalDays: Int,
    ): List<PeriodOutcome> {
        val anchor =
            habit.fixedScheduleAnchorEpochDay
                ?.let(LocalDate::ofEpochDay)
                ?: createdDate

        if (anchor.isAfter(range.endDate)) {
            return emptyList()
        }

        val approximateIndex =
            Math.floorDiv(
                range.startDate.toEpochDay() -
                        anchor.toEpochDay(),
                intervalDays.toLong(),
            )
                .coerceAtLeast(0L)

        val outcomes =
            mutableListOf<PeriodOutcome>()

        var index = approximateIndex

        while (true) {
            val periodStart =
                anchor.plusDays(
                    index *
                            intervalDays.toLong()
                )

            if (
                periodStart.isAfter(
                    range.endDate
                )
            ) {
                break
            }

            val endExclusive =
                periodStart.plusDays(
                    intervalDays.toLong()
                )

            val resultDate =
                endExclusive.minusDays(1)

            val isEligible =
                !resultDate.isBefore(
                    range.startDate
                ) &&
                        !resultDate.isAfter(
                            range.endDate
                        ) &&
                        !resultDate.isBefore(
                            createdDate
                        ) &&
                        !endExclusive.isAfter(
                            cutoffExclusive
                        )

            if (isEligible) {
                val countStart =
                    maxOf(
                        periodStart,
                        createdDate,
                    )

                val count =
                    completionDates.count {
                        !it.isBefore(countStart) &&
                                it.isBefore(
                                    endExclusive
                                )
                    }

                outcomes +=
                    PeriodOutcome(
                        date = resultDate,
                        completed =
                            count >=
                                    habit.scheduleTarget,
                    )
            }

            index += 1L
        }

        return outcomes
    }

    private fun completionIntervalOutcomes(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        range: HabitHistoryDateRange,
        cutoffExclusive: LocalDate,
        intervalDays: Int,
    ): List<PeriodOutcome> {
        val dates =
            completionDates.distinct()

        if (dates.isEmpty()) {
            return emptyList()
        }

        val outcomes =
            mutableListOf<PeriodOutcome>()

        /*
         * The first completion establishes the
         * initial anchor but is not itself scored.
         */
        var anchor = dates.first()

        dates.drop(1).forEach { date ->
            val dueDate =
                anchor.plusDays(
                    intervalDays.toLong()
                )

            when {
                date.isBefore(dueDate) -> {
                    if (
                        habit
                            .extraCompletionsMoveNextDueDate
                    ) {
                        anchor = date
                    }
                }

                date == dueDate -> {
                    outcomes +=
                        PeriodOutcome(
                            date = dueDate,
                            completed = true,
                        )

                    anchor = date
                }

                else -> {
                    /*
                     * The due interval was missed.
                     * The late completion establishes
                     * the next anchor without earning
                     * an additional success.
                     */
                    outcomes +=
                        PeriodOutcome(
                            date = dueDate,
                            completed = false,
                        )

                    anchor = date
                }
            }
        }

        val finalDueDate =
            anchor.plusDays(
                intervalDays.toLong()
            )

        if (finalDueDate.isBefore(cutoffExclusive)) {
            outcomes +=
                PeriodOutcome(
                    date = finalDueDate,
                    completed = false,
                )
        }

        return outcomes.filter {
            !it.date.isBefore(range.startDate) &&
                    !it.date.isAfter(range.endDate) &&
                    it.date.isBefore(
                        cutoffExclusive
                    )
        }
    }

    private fun activeCompletionDates(
        habitId: Long,
        logs: List<HabitLogEntity>,
    ): List<LocalDate> {
        val correctedIds =
            logs.asSequence()
                .filter {
                    it.habitId == habitId &&
                            it.delta == -1
                }
                .mapNotNull {
                    it.reversesLogId
                }
                .toSet()

        return logs.asSequence()
            .filter {
                it.habitId == habitId &&
                        it.delta == 1 &&
                        it.id !in correctedIds
            }
            .map {
                appDayCalculator
                    .containing(
                        it.completionTimestampMillis
                    )
                    .date
            }
            .toList()
    }

    private fun weekStartFor(
        date: LocalDate,
    ): LocalDate {
        val daysSinceStart =
            (
                    date.dayOfWeek.value -
                            weekStart.value +
                            7
                    ) % 7

        return date.minusDays(
            daysSinceStart.toLong()
        )
    }

}

private data class PeriodOutcome(
    val date: LocalDate,
    val completed: Boolean,
)