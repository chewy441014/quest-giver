package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.DefaultHabitDisplaySections
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.feature.habits.HabitHistoryDateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitHistoryPerformanceCalculatorTest {
    private val zone =
        ZoneId.of("America/Chicago")

    private val calculator =
        HabitHistoryPerformanceCalculator(
            appDayCalculator =
                AppDayCalculator(
                    dayBoundary =
                        LocalTime.MIDNIGHT,
                    zoneId = zone,
                ),
            weekStart = DayOfWeek.MONDAY,
        )

    @Test
    fun dailyScoresOnlyFinishedDays(): Unit {
        val habit =
            habit(
                createdDate =
                    LocalDate.of(
                        2026,
                        8,
                        24,
                    )
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    8,
                                    24,
                                ),
                        ),
                        log(
                            id = 2L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    8,
                                    26,
                                ),
                        ),
                        /*
                         * Current-day completion does
                         * not finish the current period.
                         */
                        log(
                            id = 3L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    8,
                                    31,
                                ),
                        ),
                    ),
                range =
                    range(
                        start =
                            LocalDate.of(
                                2026,
                                8,
                                24,
                            ),
                        end =
                            LocalDate.of(
                                2026,
                                8,
                                31,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        assertEquals(2, result.completedPeriods)
        assertEquals(7, result.totalPeriods)

        assertEquals(
            2f / 7f,
            result.completionRate,
            0.0001f,
        )
    }

    @Test
    fun datesBeforeCreationAreExcluded(): Unit {
        val habit =
            habit(
                createdDate =
                    LocalDate.of(
                        2026,
                        8,
                        28,
                    )
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    8,
                                    28,
                                ),
                        )
                    ),
                range =
                    range(
                        start =
                            LocalDate.of(
                                2026,
                                8,
                                24,
                            ),
                        end =
                            LocalDate.of(
                                2026,
                                8,
                                31,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        assertEquals(1, result.completedPeriods)
        assertEquals(3, result.totalPeriods)
    }

    @Test
    fun weeklyTargetScoresBinaryPeriods(): Unit {
        val created =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val habit =
            habit(
                scheduleType =
                    HabitScheduleTypeDb
                        .WEEKLY_TARGET,
                scheduleTarget = 2,
                createdDate = created,
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        // First week succeeds.
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date = created,
                        ),
                        log(
                            id = 2L,
                            habitId = habit.id,
                            date =
                                created.plusDays(1),
                        ),

                        // Second week has only 1/2.
                        log(
                            id = 3L,
                            habitId = habit.id,
                            date =
                                created.plusWeeks(1),
                        ),

                        // Third week succeeds.
                        log(
                            id = 4L,
                            habitId = habit.id,
                            date =
                                created.plusWeeks(2),
                        ),
                        log(
                            id = 5L,
                            habitId = habit.id,
                            date =
                                created
                                    .plusWeeks(2)
                                    .plusDays(1),
                        ),
                    ),
                range =
                    range(
                        start = created,
                        end =
                            LocalDate.of(
                                2026,
                                9,
                                14,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        9,
                        14,
                    ),
            )

        assertEquals(2, result.completedPeriods)
        assertEquals(3, result.totalPeriods)
    }

    @Test
    fun fixedIntervalScoresFinishedPeriods(): Unit {
        val anchor =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val habit =
            habit(
                scheduleType =
                    HabitScheduleTypeDb.INTERVAL,
                intervalDays = 3,
                intervalBasis =
                    HabitIntervalBasisDb
                        .FIXED_SCHEDULE,
                anchorDate = anchor,
                createdDate = anchor,
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date = anchor,
                        ),
                        log(
                            id = 2L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    8,
                                    30,
                                ),
                        ),
                    ),
                range =
                    range(
                        start = anchor,
                        end =
                            LocalDate.of(
                                2026,
                                9,
                                14,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        9,
                        14,
                    ),
            )

        assertEquals(2, result.completedPeriods)
        assertEquals(7, result.totalPeriods)
    }

    @Test
    fun completionIntervalRecordsLateGap(): Unit {
        val created =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val habit =
            habit(
                scheduleType =
                    HabitScheduleTypeDb.INTERVAL,
                intervalDays = 3,
                intervalBasis =
                    HabitIntervalBasisDb
                        .FROM_COMPLETION,
                createdDate = created,
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        // Initial anchor.
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date = created,
                        ),

                        // Exactly on time.
                        log(
                            id = 2L,
                            habitId = habit.id,
                            date =
                                created.plusDays(3),
                        ),

                        /*
                         * Due August 30, completed
                         * late on September 1.
                         */
                        log(
                            id = 3L,
                            habitId = habit.id,
                            date =
                                LocalDate.of(
                                    2026,
                                    9,
                                    1,
                                ),
                        ),
                    ),
                range =
                    range(
                        start = created,
                        end =
                            LocalDate.of(
                                2026,
                                9,
                                5,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        9,
                        5,
                    ),
            )

        /*
         * Successes: Aug 24, Aug 27, Sep 1.
         * Failures: Aug 30 and Sep 4.
         */
        assertEquals(3, result.completedPeriods)
        assertEquals(5, result.totalPeriods)
    }

    @Test
    fun earlyCompletionMovesAnchorOnlyWhenEnabled(): Unit {
        val created =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val logs =
            listOf(
                log(
                    id = 1L,
                    habitId = HABIT_ID,
                    date = created,
                ),
                log(
                    id = 2L,
                    habitId = HABIT_ID,
                    date =
                        created.plusDays(1),
                ),
                log(
                    id = 3L,
                    habitId = HABIT_ID,
                    date =
                        created.plusDays(3),
                ),
            )

        val selectedRange =
            range(
                start = created,
                end =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        val fixedAnchor =
            calculate(
                habit =
                    habit(
                        scheduleType =
                            HabitScheduleTypeDb
                                .INTERVAL,
                        intervalDays = 3,
                        intervalBasis =
                            HabitIntervalBasisDb
                                .FROM_COMPLETION,
                        createdDate = created,
                        moveAfterExtra = false,
                    ),
                logs = logs,
                range = selectedRange,
                currentDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        val movingAnchor =
            calculate(
                habit =
                    habit(
                        scheduleType =
                            HabitScheduleTypeDb
                                .INTERVAL,
                        intervalDays = 3,
                        intervalBasis =
                            HabitIntervalBasisDb
                                .FROM_COMPLETION,
                        createdDate = created,
                        moveAfterExtra = true,
                    ),
                logs = logs,
                range = selectedRange,
                currentDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        assertEquals(
            HabitHistoryPerformanceResult(
                completedPeriods = 2,
                totalPeriods = 3,
            ),
            fixedAnchor,
        )

        assertEquals(
            HabitHistoryPerformanceResult(
                completedPeriods = 1,
                totalPeriods = 2,
            ),
            movingAnchor,
        )
    }

    @Test
    fun correctionRemovesCompletion(): Unit {
        val firstDate =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val habit =
            habit(createdDate = firstDate)

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        log(
                            id = 10L,
                            habitId = habit.id,
                            date = firstDate,
                        ),
                        log(
                            id = 11L,
                            habitId = habit.id,
                            date = firstDate,
                            delta = -1,
                            reversesLogId = 10L,
                        ),
                    ),
                range =
                    range(
                        start = firstDate,
                        end =
                            firstDate.plusDays(1),
                    ),
                currentDate =
                    firstDate.plusDays(2),
            )

        assertEquals(0, result.completedPeriods)
        assertEquals(2, result.totalPeriods)
    }

    @Test
    fun archivedHabitStopsAtArchiveDay(): Unit {
        val created =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val archiveDate =
            LocalDate.of(
                2026,
                8,
                27,
            )

        val habit =
            habit(
                createdDate = created,
                archivedDate = archiveDate,
            )

        val result =
            calculate(
                habit = habit,
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                            date = created,
                        ),
                        /*
                         * This later log must not affect
                         * archived performance.
                         */
                        log(
                            id = 2L,
                            habitId = habit.id,
                            date =
                                archiveDate.plusDays(1),
                        ),
                    ),
                range =
                    range(
                        start = created,
                        end =
                            LocalDate.of(
                                2026,
                                8,
                                31,
                            ),
                    ),
                currentDate =
                    LocalDate.of(
                        2026,
                        8,
                        31,
                    ),
            )

        assertEquals(1, result.completedPeriods)
        assertEquals(4, result.totalPeriods)
    }

    private fun calculate(
        habit: HabitEntity,
        logs: List<HabitLogEntity>,
        range: HabitHistoryDateRange,
        currentDate: LocalDate,
    ): HabitHistoryPerformanceResult =
        calculator.calculate(
            habit = habit,
            logs = logs,
            range = range,
            currentDate = currentDate,
        )

    private fun habit(
        id: Long = HABIT_ID,
        scheduleType:
        HabitScheduleTypeDb =
            HabitScheduleTypeDb.DAILY,
        scheduleTarget: Int = 1,
        intervalDays: Int? = null,
        intervalBasis:
        HabitIntervalBasisDb? = null,
        anchorDate: LocalDate? = null,
        createdDate: LocalDate,
        archivedDate: LocalDate? = null,
        moveAfterExtra: Boolean = false,
    ): HabitEntity =
        HabitEntity(
            id = id,
            name = "Habit $id",
            displaySectionId =
                DefaultHabitDisplaySections
                    .ANYTIME_ID,
            displayOrder = 0,
            scheduleType = scheduleType,
            scheduleTarget = scheduleTarget,
            intervalDays = intervalDays,
            intervalBasis = intervalBasis,
            fixedScheduleAnchorEpochDay =
                anchorDate?.toEpochDay(),
            extraCompletionsMoveNextDueDate =
                moveAfterExtra,
            createdAtEpochMillis =
                timestamp(createdDate),
            archivedAtEpochMillis =
                archivedDate?.let(
                    ::timestamp
                ),
        )

    private fun log(
        id: Long,
        habitId: Long,
        date: LocalDate,
        delta: Int = 1,
        reversesLogId: Long? = null,
    ): HabitLogEntity =
        HabitLogEntity(
            id = id,
            habitId = habitId,
            completionTimestampMillis =
                timestamp(date),
            recordedTimestampMillis =
                timestamp(date),
            delta = delta,
            reversesLogId = reversesLogId,
        )

    private fun timestamp(
        date: LocalDate,
    ): Long =
        date.atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun range(
        start: LocalDate,
        end: LocalDate,
    ) =
        HabitHistoryDateRange(
            startDate = start,
            endDate = end,
        )

    private companion object {
        const val HABIT_ID = 1L
    }
}