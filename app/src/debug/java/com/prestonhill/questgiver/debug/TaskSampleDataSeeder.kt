package com.prestonhill.questgiver.debug

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class TaskSeedResult(
    val tasks: Int,
    val logs: Int,
)

class TaskSampleDataSeeder(
    database: QuestGiverDatabase,
    private val clock: Clock,
) {
    private val repository =
        TaskRepository(database)

    suspend fun seed(
        currentDate: LocalDate,
        calculator: AppDayCalculator,
    ): TaskSeedResult {
        val earliestDate =
            currentDate.minusDays(419)

        val createdAt =
            calculator
                .forDate(earliestDate)
                .startTimestampMillis

        var taskCount = 0
        var logCount = 0

        suspend fun create(
            name: String,
            category: String?,
            scheduleType:
            TaskScheduleTypeDb,
            scheduledDate:
            LocalDate? = null,
            recurrenceStart:
            LocalDate? = null,
            weekdaysMask:
            Int? = null,
            intervalDays:
            Int? = null,
            intervalBasis:
            TaskIntervalBasisDb? = null,
            dueMinuteOfDay:
            Int? = null,
            remainsVisible:
            Boolean = false,
        ): Long {
            val id =
                repository.createTask(
                    TaskEntity(
                        name = name,
                        category = category,
                        displayOrder =
                            taskCount,
                        scheduleType =
                            scheduleType,
                        scheduledEpochDay =
                            scheduledDate
                                ?.toEpochDay(),
                        recurrenceStartEpochDay =
                            recurrenceStart
                                ?.toEpochDay(),
                        weekdaysMask =
                            weekdaysMask,
                        intervalDays =
                            intervalDays,
                        intervalBasis =
                            intervalBasis,
                        dueMinuteOfDay =
                            dueMinuteOfDay,
                        remainsVisibleAfterDue =
                            remainsVisible,
                        createdAtEpochMillis =
                            createdAt +
                                    taskCount *
                                    1_000L,
                    )
                )

            taskCount += 1
            return id
        }

        suspend fun complete(
            taskId: Long,
            date: LocalDate,
            time: LocalTime,
        ) {
            val timestamp =
                calculator.timestampFor(
                    appDate = date,
                    time = time,
                )

            val result =
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay =
                        date.toEpochDay(),
                    completionTimestampMillis =
                        timestamp,
                    recordedTimestampMillis =
                        timestamp,
                )

            check(
                result ==
                        TaskCompletionResult.SUCCESS
            )

            logCount += 1
        }

        val dailyPlan =
            create(
                name = "Review daily plan",
                category = "Planning",
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                recurrenceStart =
                    earliestDate,
                dueMinuteOfDay = 8 * 60,
            )

        val strengthWorkout =
            create(
                name = "Strength workout",
                category = "Health",
                scheduleType =
                    TaskScheduleTypeDb
                        .WEEKLY_DAYS,
                recurrenceStart =
                    earliestDate,
                weekdaysMask =
                    weekdayMask(
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.FRIDAY,
                    ),
                dueMinuteOfDay = 17 * 60,
            )

        val waterFilter =
            create(
                name = "Change water filter",
                category = "Home",
                scheduleType =
                    TaskScheduleTypeDb.INTERVAL,
                recurrenceStart =
                    earliestDate,
                intervalDays = 14,
                intervalBasis =
                    TaskIntervalBasisDb
                        .FIXED_SCHEDULE,
            )

        val callFamily =
            create(
                name = "Call family",
                category = "Personal",
                scheduleType =
                    TaskScheduleTypeDb
                        .WEEKLY_DAYS,
                recurrenceStart =
                    earliestDate,
                weekdaysMask =
                    weekdayMask(
                        DayOfWeek.SUNDAY
                    ),
                dueMinuteOfDay = 19 * 60,
            )

        create(
            name = "Upcoming appointment",
            category = "Personal",
            scheduleType =
                TaskScheduleTypeDb.ONE_TIME,
            scheduledDate =
                currentDate.plusDays(3),
            dueMinuteOfDay = 10 * 60,
            remainsVisible = true,
        )

        val completedOneTime =
            create(
                name = "Submit completed project",
                category = "Work",
                scheduleType =
                    TaskScheduleTypeDb.ONE_TIME,
                scheduledDate =
                    currentDate.minusDays(45),
                dueMinuteOfDay = 16 * 60,
                remainsVisible = true,
            )

        val archivedDaily =
            create(
                name = "Old daily review",
                category = "Archive sample",
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
                recurrenceStart =
                    earliestDate,
            )

        for (offset in 0L..419L) {
            val date =
                currentDate.minusDays(offset)

            if (offset % 7L != 6L) {
                complete(
                    taskId = dailyPlan,
                    date = date,
                    time =
                        LocalTime.of(8, 0),
                )
            }

            if (
                date.dayOfWeek in
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.FRIDAY,
                ) &&
                offset % 13L != 12L
            ) {
                complete(
                    taskId =
                        strengthWorkout,
                    date = date,
                    time =
                        LocalTime.of(17, 30),
                )
            }

            if (offset % 14L == 0L) {
                complete(
                    taskId = waterFilter,
                    date = date,
                    time =
                        LocalTime.of(18, 0),
                )
            }

            if (
                date.dayOfWeek ==
                DayOfWeek.SUNDAY &&
                offset % 4L != 3L
            ) {
                complete(
                    taskId = callFamily,
                    date = date,
                    time =
                        LocalTime.of(19, 15),
                )
            }

            if (
                offset >= 90L &&
                offset % 5L == 0L
            ) {
                complete(
                    taskId = archivedDaily,
                    date = date,
                    time =
                        LocalTime.of(20, 0),
                )
            }
        }

        val completedDate =
            currentDate.minusDays(45)

        complete(
            taskId = completedOneTime,
            date = completedDate,
            time = LocalTime.of(15, 30),
        )

        check(
            repository.archiveTask(
                taskId = completedOneTime,
                timestampMillis =
                    clock.millis(),
            )
        )

        check(
            repository.archiveTask(
                taskId = archivedDaily,
                timestampMillis =
                    clock.millis(),
            )
        )

        return TaskSeedResult(
            tasks = taskCount,
            logs = logCount,
        )
    }

    private fun weekdayMask(
        vararg days: DayOfWeek,
    ): Int =
        days.fold(0) { mask, day ->
            mask or
                    (
                            1 shl
                                    (
                                            day.value - 1
                                            )
                            )
        }
}