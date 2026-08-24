package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHistoryMapperTest {
    private val mapper = TaskHistoryMapper()

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
                        completedAt = 100L,
                    ),
                    log(
                        id = 2L,
                        day = DAY,
                        completedAt = 200L,
                    ),
                    log(
                        id = 3L,
                        day = DAY,
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
    fun reversalMarksCorrected(): Unit {
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

        val logs =
            mapped.single().logs

        assertEquals(1, logs.size)
        assertEquals(10L, logs.single().id)
        assertTrue(logs.single().isCorrected)
    }

    @Test
    fun activeLogPermissions(): Unit {
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

        assertTrue(row.canOpenTask)
        assertTrue(row.canCorrect)
        assertFalse(row.canDelete)
    }

    @Test
    fun orphanLogPermissions(): Unit {
        val row =
            mapper.logs(
                listOf(
                    log(
                        id = 1L,
                        day = DAY,
                        taskId = null,
                    )
                )
            )
                .single()
                .logs
                .single()

        assertFalse(row.canOpenTask)
        assertFalse(row.canCorrect)
        assertTrue(row.canDelete)
    }

    @Test
    fun correctedLogPermissions(): Unit {
        val row =
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
                )
            )
                .single()
                .logs
                .single()

        assertTrue(row.canOpenTask)
        assertFalse(row.canCorrect)
        assertFalse(row.canDelete)
    }

    private fun task(
        id: Long,
        type: TaskScheduleTypeDb,
        scheduledDay: Long? = null,
        weekdaysMask: Int? = null,
        intervalDays: Int? = null,
        intervalBasis: TaskIntervalBasisDb? = null,
    ): TaskEntity =
        TaskEntity(
            id = id,
            name = "Task $id",
            category = "Test",
            displayOrder = id.toInt(),
            scheduleType = type,
            scheduledEpochDay = scheduledDay,
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
            createdAtEpochMillis = 1_000L,
        )

    private fun log(
        id: Long,
        day: Long,
        taskId: Long? = 1L,
        completedAt: Long = 1_000L,
        delta: Int = 1,
        reversesLogId: Long? = null,
    ): TaskLogEntity =
        TaskLogEntity(
            id = id,
            taskId = taskId,
            taskNameSnapshot = "Task $id",
            categorySnapshot = "Test",
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
    }
}