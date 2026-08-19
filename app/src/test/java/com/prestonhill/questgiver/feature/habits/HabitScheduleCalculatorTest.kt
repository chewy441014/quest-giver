package com.prestonhill.questgiver.feature.habits

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HabitScheduleCalculatorTest {
    private val zone = ZoneId.of("America/Chicago")

    private val appDayCalculator =
        AppDayCalculator(
            dayBoundary = LocalTime.MIDNIGHT,
            zoneId = zone
        )

    private val calculator =
        HabitScheduleCalculator(
            appDayCalculator = appDayCalculator,
            weekStart = DayOfWeek.MONDAY
        )

    @Test
    fun dailyStreakCountsDays() {
        val habit = habit(
            createdDate = date(2026, 8, 17)
        )

        val logs = listOf(
            completion(1, date(2026, 8, 17)),
            completion(2, date(2026, 8, 18)),
            completion(3, date(2026, 8, 19))
        )

        val result = evaluate(
            habit = habit,
            logs = logs,
            currentDate = date(2026, 8, 19)
        )

        assertEquals(1, result.dailyCompletionCount)
        assertEquals(3, result.streakCount)
        assertEquals(HabitDueStatus.COMPLETED, result.dueStatus)
    }

    @Test
    fun weeklyStreakIgnoresDays() {
        val habit = habit(
            scheduleType =
                HabitScheduleTypeDb.WEEKLY_TARGET,
            target = 2,
            createdDate = date(2026, 8, 10)
        )

        val logs = listOf(
            completion(1, date(2026, 8, 10)),
            completion(2, date(2026, 8, 15)),
            completion(3, date(2026, 8, 17)),
            completion(4, date(2026, 8, 19))
        )

        val result = evaluate(
            habit = habit,
            logs = logs,
            currentDate = date(2026, 8, 19)
        )

        assertEquals(2, result.scheduleCompletionCount)
        assertEquals(2, result.streakCount)
        assertEquals(HabitDueStatus.COMPLETED, result.dueStatus)
    }

    @Test
    fun reversalRemovesCompletion() {
        val habit = habit(
            createdDate = date(2026, 8, 19)
        )

        val positive =
            completion(1, date(2026, 8, 19))

        val reversal =
            HabitLogEntity(
                id = 2,
                habitId = habit.id,
                completionTimestampMillis =
                    positive.completionTimestampMillis,
                recordedTimestampMillis =
                    positive.recordedTimestampMillis + 1,
                delta = -1,
                reversesLogId = positive.id
            )

        val result = evaluate(
            habit = habit,
            logs = listOf(positive, reversal),
            currentDate = date(2026, 8, 19)
        )

        assertEquals(0, result.dailyCompletionCount)
        assertEquals(0, result.streakCount)
        assertEquals(HabitDueStatus.DUE, result.dueStatus)
    }

    @Test
    fun fixedIntervalsCountPeriods() {
        val anchor = date(2026, 8, 10)

        val habit = habit(
            scheduleType =
                HabitScheduleTypeDb.INTERVAL,
            intervalDays = 3,
            intervalBasis =
                HabitIntervalBasisDb.FIXED_SCHEDULE,
            anchorDate = anchor,
            createdDate = anchor
        )

        val logs = listOf(
            completion(1, date(2026, 8, 12)),
            completion(2, date(2026, 8, 14)),
            completion(3, date(2026, 8, 17))
        )

        val result = evaluate(
            habit = habit,
            logs = logs,
            currentDate = date(2026, 8, 17)
        )

        assertEquals(1, result.scheduleCompletionCount)
        assertEquals(3, result.streakCount)
        assertEquals(HabitDueStatus.COMPLETED, result.dueStatus)
    }

    @Test
    fun completionIntervalTracksDueDate() {
        val habit = habit(
            scheduleType =
                HabitScheduleTypeDb.INTERVAL,
            intervalDays = 3,
            intervalBasis =
                HabitIntervalBasisDb.FROM_COMPLETION,
            createdDate = date(2026, 8, 10)
        )

        val logs = listOf(
            completion(1, date(2026, 8, 10)),
            completion(2, date(2026, 8, 13)),
            completion(3, date(2026, 8, 16))
        )

        val result = evaluate(
            habit = habit,
            logs = logs,
            currentDate = date(2026, 8, 18)
        )

        assertEquals(1, result.scheduleCompletionCount)
        assertEquals(3, result.streakCount)
        assertEquals(HabitDueStatus.NOT_DUE, result.dueStatus)
    }

    private fun evaluate(
        habit: HabitEntity,
        logs: List<HabitLogEntity>,
        currentDate: LocalDate
    ): HabitScheduleEvaluation =
        calculator.evaluate(
            habit = habit,
            logs = logs,
            appDay =
                appDayCalculator.forDate(currentDate)
        )

    private fun habit(
        scheduleType: HabitScheduleTypeDb =
            HabitScheduleTypeDb.DAILY,
        target: Int = 1,
        intervalDays: Int? = null,
        intervalBasis: HabitIntervalBasisDb? = null,
        anchorDate: LocalDate? = null,
        createdDate: LocalDate
    ) =
        HabitEntity(
            id = 1,
            name = "Test habit",
            category = HabitCategoryDb.ANYTIME,
            displayOrder = 0,
            scheduleType = scheduleType,
            scheduleTarget = target,
            intervalDays = intervalDays,
            intervalBasis = intervalBasis,
            fixedScheduleAnchorEpochDay =
                anchorDate?.toEpochDay(),
            createdAtEpochMillis =
                timestamp(createdDate)
        )

    private fun completion(
        id: Long,
        date: LocalDate
    ) =
        HabitLogEntity(
            id = id,
            habitId = 1,
            completionTimestampMillis = timestamp(date),
            recordedTimestampMillis = timestamp(date),
            delta = 1
        )

    private fun timestamp(date: LocalDate): Long =
        date.atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun date(
        year: Int,
        month: Int,
        day: Int
    ): LocalDate =
        LocalDate.of(year, month, day)
}