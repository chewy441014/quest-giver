package com.prestonhill.questgiver.feature.habits

import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import java.time.DayOfWeek
import java.time.LocalDate

data class HabitScheduleEvaluation(
    val dailyCompletionCount: Int,
    val scheduleCompletionCount: Int,
    val scheduleTarget: Int,
    val streakCount: Int,
    val dueStatus: HabitDueStatus
)

class HabitScheduleCalculator(
    private val appDayCalculator: AppDayCalculator,
    private val weekStart: DayOfWeek
) {
    fun evaluate(
        habit: HabitEntity,
        logs: List<HabitLogEntity>,
        appDay: AppDay
    ): HabitScheduleEvaluation {
        val completionDates = activeCompletionDates(
            habitId = habit.id,
            logs = logs
        )

        val currentDate = appDay.date
        val createdDate =
            appDayCalculator
                .containing(habit.createdAtEpochMillis)
                .date

        val dailyCount =
            completionDates.count { date ->
                date == currentDate
            }

        if (currentDate.isBefore(createdDate)) {
            return HabitScheduleEvaluation(
                dailyCompletionCount = dailyCount,
                scheduleCompletionCount = 0,
                scheduleTarget = habit.scheduleTarget,
                streakCount = 0,
                dueStatus = HabitDueStatus.NOT_DUE
            )
        }

        return when (habit.scheduleType) {
            HabitScheduleTypeDb.DAILY ->
                evaluateDaily(
                    habit = habit,
                    completionDates = completionDates,
                    currentDate = currentDate,
                    createdDate = createdDate,
                    dailyCount = dailyCount
                )

            HabitScheduleTypeDb.WEEKLY_TARGET ->
                evaluateWeekly(
                    habit = habit,
                    completionDates = completionDates,
                    currentDate = currentDate,
                    createdDate = createdDate,
                    dailyCount = dailyCount
                )

            HabitScheduleTypeDb.INTERVAL ->
                evaluateInterval(
                    habit = habit,
                    completionDates = completionDates,
                    currentDate = currentDate,
                    createdDate = createdDate,
                    dailyCount = dailyCount
                )
        }
    }

    private fun evaluateDaily(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        currentDate: LocalDate,
        createdDate: LocalDate,
        dailyCount: Int
    ): HabitScheduleEvaluation {
        val target = habit.scheduleTarget
        var cursor =
            if (dailyCount >= target) {
                currentDate
            } else {
                currentDate.minusDays(1)
            }

        var streak = 0

        while (!cursor.isBefore(createdDate)) {
            val count =
                completionDates.count { date ->
                    date == cursor
                }

            if (count < target) break

            streak += 1
            cursor = cursor.minusDays(1)
        }

        return HabitScheduleEvaluation(
            dailyCompletionCount = dailyCount,
            scheduleCompletionCount = dailyCount,
            scheduleTarget = target,
            streakCount = streak,
            dueStatus =
                if (dailyCount >= target) {
                    HabitDueStatus.COMPLETED
                } else {
                    HabitDueStatus.DUE
                }
        )
    }

    private fun evaluateWeekly(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        currentDate: LocalDate,
        createdDate: LocalDate,
        dailyCount: Int
    ): HabitScheduleEvaluation {
        val target = habit.scheduleTarget
        val currentWeekStart = weekStartFor(currentDate)

        val currentCount =
            countInRange(
                dates = completionDates,
                start = currentWeekStart,
                endExclusive =
                    currentWeekStart.plusWeeks(1)
            )

        var cursor =
            if (currentCount >= target) {
                currentWeekStart
            } else {
                currentWeekStart.minusWeeks(1)
            }

        var streak = 0

        while (
            !cursor.plusDays(6).isBefore(createdDate)
        ) {
            val count =
                countInRange(
                    dates = completionDates,
                    start = cursor,
                    endExclusive = cursor.plusWeeks(1)
                )

            if (count < target) break

            streak += 1
            cursor = cursor.minusWeeks(1)
        }

        return HabitScheduleEvaluation(
            dailyCompletionCount = dailyCount,
            scheduleCompletionCount = currentCount,
            scheduleTarget = target,
            streakCount = streak,
            dueStatus =
                if (currentCount >= target) {
                    HabitDueStatus.COMPLETED
                } else {
                    HabitDueStatus.DUE
                }
        )
    }

    private fun evaluateInterval(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        currentDate: LocalDate,
        createdDate: LocalDate,
        dailyCount: Int
    ): HabitScheduleEvaluation {
        val intervalDays = requireNotNull(
            habit.intervalDays
        )

        return when (habit.intervalBasis) {
            HabitIntervalBasisDb.FIXED_SCHEDULE ->
                evaluateFixedInterval(
                    habit = habit,
                    completionDates = completionDates,
                    currentDate = currentDate,
                    createdDate = createdDate,
                    dailyCount = dailyCount,
                    intervalDays = intervalDays
                )

            HabitIntervalBasisDb.FROM_COMPLETION ->
                evaluateCompletionInterval(
                    habit = habit,
                    completionDates = completionDates,
                    currentDate = currentDate,
                    dailyCount = dailyCount,
                    intervalDays = intervalDays
                )

            null -> error(
                "Interval habit requires an interval basis"
            )
        }
    }

    private fun evaluateFixedInterval(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        currentDate: LocalDate,
        createdDate: LocalDate,
        dailyCount: Int,
        intervalDays: Int
    ): HabitScheduleEvaluation {
        val target = habit.scheduleTarget
        val anchor =
            habit.fixedScheduleAnchorEpochDay
                ?.let(LocalDate::ofEpochDay)
                ?: createdDate

        if (currentDate.isBefore(anchor)) {
            return HabitScheduleEvaluation(
                dailyCompletionCount = dailyCount,
                scheduleCompletionCount = 0,
                scheduleTarget = target,
                streakCount = 0,
                dueStatus = HabitDueStatus.NOT_DUE
            )
        }

        val periodIndex =
            (
                    currentDate.toEpochDay() -
                            anchor.toEpochDay()
                    ) / intervalDays

        fun periodStart(index: Long): LocalDate =
            anchor.plusDays(index * intervalDays)

        val currentPeriodStart =
            periodStart(periodIndex)

        val currentCount =
            countInRange(
                dates = completionDates,
                start = currentPeriodStart,
                endExclusive =
                    currentPeriodStart.plusDays(intervalDays.toLong())
            )

        var cursorIndex =
            if (currentCount >= target) {
                periodIndex
            } else {
                periodIndex - 1
            }

        var streak = 0

        while (cursorIndex >= 0) {
            val start = periodStart(cursorIndex)
            val end =
                start.plusDays(intervalDays.toLong())

            if (end.minusDays(1).isBefore(createdDate)) {
                break
            }

            val count =
                countInRange(
                    dates = completionDates,
                    start = start,
                    endExclusive = end
                )

            if (count < target) break

            streak += 1
            cursorIndex -= 1
        }

        return HabitScheduleEvaluation(
            dailyCompletionCount = dailyCount,
            scheduleCompletionCount = currentCount,
            scheduleTarget = target,
            streakCount = streak,
            dueStatus =
                if (currentCount >= target) {
                    HabitDueStatus.COMPLETED
                } else {
                    HabitDueStatus.DUE
                }
        )
    }

    private fun evaluateCompletionInterval(
        habit: HabitEntity,
        completionDates: List<LocalDate>,
        currentDate: LocalDate,
        dailyCount: Int,
        intervalDays: Int
    ): HabitScheduleEvaluation {
        val dates =
            completionDates
                .filter { date -> !date.isAfter(currentDate) }
                .distinct()
                .sorted()

        if (dates.isEmpty()) {
            return HabitScheduleEvaluation(
                dailyCompletionCount = dailyCount,
                scheduleCompletionCount = 0,
                scheduleTarget = 1,
                streakCount = 0,
                dueStatus = HabitDueStatus.DUE
            )
        }

        var anchor = dates.first()
        var nextDue =
            anchor.plusDays(intervalDays.toLong())
        var streak = 1

        dates.drop(1).forEach { date ->
            if (date.isBefore(nextDue)) {
                if (
                    habit.extraCompletionsMoveNextDueDate
                ) {
                    anchor = date
                    nextDue =
                        anchor.plusDays(intervalDays.toLong())
                }
            } else {
                streak =
                    if (date == nextDue) {
                        streak + 1
                    } else {
                        1
                    }

                anchor = date
                nextDue =
                    anchor.plusDays(intervalDays.toLong())
            }
        }

        val isDue = !currentDate.isBefore(nextDue)

        if (currentDate.isAfter(nextDue)) {
            streak = 0
        }

        return HabitScheduleEvaluation(
            dailyCompletionCount = dailyCount,
            scheduleCompletionCount =
                if (isDue) 0 else 1,
            scheduleTarget = 1,
            streakCount = streak,
            dueStatus =
                if (isDue) {
                    HabitDueStatus.DUE
                } else {
                    HabitDueStatus.NOT_DUE
                }
        )
    }

    private fun activeCompletionDates(
        habitId: Long,
        logs: List<HabitLogEntity>
    ): List<LocalDate> {
        val reversedIds =
            logs.asSequence()
                .filter { log ->
                    log.habitId == habitId &&
                            log.delta == -1
                }
                .mapNotNull { log ->
                    log.reversesLogId
                }
                .toSet()

        return logs.asSequence()
            .filter { log ->
                log.habitId == habitId &&
                        log.delta == 1 &&
                        log.id !in reversedIds
            }
            .map { log ->
                appDayCalculator
                    .containing(
                        log.completionTimestampMillis
                    )
                    .date
            }
            .sorted()
            .toList()
    }

    private fun weekStartFor(date: LocalDate): LocalDate {
        val daysSinceStart =
            (
                    date.dayOfWeek.value -
                            weekStart.value +
                            7
                    ) % 7

        return date.minusDays(daysSinceStart.toLong())
    }

    private fun countInRange(
        dates: List<LocalDate>,
        start: LocalDate,
        endExclusive: LocalDate
    ): Int =
        dates.count { date ->
            !date.isBefore(start) &&
                    date.isBefore(endExclusive)
        }
}