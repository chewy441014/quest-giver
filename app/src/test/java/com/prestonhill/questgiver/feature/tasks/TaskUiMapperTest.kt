package com.prestonhill.questgiver.feature.tasks

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUiMapperTest {
    private val zone =
        ZoneId.of("America/Chicago")

    private val dayCalculator =
        AppDayCalculator(
            dayBoundary = LocalTime.of(4, 0),
            zoneId = zone,
        )

    private val mapper =
        TaskUiMapper(
            TaskScheduleCalculator(
                dayCalculator
            )
        )

    private val currentDate =
        LocalDate.of(2026, 8, 20)

    @Test
    fun todayTaskIsNotRepeatedUpcoming() {
        val task =
            task(
                id = 1,
                scheduleType =
                    TaskScheduleTypeDb.WEEKLY_DAYS,
                startDate =
                    LocalDate.of(2026, 8, 17),
                weekdaysMask =
                    weekdayMask(
                        currentDate,
                        LocalDate.of(2026, 8, 23),
                    ),
            )

        val state =
            map(tasks = listOf(task))

        assertEquals(1, state.today.size)
        assertEquals(task.id, state.today.single().id)
        assertTrue(state.upcoming.isEmpty())

        assertEquals(
            1,
            state.occurrencesOf(task.id),
        )
    }

    @Test
    fun completionIsReservedForToday() {
        val task =
            task(
                id = 1,
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                startDate =
                    LocalDate.of(2026, 8, 17),
            )

        val state =
            map(
                tasks = listOf(task),
                logs = listOf(
                    completion(
                        id = 1,
                        taskId = task.id,
                        scheduledDate = currentDate,
                    )
                ),
            )

        assertTrue(state.today.isEmpty())
        assertTrue(state.upcoming.isEmpty())
        assertTrue(state.hasHiddenToday)

        assertEquals(
            0,
            state.occurrencesOf(task.id),
        )
    }

    @Test
    fun upcomingUsesClosestDateOnly() {
        val sunday =
            LocalDate.of(2026, 8, 23)

        val tuesday =
            LocalDate.of(2026, 8, 25)

        val task =
            task(
                id = 1,
                scheduleType =
                    TaskScheduleTypeDb.WEEKLY_DAYS,
                startDate =
                    LocalDate.of(2026, 8, 17),
                weekdaysMask =
                    weekdayMask(
                        sunday,
                        tuesday,
                    ),
            )

        val state =
            map(tasks = listOf(task))

        assertTrue(state.today.isEmpty())
        assertEquals(1, state.upcoming.size)
        assertEquals(
            sunday,
            state.upcoming.single().date,
        )

        assertEquals(
            1,
            state.occurrencesOf(task.id),
        )
    }

    @Test
    fun upcomingGroupsAndSortsTasks() {
        val tomorrow =
            currentDate.plusDays(1)

        val followingDay =
            currentDate.plusDays(2)

        val later =
            task(
                id = 1,
                name = "Later",
                scheduledDate = followingDay,
            )

        val second =
            task(
                id = 2,
                name = "Second",
                scheduledDate = tomorrow,
                displayOrder = 1,
            )

        val first =
            task(
                id = 3,
                name = "First",
                scheduledDate = tomorrow,
                displayOrder = 0,
            )

        val state =
            map(
                tasks = listOf(
                    later,
                    second,
                    first,
                )
            )

        assertEquals(
            listOf(
                tomorrow,
                followingDay,
            ),
            state.upcoming.map { it.date },
        )

        assertEquals(
            listOf("First", "Second"),
            state.upcoming
                .first()
                .tasks
                .map { it.name },
        )
    }

    @Test
    fun completedOneTimeTaskIsAbsent() {
        val task =
            task(
                id = 1,
                scheduledDate = currentDate,
            )

        val state =
            map(
                tasks = listOf(task),
                logs = listOf(
                    completion(
                        id = 1,
                        taskId = task.id,
                        scheduledDate = currentDate,
                    )
                ),
            )

        assertTrue(state.today.isEmpty())
        assertTrue(state.upcoming.isEmpty())
        assertEquals(
            0,
            state.occurrencesOf(task.id),
        )
    }

    @Test
    fun invalidInspectionIsCleared() {
        val task =
            task(
                id = 1,
                scheduledDate = currentDate,
            )

        val state =
            map(
                tasks = listOf(task),
                inspectedTaskId = 999,
            )

        assertNull(state.inspectedTaskId)
    }

    @Test
    fun revealedCompletionCanChange(): Unit {
        val task =
            task(
                id = 1L,
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                startDate = currentDate,
            )

        val state =
            map(
                tasks = listOf(task),
                logs = listOf(
                    completion(
                        id = 1L,
                        taskId = task.id,
                        scheduledDate =
                            currentDate,
                    )
                ),
                showHiddenToday = true,
            )

        val row = state.today.single()

        assertTrue(row.isCompleted)
        assertTrue(row.canComplete)
    }

    private fun map(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity> = emptyList(),
        inspectedTaskId: Long? = null,
        showHiddenToday: Boolean = false,
    ): TaskScreenUiState {
        val timestamp =
            timestamp(
                date = currentDate,
                hour = 12,
            )

        return mapper.map(
            tasks = tasks,
            logs = logs,
            appDay =
                dayCalculator.containing(timestamp),
            currentTimestampMillis = timestamp,
            inspectedTaskId = inspectedTaskId,
            showHiddenToday = showHiddenToday,
        )
    }

    private fun task(
        id: Long,
        name: String = "Test task",
        scheduleType: TaskScheduleTypeDb =
            TaskScheduleTypeDb.ONE_TIME,
        scheduledDate: LocalDate? = null,
        startDate: LocalDate? = null,
        weekdaysMask: Int? = null,
        displayOrder: Int = 0,
        archivedAt: Long? = null,
    ): TaskEntity =
        TaskEntity(
            id = id,
            name = name,
            category = "General",
            displayOrder = displayOrder,
            scheduleType = scheduleType,
            scheduledEpochDay =
                scheduledDate?.toEpochDay(),
            recurrenceStartEpochDay =
                startDate?.toEpochDay(),
            weekdaysMask = weekdaysMask,
            archivedAtEpochMillis = archivedAt,
            createdAtEpochMillis =
                timestamp(
                    date =
                        LocalDate.of(2026, 8, 1),
                    hour = 12,
                ),
        )

    private fun completion(
        id: Long,
        taskId: Long,
        scheduledDate: LocalDate,
    ): TaskLogEntity {
        val timestamp =
            timestamp(
                date = scheduledDate,
                hour = 12,
            )

        return TaskLogEntity(
            id = id,
            taskId = taskId,
            taskNameSnapshot = "Test task",
            categorySnapshot = "General",
            scheduledEpochDay =
                scheduledDate.toEpochDay(),
            completionTimestampMillis =
                timestamp,
            recordedTimestampMillis =
                timestamp,
            delta = 1,
        )
    }

    private fun weekdayMask(
        vararg dates: LocalDate,
    ): Int =
        dates.fold(0) { mask, date ->
            mask or
                    (
                            1 shl
                                    (
                                            date.dayOfWeek.value -
                                                    1
                                            )
                            )
        }

    private fun timestamp(
        date: LocalDate,
        hour: Int,
    ): Long =
        date.atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun TaskScreenUiState.occurrencesOf(
        taskId: Long,
    ): Int =
        today.count { it.id == taskId } +
                upcoming.sumOf { day ->
                    day.tasks.count {
                        it.id == taskId
                    }
                }
}