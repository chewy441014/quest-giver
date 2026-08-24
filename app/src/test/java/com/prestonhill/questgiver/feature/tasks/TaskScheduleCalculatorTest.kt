package com.prestonhill.questgiver.feature.tasks

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScheduleCalculatorTest {
    private val zone =
        ZoneId.of("America/Chicago")

    private val dayCalculator =
        AppDayCalculator(
            dayBoundary = LocalTime.of(4, 0),
            zoneId = zone,
        )

    private val calculator =
        TaskScheduleCalculator(dayCalculator)

    @Test
    fun undatedTaskHidesAfterCompletion() {
        val date = LocalDate.of(2026, 8, 20)
        val task = task()

        val before =
            evaluate(
                task = task,
                date = date,
            )

        assertTrue(before.isScheduledToday)
        assertFalse(before.isCompleted)
        assertTrue(before.shouldShowToday)
        assertEquals(
            date.toEpochDay(),
            before.completionEpochDay,
        )

        val after =
            evaluate(
                task = task,
                date = date,
                logs = listOf(
                    completion(
                        id = 1,
                        scheduledDate = date,
                        completionDate = date,
                    )
                ),
            )

        assertTrue(after.isCompleted)
        assertFalse(after.shouldShowToday)
        assertTrue(after.upcomingDates.isEmpty())
    }

    @Test
    fun datedTaskAppearsUpcoming() {
        val current = LocalDate.of(2026, 8, 18)
        val scheduled = LocalDate.of(2026, 8, 20)

        val result =
            evaluate(
                task =
                    task(
                        scheduledDate = scheduled,
                    ),
                date = current,
            )

        assertFalse(result.isScheduledToday)
        assertFalse(result.shouldShowToday)
        assertEquals(
            listOf(scheduled),
            result.upcomingDates,
        )
    }

    @Test
    fun persistentOneTimeTaskRemainsVisible() {
        val scheduled = LocalDate.of(2026, 8, 18)
        val current = LocalDate.of(2026, 8, 20)

        val normal =
            evaluate(
                task =
                    task(
                        scheduledDate = scheduled,
                    ),
                date = current,
            )

        val persistent =
            evaluate(
                task =
                    task(
                        scheduledDate = scheduled,
                        remainsVisible = true,
                    ),
                date = current,
            )

        assertFalse(normal.shouldShowToday)
        assertTrue(persistent.shouldShowToday)

        assertEquals(
            scheduled.toEpochDay(),
            persistent.completionEpochDay,
        )
    }

    @Test
    fun dailyMissesDoNotCarryForward() {
        val current = LocalDate.of(2026, 8, 20)

        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                startDate =
                    LocalDate.of(2026, 8, 17),
            )

        val oldCompletion =
            completion(
                id = 1,
                scheduledDate =
                    LocalDate.of(2026, 8, 17),
                completionDate =
                    LocalDate.of(2026, 8, 17),
            )

        val incomplete =
            evaluate(
                task = task,
                date = current,
                logs = listOf(oldCompletion),
            )

        assertTrue(incomplete.isScheduledToday)
        assertFalse(incomplete.isCompleted)
        assertTrue(incomplete.shouldShowToday)

        assertEquals(
            current.toEpochDay(),
            incomplete.completionEpochDay,
        )

        val completed =
            evaluate(
                task = task,
                date = current,
                logs = listOf(
                    oldCompletion,
                    completion(
                        id = 2,
                        scheduledDate = current,
                        completionDate = current,
                    ),
                ),
            )

        assertTrue(completed.isCompleted)
        assertFalse(completed.shouldShowToday)
    }

    @Test
    fun weeklyUsesSelectedDays() {
        val current =
            LocalDate.of(2026, 8, 18)

        val wednesday =
            LocalDate.of(2026, 8, 19)

        val monday =
            LocalDate.of(2026, 8, 24)

        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.WEEKLY_DAYS,
                startDate =
                    LocalDate.of(2026, 8, 17),
                weekdaysMask =
                    weekdayMask(
                        wednesday,
                        monday,
                    ),
            )

        val result =
            evaluate(
                task = task,
                date = current,
            )

        assertFalse(result.isScheduledToday)
        assertFalse(result.shouldShowToday)

        assertEquals(
            listOf(wednesday, monday),
            result.upcomingDates,
        )
    }

    @Test
    fun fixedIntervalUsesAnchor() {
        val current =
            LocalDate.of(2026, 8, 20)

        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.INTERVAL,
                startDate =
                    LocalDate.of(2026, 8, 17),
                intervalDays = 3,
                intervalBasis =
                    TaskIntervalBasisDb.FIXED_SCHEDULE,
            )

        val result =
            evaluate(
                task = task,
                date = current,
            )

        assertTrue(result.isScheduledToday)
        assertTrue(result.shouldShowToday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 23),
                LocalDate.of(2026, 8, 26),
            ),
            result.upcomingDates,
        )
    }

    @Test
    fun completionIntervalMovesFromCompletion() {
        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.INTERVAL,
                startDate =
                    LocalDate.of(2026, 8, 17),
                intervalDays = 3,
                intervalBasis =
                    TaskIntervalBasisDb.FROM_COMPLETION,
            )

        val firstCompletion =
            completion(
                id = 1,
                scheduledDate =
                    LocalDate.of(2026, 8, 18),
                completionDate =
                    LocalDate.of(2026, 8, 18),
            )

        val beforeDue =
            evaluate(
                task = task,
                date =
                    LocalDate.of(2026, 8, 20),
                logs = listOf(firstCompletion),
            )

        assertFalse(beforeDue.shouldShowToday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 21)
            ),
            beforeDue.upcomingDates,
        )

        val completedLate =
            evaluate(
                task = task,
                date =
                    LocalDate.of(2026, 8, 22),
                logs = listOf(firstCompletion),
            )

        assertTrue(completedLate.isScheduledToday)
        assertTrue(completedLate.shouldShowToday)

        assertEquals(
            LocalDate.of(2026, 8, 22)
                .toEpochDay(),
            completedLate.completionEpochDay,
        )

        val secondCompletion =
            completion(
                id = 2,
                scheduledDate =
                    LocalDate.of(2026, 8, 22),
                completionDate =
                    LocalDate.of(2026, 8, 22),
            )

        val afterCompletion =
            evaluate(
                task = task,
                date =
                    LocalDate.of(2026, 8, 22),
                logs = listOf(
                    firstCompletion,
                    secondCompletion,
                ),
            )

        assertTrue(afterCompletion.isCompleted)
        assertFalse(afterCompletion.shouldShowToday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 25)
            ),
            afterCompletion.upcomingDates,
        )
    }

    @Test
    fun reversalMakesTaskIncomplete() {
        val date =
            LocalDate.of(2026, 8, 20)

        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                startDate = date,
            )

        val positive =
            completion(
                id = 1,
                scheduledDate = date,
                completionDate = date,
            )

        val reversal =
            positive.copy(
                id = 2,
                recordedTimestampMillis =
                    positive.recordedTimestampMillis + 1,
                delta = -1,
                reversesLogId = positive.id,
            )

        val result =
            evaluate(
                task = task,
                date = date,
                logs = listOf(
                    positive,
                    reversal,
                ),
            )

        assertFalse(result.isCompleted)
        assertTrue(result.shouldShowToday)
    }

    @Test
    fun earlyDueTimeUsesAppDay() {
        val appDate =
            LocalDate.of(2026, 8, 18)

        val task =
            task(
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                startDate = appDate,
                dueMinute = 2 * 60,
            )

        val beforeDue =
            evaluateAt(
                task = task,
                timestamp =
                    timestamp(
                        date =
                            LocalDate.of(2026, 8, 19),
                        hour = 1,
                        minute = 59,
                    ),
            )

        val afterDue =
            evaluateAt(
                task = task,
                timestamp =
                    timestamp(
                        date =
                            LocalDate.of(2026, 8, 19),
                        hour = 2,
                        minute = 1,
                    ),
            )

        val persistent =
            evaluateAt(
                task =
                    task.copy(
                        remainsVisibleAfterDue = true
                    ),
                timestamp =
                    timestamp(
                        date =
                            LocalDate.of(2026, 8, 19),
                        hour = 2,
                        minute = 1,
                    ),
            )

        assertTrue(beforeDue.shouldShowToday)
        assertFalse(afterDue.shouldShowToday)
        assertTrue(persistent.shouldShowToday)
    }

    private fun evaluate(
        task: TaskEntity,
        date: LocalDate,
        logs: List<TaskLogEntity> = emptyList(),
    ): TaskScheduleEvaluation =
        evaluateAt(
            task = task,
            timestamp =
                timestamp(
                    date = date,
                    hour = 12,
                ),
            logs = logs,
        )

    private fun evaluateAt(
        task: TaskEntity,
        timestamp: Long,
        logs: List<TaskLogEntity> = emptyList(),
    ): TaskScheduleEvaluation =
        calculator.evaluate(
            task = task,
            logs = logs,
            appDay =
                dayCalculator.containing(timestamp),
            currentTimestampMillis = timestamp,
        )

    private fun task(
        scheduleType: TaskScheduleTypeDb =
            TaskScheduleTypeDb.ONE_TIME,
        scheduledDate: LocalDate? = null,
        startDate: LocalDate? = null,
        weekdaysMask: Int? = null,
        intervalDays: Int? = null,
        intervalBasis: TaskIntervalBasisDb? = null,
        dueMinute: Int? = null,
        remainsVisible: Boolean = false,
    ): TaskEntity =
        TaskEntity(
            id = TASK_ID,
            name = "Test task",
            category = "General",
            displayOrder = 0,
            scheduleType = scheduleType,
            scheduledEpochDay =
                scheduledDate?.toEpochDay(),
            recurrenceStartEpochDay =
                startDate?.toEpochDay(),
            weekdaysMask = weekdaysMask,
            intervalDays = intervalDays,
            intervalBasis = intervalBasis,
            dueMinuteOfDay = dueMinute,
            remainsVisibleAfterDue =
                remainsVisible,
            createdAtEpochMillis =
                timestamp(
                    date =
                        LocalDate.of(2026, 8, 1),
                    hour = 12,
                ),
        )

    private fun completion(
        id: Long,
        scheduledDate: LocalDate,
        completionDate: LocalDate,
    ): TaskLogEntity {
        val timestamp =
            timestamp(
                date = completionDate,
                hour = 12,
            )

        return TaskLogEntity(
            id = id,
            taskId = TASK_ID,
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
                    (1 shl (
                            date.dayOfWeek.value - 1
                            ))
        }

    private fun timestamp(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): Long =
        date.atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    companion object {
        const val TASK_ID = 1L
    }
}