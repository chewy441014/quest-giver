package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class HabitHistoryMapperTest {
    private val mapper =
        HabitHistoryMapper()

    private val zone =
        ZoneId.of("America/Chicago")

    private val calculator =
        AppDayCalculator(
            dayBoundary = LocalTime.of(4, 0),
            zoneId = zone,
        )

    @Test
    fun uncategorizedHabitGetsOneStampPerDay(): Unit {
        val habit =
            habit(
                id = 1L,
                historyCategory = null,
            )

        val calendar =
            mapCalendar(
                habits = listOf(habit),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                        ),
                        log(
                            id = 2L,
                            habitId = habit.id,
                            timestamp =
                                timestamp(
                                    CURRENT_DATE,
                                    13,
                                ),
                        ),
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            "Uncategorized habits",
            filter.groupLabel,
        )

        assertEquals(
            listOf(filter.key),
            calendar.day().stampKeys,
        )
    }

    @Test
    fun performanceUsesPopulationAndNewestOrder(): Unit {
        val habits =
            listOf(
                habit(
                    id = 1L,
                    name = "Old active",
                    createdAt = 1_000L,
                ),
                habit(
                    id = 2L,
                    name = "New active",
                    createdAt = 3_000L,
                ),
                habit(
                    id = 3L,
                    name = "Excluded",
                    visibleInHistory = false,
                    createdAt = 4_000L,
                ),
                habit(
                    id = 4L,
                    name = "Archived",
                    archivedAt = 5_000L,
                    createdAt = 2_000L,
                ),
            )

        assertEquals(
            listOf(
                "New active",
                "Old active",
            ),
            mapPerformance(habits)
                .map {
                    it.name
                },
        )

        assertEquals(
            listOf("Archived"),
            mapPerformance(
                habits = habits,
                showArchived = true,
            )
                .map {
                    it.name
                },
        )
    }

    @Test
    fun performanceMapsScheduleLabels(): Unit {
        val rows =
            mapPerformance(
                habits =
                    listOf(
                        habit(
                            id = 1L,
                            scheduleTarget = 2,
                        ),
                        habit(
                            id = 2L,
                            scheduleType =
                                HabitScheduleTypeDb
                                    .WEEKLY_TARGET,
                            scheduleTarget = 3,
                        ),
                        habit(
                            id = 3L,
                            scheduleType =
                                HabitScheduleTypeDb
                                    .INTERVAL,
                            intervalDays = 5,
                            intervalBasis =
                                HabitIntervalBasisDb
                                    .FIXED_SCHEDULE,
                        ),
                        habit(
                            id = 4L,
                            scheduleType =
                                HabitScheduleTypeDb
                                    .INTERVAL,
                            intervalDays = 3,
                            intervalBasis =
                                HabitIntervalBasisDb
                                    .FROM_COMPLETION,
                        ),
                    )
            )
                .associate {
                    it.habitId to it.schedule
                }

        assertEquals(
            "2 per day",
            rows[1L],
        )

        assertEquals(
            "3 per week",
            rows[2L],
        )

        assertEquals(
            "Every 5 days",
            rows[3L],
        )

        assertEquals(
            "Every 3 days after completion",
            rows[4L],
        )
    }

    @Test
    fun performanceRecalculatesWithCurrentSchedule(): Unit {
        val date =
            CURRENT_DATE.minusDays(1)

        val original =
            habit(
                id = 1L,
                scheduleTarget = 1,
                createdAt =
                    timestamp(date, 8),
            )

        val logs =
            listOf(
                log(
                    id = 1L,
                    habitId = original.id,
                    timestamp =
                        timestamp(date, 12),
                )
            )

        val originalRow =
            mapPerformance(
                habits = listOf(original),
                logs = logs,
                range =
                    HabitHistoryDateRange(
                        startDate = date,
                        endDate = CURRENT_DATE,
                    ),
            )
                .single()

        val editedRow =
            mapPerformance(
                habits =
                    listOf(
                        original.copy(
                            scheduleTarget = 2
                        )
                    ),
                logs = logs,
                range =
                    HabitHistoryDateRange(
                        startDate = date,
                        endDate = CURRENT_DATE,
                    ),
            )
                .single()

        assertEquals(
            1,
            originalRow.completedPeriods,
        )

        assertEquals(
            0,
            editedRow.completedPeriods,
        )

        assertEquals(
            1,
            originalRow.totalPeriods,
        )

        assertEquals(
            1,
            editedRow.totalPeriods,
        )
    }

    @Test
    fun categoryGetsOneStampAcrossHabits(): Unit {
        val first =
            habit(
                id = 1L,
                historyCategory = "Gym",
            )

        val second =
            habit(
                id = 2L,
                historyCategory = "gym",
            )

        val calendar =
            mapCalendar(
                habits =
                    listOf(first, second),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = first.id,
                        ),
                        log(
                            id = 2L,
                            habitId = second.id,
                        ),
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            "Habit categories",
            filter.groupLabel,
        )

        assertEquals(
            listOf(filter.key),
            calendar.day().stampKeys,
        )
    }

    @Test
    fun correctedCompletionCreatesNoStamp(): Unit {
        val habit =
            habit(id = 1L)

        val calendar =
            mapCalendar(
                habits = listOf(habit),
                logs =
                    listOf(
                        log(
                            id = 10L,
                            habitId = habit.id,
                        ),
                        log(
                            id = 11L,
                            habitId = habit.id,
                            delta = -1,
                            reversesLogId = 10L,
                        ),
                    ),
            )

        /*
         * The habit remains selectable even
         * when it has no active completions.
         */
        assertEquals(
            1,
            calendar.availableFilters.size,
        )

        Assert.assertTrue(
            calendar.day()
                .stampKeys
                .isEmpty()
        )
    }

    @Test
    fun archiveModeSelectsItsOwnPopulation(): Unit {
        val active =
            habit(
                id = 1L,
                name = "Active",
            )

        val excluded =
            habit(
                id = 2L,
                name = "Excluded",
                visibleInHistory = false,
            )

        val archived =
            habit(
                id = 3L,
                name = "Archived",
                archivedAt = 5_000L,
            )

        val habits =
            listOf(
                active,
                excluded,
                archived,
            )

        assertEquals(
            listOf("Active"),
            mapCalendar(
                habits = habits,
            )
                .availableFilters
                .map {
                    it.label
                },
        )

        assertEquals(
            listOf("Archived"),
            mapCalendar(
                habits = habits,
                showArchived = true,
            )
                .availableFilters
                .map {
                    it.label
                },
        )
    }

    @Test
    fun uncategorizedFiltersAreNewestFirst(): Unit {
        val calendar =
            mapCalendar(
                habits =
                    listOf(
                        habit(
                            id = 1L,
                            name = "Old",
                            createdAt = 1_000L,
                        ),
                        habit(
                            id = 2L,
                            name = "Newest",
                            createdAt = 3_000L,
                        ),
                        habit(
                            id = 3L,
                            name = "Middle",
                            createdAt = 2_000L,
                        ),
                    ),
            )

        assertEquals(
            listOf(
                "Newest",
                "Middle",
                "Old",
            ),
            calendar.availableFilters
                .map {
                    it.label
                },
        )
    }

    @Test
    fun calendarUsesAppDayBoundary(): Unit {
        val habit =
            habit(id = 1L)

        /*
         * At a 4 AM boundary, 2 AM Tuesday
         * still belongs to Monday's app-day.
         */
        val earlyNextMorning =
            timestamp(
                CURRENT_DATE.plusDays(1),
                2,
            )

        val calendar =
            mapCalendar(
                habits = listOf(habit),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            habitId = habit.id,
                            timestamp =
                                earlyNextMorning,
                        )
                    ),
            )

        assertEquals(
            1,
            calendar.day().stampKeys.size,
        )

        Assert.assertTrue(
            calendar.days
                .single {
                    it.date ==
                            CURRENT_DATE
                                .plusDays(1)
                }
                .stampKeys
                .isEmpty()
        )
    }

    @Test
    fun habitRangePresetsResolveDates(): Unit {
        val current =
            LocalDate.of(
                2026,
                9,
                2,
            )

        val custom =
            defaultHabitCustomRange(current)

        assertEquals(
            LocalDate.of(
                2026,
                8,
                4,
            ),
            HabitHistoryRangePreset
                .THIRTY_DAYS
                .dateRange(
                    currentDate = current,
                    customRange = custom,
                )
                .startDate,
        )

        assertEquals(
            LocalDate.of(
                2026,
                7,
                5,
            ),
            HabitHistoryRangePreset
                .SIXTY_DAYS
                .dateRange(
                    currentDate = current,
                    customRange = custom,
                )
                .startDate,
        )

        assertEquals(
            custom,
            HabitHistoryRangePreset
                .CUSTOM
                .dateRange(
                    currentDate = current,
                    customRange = custom,
                ),
        )
    }

    @Test
    fun habitCustomRangeDefaultsToPreviousMonth(): Unit {
        assertEquals(
            HabitHistoryDateRange(
                startDate =
                    LocalDate.of(
                        2026,
                        7,
                        1,
                    ),
                endDate =
                    LocalDate.of(
                        2026,
                        7,
                        31,
                    ),
            ),
            defaultHabitCustomRange(
                LocalDate.of(
                    2026,
                    8,
                    24,
                )
            ),
        )
    }

    @Test
    fun stampColorsRemainStableAfterRename(): Unit {
        val original =
            habit(
                id = 1L,
                name = "Original",
            )

        val renamed =
            original.copy(
                name = "Renamed"
            )

        val originalColors =
            mapCalendar(
                habits = listOf(original)
            )
                .availableFilters
                .single()
                .colors

        val renamedColors =
            mapCalendar(
                habits = listOf(renamed)
            )
                .availableFilters
                .single()
                .colors

        assertEquals(
            originalColors,
            renamedColors,
        )
    }

    private fun mapPerformance(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity> =
            emptyList(),
        range: HabitHistoryDateRange =
            HabitHistoryDateRange(
                startDate =
                    CURRENT_DATE.minusDays(29),
                endDate = CURRENT_DATE,
            ),
        showArchived: Boolean = false,
    ): List<HabitHistoryPerformanceUiState> =
        mapper.performance(
            habits = habits,
            logs = logs,
            range = range,
            currentDate = CURRENT_DATE,
            calculator =
                HabitHistoryPerformanceCalculator(
                    appDayCalculator =
                        calculator,
                    weekStart =
                        DayOfWeek.MONDAY,
                ),
            showArchivedHabits =
                showArchived,
        )

    private fun mapCalendar(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity> =
            emptyList(),
        showArchived: Boolean = false,
    ): HistoryStampCalendarUiState =
        mapper.stampCalendar(
            habits = habits,
            logs = logs,
            month = MONTH,
            currentDate = CURRENT_DATE,
            weekStart = DayOfWeek.MONDAY,
            calculator = calculator,
            showArchivedHabits =
                showArchived,
        )

    private fun HistoryStampCalendarUiState.day(
        date: LocalDate = CURRENT_DATE,
    ): HistoryStampCalendarDayUiState =
        days.single {
            it.date == date
        }

    private fun habit(
        id: Long,
        name: String = "Habit $id",
        historyCategory: String? = null,
        visibleInHistory: Boolean = true,
        createdAt: Long = id,
        archivedAt: Long? = null,
        scheduleType:
        HabitScheduleTypeDb =
            HabitScheduleTypeDb.DAILY,
        scheduleTarget: Int = 1,
        intervalDays: Int? = null,
        intervalBasis:
        HabitIntervalBasisDb? = null,
    ): HabitEntity =
        HabitEntity(
            id = id,
            name = name,
            displaySectionId = "ANYTIME",
            historyCategory =
                historyCategory,
            displayOrder = id.toInt(),
            isVisibleInHistory =
                visibleInHistory,
            scheduleType = scheduleType,
            scheduleTarget = scheduleTarget,
            intervalDays = intervalDays,
            intervalBasis = intervalBasis,
            fixedScheduleAnchorEpochDay =
                if (
                    scheduleType ==
                    HabitScheduleTypeDb.INTERVAL &&
                    intervalBasis ==
                    HabitIntervalBasisDb
                        .FIXED_SCHEDULE
                ) {
                    CURRENT_DATE
                        .minusDays(30)
                        .toEpochDay()
                } else {
                    null
                },
            createdAtEpochMillis =
                createdAt,
            archivedAtEpochMillis =
                archivedAt,
        )

    private fun log(
        id: Long,
        habitId: Long,
        timestamp: Long =
            timestamp(
                CURRENT_DATE,
                12,
            ),
        delta: Int = 1,
        reversesLogId: Long? = null,
    ): HabitLogEntity =
        HabitLogEntity(
            id = id,
            habitId = habitId,
            completionTimestampMillis =
                timestamp,
            recordedTimestampMillis =
                timestamp,
            delta = delta,
            reversesLogId =
                reversesLogId,
        )

    private fun timestamp(
        date: LocalDate,
        hour: Int,
    ): Long =
        date.atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private companion object {
        val CURRENT_DATE: LocalDate =
            LocalDate.of(
                2026,
                8,
                24,
            )

        val MONTH: YearMonth =
            YearMonth.from(CURRENT_DATE)
    }
}