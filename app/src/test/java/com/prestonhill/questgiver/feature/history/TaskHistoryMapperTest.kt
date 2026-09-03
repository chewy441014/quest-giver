package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.feature.tasks.TaskScheduleCalculator
import java.time.LocalTime
import java.time.ZoneId
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHistoryMapperTest {
    private val mapper = TaskHistoryMapper()

    private val zone =
        ZoneId.of("America/Chicago")

    private val dayCalculator =
        AppDayCalculator(
            dayBoundary = LocalTime.MIDNIGHT,
            zoneId = zone,
        )

    private val scheduleCalculator =
        TaskScheduleCalculator(
            dayCalculator
        )

    private val currentTimestamp =
        LocalDate.ofEpochDay(DAY)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun mapsSchedules(): Unit {
        val tasks =
            listOf(
                task(
                    id = 1L,
                    type =
                        TaskScheduleTypeDb.ONE_TIME,
                    scheduledDay = DAY,
                ),
                task(
                    id = 2L,
                    type =
                        TaskScheduleTypeDb.ONE_TIME,
                ),
                task(
                    id = 3L,
                    type =
                        TaskScheduleTypeDb.DAILY,
                ),
                task(
                    id = 4L,
                    type =
                        TaskScheduleTypeDb.WEEKLY_DAYS,
                    weekdaysMask =
                        (1 shl 0) or
                                (1 shl 2),
                ),
                task(
                    id = 5L,
                    type =
                        TaskScheduleTypeDb.INTERVAL,
                    intervalDays = 3,
                    intervalBasis =
                        TaskIntervalBasisDb
                            .FIXED_SCHEDULE,
                ),
                task(
                    id = 6L,
                    type =
                        TaskScheduleTypeDb.INTERVAL,
                    intervalDays = 5,
                    intervalBasis =
                        TaskIntervalBasisDb
                            .FROM_COMPLETION,
                ),
            )

        val mapped = mapper.tasks(tasks)

        assertEquals(
            listOf(
                "One time · 2026-08-24",
                "One time · Anytime",
                "Daily",
                "Weekly · Mon, Wed",
                "Every 3 days",
                "Every 5 days after completion",
            ),
            mapped.map { it.schedule },
        )
    }

    @Test
    fun groupsNewestFirst(): Unit {
        val previousDay = DAY - 1L

        val mapped =
            mapper.logs(
                listOf(
                    log(
                        id = 1L,
                        day = previousDay,
                        taskId = 1L,
                        completedAt = 100L,
                    ),
                    log(
                        id = 2L,
                        day = DAY,
                        taskId = 2L,
                        completedAt = 200L,
                    ),
                    log(
                        id = 3L,
                        day = DAY,
                        taskId = 3L,
                        completedAt = 300L,
                    ),
                )
            )

        assertEquals(
            listOf(
                LocalDate.ofEpochDay(DAY),
                LocalDate.ofEpochDay(previousDay),
            ),
            mapped.map { it.date },
        )

        assertEquals(
            listOf(3L, 2L),
            mapped.first().logs.map { it.id },
        )
    }

    @Test
    fun correctedLogsAreHidden(): Unit {
        val mapped =
            mapper.logs(
                listOf(
                    log(
                        id = 10L,
                        day = DAY,
                    ),
                    log(
                        id = 11L,
                        day = DAY,
                        delta = -1,
                        reversesLogId = 10L,
                    ),
                )
            )

        assertTrue(mapped.isEmpty())
    }

    @Test
    fun archivedTaskCannotChange(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.DAILY,
                archivedAt = 2_000L,
            )

        val row =
            mapTasks(listOf(task))
                .single()

        assertTrue(row.isArchived)

        assertFalse(
            row.canChangeCompletion
        )
    }

    @Test
    fun activeLogMapsTaskIdentity(): Unit {
        val row =
            mapper.logs(
                listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = 7L,
                    )
                )
            )
                .single()
                .logs
                .single()

        assertEquals(7L, row.taskId)

        assertFalse(
            row.isTaskCompletionChanging
        )
    }

    @Test
    fun currentTaskCanChange(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.DAILY,
            )

        val row =
            mapTasks(listOf(task))
                .single()

        assertFalse(row.isCompleted)
        assertTrue(row.canChangeCompletion)

        assertEquals(
            DAY,
            row.completionEpochDay,
        )
    }

    @Test
    fun completedTaskCanChange(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.DAILY,
            )

        val row =
            mapTasks(
                tasks = listOf(task),
                logs = listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = task.id,
                    )
                ),
            )
                .single()

        assertTrue(row.isCompleted)
        assertTrue(row.canChangeCompletion)
    }

    @Test
    fun futureTaskCannotChange(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                scheduledDay = DAY + 1L,
            )

        val row =
            mapTasks(listOf(task))
                .single()

        assertFalse(row.isCompleted)
        assertFalse(row.canChangeCompletion)
    }

    @Test
    fun hiddenDueTaskCanChange(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                scheduledDay = DAY,
                dueMinuteOfDay = 9 * 60,
            )

        val row =
            mapTasks(listOf(task))
                .single()

        assertFalse(row.isCompleted)
        assertTrue(row.canChangeCompletion)
    }

    @Test
    fun changingTaskIsMarked(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.DAILY,
            )

        val row =
            mapTasks(
                tasks = listOf(task),
                changingTaskIds =
                    setOf(task.id),
            )
                .single()

        assertTrue(row.isChanging)
    }

    @Test
    fun replacementIsCurrentLog(): Unit {
        val rows =
            mapper.logs(
                listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = 7L,
                    ),
                    log(
                        id = 2L,
                        day = DAY,
                        taskId = 7L,
                        delta = -1,
                        reversesLogId = 1L,
                    ),
                    log(
                        id = 3L,
                        day = DAY,
                        taskId = 7L,
                    ),
                )
            )
                .flatMap { it.logs }

        assertEquals(
            listOf(3L),
            rows.map { it.id },
        )
    }

    @Test
    fun oneTimeUsesLatestLog(): Unit {
        val oneTime =
            task(
                id = 7L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                scheduledDay = DAY,
            )

        val rows =
            mapper.logs(
                logs = listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = oneTime.id,
                        completedAt = 1_000L,
                    ),
                    log(
                        id = 2L,
                        day = DAY + 2L,
                        taskId = oneTime.id,
                        completedAt = 2_000L,
                    ),
                ),
                tasks = listOf(oneTime),
            )
                .flatMap { it.logs }

        assertEquals(
            listOf(2L),
            rows.map { it.id },
        )
    }

    @Test
    fun recurringTaskGetsOneStampPerDay(): Unit {
        val task =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                category = null,
            )

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = task.id,
                            completedAt = 1_000L,
                            category = null,
                        ),
                        log(
                            id = 2L,
                            day = DAY,
                            taskId = task.id,
                            completedAt = 2_000L,
                            category = null,
                        ),
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            "Recurring tasks",
            filter.groupLabel,
        )

        assertEquals(
            listOf(filter.key),
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys,
        )
    }

    @Test
    fun categoryGetsOneStampPerDay(): Unit {
        val first =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                category = "Work",
            )

        val second =
            task(
                id = 2L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                category = "work",
            )

        val calendar =
            mapCalendar(
                tasks = listOf(first, second),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = first.id,
                            category = "Work",
                        ),
                        log(
                            id = 2L,
                            day = DAY,
                            taskId = second.id,
                            completedAt = 2_000L,
                            category = "work",
                        ),
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            "Categories",
            filter.groupLabel,
        )

        assertEquals(
            listOf(filter.key),
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys,
        )
    }

    @Test
    fun recurringCompletionCanCreateTwoStamps(): Unit {
        val task =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                category = "Health",
            )

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = task.id,
                            category = "Health",
                        )
                    ),
            )

        assertEquals(
            setOf(
                "Recurring tasks",
                "Categories",
            ),
            calendar.availableFilters
                .map {
                    it.groupLabel
                }
                .toSet(),
        )

        assertEquals(
            2,
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys
                .size,
        )
    }

    @Test
    fun correctedCompletionCreatesNoStamp(): Unit {
        val task =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                category = "Work",
            )

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 10L,
                            day = DAY,
                            taskId = task.id,
                            category = "Work",
                        ),
                        log(
                            id = 11L,
                            day = DAY,
                            taskId = task.id,
                            delta = -1,
                            reversesLogId = 10L,
                            category = "Work",
                        ),
                    ),
            )

        assertTrue(
            calendar.availableFilters.isEmpty()
        )

        assertTrue(
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys
                .isEmpty()
        )
    }

    @Test
    fun uncategorizedOneTimeTaskCreatesNoStamp(): Unit {
        val task =
            task(
                id = 1L,
                type =
                    TaskScheduleTypeDb.ONE_TIME,
                category = null,
                scheduledDay = DAY,
            )

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = task.id,
                            category = null,
                        )
                    ),
            )

        assertTrue(
            calendar.availableFilters.isEmpty()
        )

        assertTrue(
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys
                .isEmpty()
        )
    }

    @Test
    fun archivedRecurringTaskRemainsSelectable(): Unit {
        val task =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                category = null,
                archivedAt = 5_000L,
            )

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = task.id,
                            category = null,
                        )
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            task.name,
            filter.label,
        )

        assertEquals(
            "Recurring tasks",
            filter.groupLabel,
        )
    }

    @Test
    fun calendarUsesScheduledDay(): Unit {
        val task =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                category = null,
            )

        val nextDate =
            CURRENT_DATE.plusDays(1)

        val completedNextDay =
            nextDate
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        val calendar =
            mapCalendar(
                tasks = listOf(task),
                logs =
                    listOf(
                        log(
                            id = 1L,
                            day = DAY,
                            taskId = task.id,
                            completedAt =
                                completedNextDay,
                            category = null,
                        )
                    ),
            )

        val filter =
            calendar.availableFilters.single()

        assertEquals(
            listOf(filter.key),
            calendar.days
                .single {
                    it.date == CURRENT_DATE
                }
                .stampKeys,
        )

        assertTrue(
            calendar.days
                .single {
                    it.date == nextDate
                }
                .stampKeys
                .isEmpty()
        )
    }

    @Test
    fun stampColorsRemainStableAfterRename(): Unit {
        val original =
            task(
                id = 1L,
                type = TaskScheduleTypeDb.DAILY,
                name = "Original name",
                category = null,
            )

        val renamed =
            original.copy(
                name = "Renamed task"
            )

        val logs =
            listOf(
                log(
                    id = 1L,
                    day = DAY,
                    taskId = original.id,
                    category = null,
                )
            )

        val originalColors =
            mapCalendar(
                tasks = listOf(original),
                logs = logs,
            )
                .availableFilters
                .single()
                .colors

        val renamedColors =
            mapCalendar(
                tasks = listOf(renamed),
                logs = logs,
            )
                .availableFilters
                .single()
                .colors

        assertEquals(
            originalColors,
            renamedColors,
        )
    }

    @Test
    fun recurringDaysStaySeparate(): Unit {
        val daily =
            task(
                id = 7L,
                type =
                    TaskScheduleTypeDb.DAILY,
            )

        val rows =
            mapper.logs(
                logs = listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = daily.id,
                        completedAt = 1_000L,
                    ),
                    log(
                        id = 2L,
                        day = DAY,
                        taskId = daily.id,
                        completedAt = 2_000L,
                    ),
                    log(
                        id = 3L,
                        day = DAY + 1L,
                        taskId = daily.id,
                        completedAt = 1_500L,
                    ),
                ),
                tasks = listOf(daily),
            )
                .flatMap { it.logs }

        assertEquals(
            listOf(3L, 2L),
            rows.map { it.id },
        )
    }

    @Test
    fun taskChangeMarksLog(): Unit {
        val row =
            mapper.logs(
                logs = listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = 7L,
                    )
                ),
                changingTaskIds = setOf(7L),
            )
                .single()
                .logs
                .single()

        assertTrue(
            row.isTaskCompletionChanging
        )
    }

    private fun mapCalendar(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
    ): HistoryStampCalendarUiState =
        mapper.stampCalendar(
            tasks = tasks,
            logs = logs,
            month = MONTH,
            currentDate = CURRENT_DATE,
            weekStart = DayOfWeek.MONDAY,
        )

    private fun mapTasks(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity> =
            emptyList(),
        changingTaskIds: Set<Long> =
            emptySet(),
    ): List<HistoryTaskUiState> =
        mapper.tasks(
            tasks = tasks,
            logs = logs,
            appDay =
                dayCalculator.containing(
                    currentTimestamp
                ),
            currentTimestampMillis =
                currentTimestamp,
            calculator =
                scheduleCalculator,
            changingTaskIds =
                changingTaskIds,
        )
    private fun task(
        id: Long,
        type: TaskScheduleTypeDb,
        name: String = "Task $id",
        category: String? = "Test",
        scheduledDay: Long? = null,
        weekdaysMask: Int? = null,
        intervalDays: Int? = null,
        intervalBasis:
        TaskIntervalBasisDb? = null,
        dueMinuteOfDay: Int? = null,
        archivedAt: Long? = null,
    ): TaskEntity =
        TaskEntity(
            id = id,
            name = name,
            category = category,
            displayOrder = id.toInt(),
            scheduleType = type,
            scheduledEpochDay = scheduledDay,
            dueMinuteOfDay = dueMinuteOfDay,
            recurrenceStartEpochDay =
                if (
                    type ==
                    TaskScheduleTypeDb.ONE_TIME
                ) {
                    null
                } else {
                    DAY
                },
            weekdaysMask = weekdaysMask,
            intervalDays = intervalDays,
            intervalBasis = intervalBasis,
            archivedAtEpochMillis = archivedAt,
            createdAtEpochMillis = 1_000L,
        )

    private fun log(
        id: Long,
        day: Long,
        taskId: Long = 1L,
        completedAt: Long = 1_000L,
        delta: Int = 1,
        reversesLogId: Long? = null,
        category: String? = "Test",
    ): TaskLogEntity =
        TaskLogEntity(
            id = id,
            taskId = taskId,
            taskNameSnapshot = "Task $id",
            categorySnapshot = category,
            scheduledEpochDay = day,
            completionTimestampMillis =
                completedAt,
            recordedTimestampMillis =
                completedAt,
            delta = delta,
            reversesLogId = reversesLogId,
        )

    private companion object {
        val DAY: Long =
            LocalDate.of(2026, 8, 24)
                .toEpochDay()

        val CURRENT_DATE: LocalDate =
            LocalDate.ofEpochDay(DAY)

        val MONTH: YearMonth =
            YearMonth.from(CURRENT_DATE)

    }
}