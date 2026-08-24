package com.prestonhill.questgiver.feature.tasks

import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import java.time.LocalDate
import java.time.LocalTime

data class TaskScheduleEvaluation(
    val isScheduledToday: Boolean,
    val isCompleted: Boolean,
    val shouldShowToday: Boolean,
    val completionEpochDay: Long?,
    val dueTimestampMillis: Long?,
    val upcomingDates: List<LocalDate>,
    val wasCompletedToday: Boolean,
)

class TaskScheduleCalculator(
    private val appDayCalculator: AppDayCalculator,
) {
    fun evaluate(
        task: TaskEntity,
        logs: List<TaskLogEntity>,
        appDay: AppDay,
        currentTimestampMillis: Long,
    ): TaskScheduleEvaluation {
        val currentDate = appDay.date

        val activeLogs =
            activeLogs(
                taskId = task.id,
                logs = logs,
            )

        val wasCompletedToday =
            activeLogs.any { log ->
                appDayCalculator
                    .containing(
                        log.completionTimestampMillis
                    )
                    .date == currentDate
            }

        val completed =
            if (
                task.scheduleType ==
                TaskScheduleTypeDb.ONE_TIME
            ) {
                activeLogs.isNotEmpty()
            } else {
                activeLogs.any { log ->
                    log.scheduledEpochDay ==
                            currentDate.toEpochDay()
                }
            }

        val scheduledToday =
            isActionableOn(
                task = task,
                date = currentDate,
                activeLogs = activeLogs,
            )

        val completionEpochDay =
            when {
                task.scheduleType ==
                        TaskScheduleTypeDb.ONE_TIME ->
                    task.scheduledEpochDay
                        ?: currentDate.toEpochDay()

                scheduledToday || completed ->
                    currentDate.toEpochDay()

                else -> null
            }

        val dueDate =
            when {
                task.scheduleType ==
                        TaskScheduleTypeDb.ONE_TIME ->
                    task.scheduledEpochDay
                        ?.let(LocalDate::ofEpochDay)
                        ?: currentDate

                else -> currentDate
            }

        val dueTimestamp =
            task.dueMinuteOfDay?.let { minute ->
                appDayCalculator.timestampFor(
                    appDate = dueDate,
                    time =
                        LocalTime.ofSecondOfDay(
                            minute * 60L
                        ),
                )
            }

        val dueTimePassed =
            dueTimestamp != null &&
                    currentTimestampMillis >
                    dueTimestamp

        val shouldShow =
            when {
                completed -> false

                task.scheduleType ==
                        TaskScheduleTypeDb.ONE_TIME ->
                    showOneTime(
                        task = task,
                        currentDate = currentDate,
                        dueTimePassed = dueTimePassed,
                    )

                !scheduledToday -> false

                dueTimePassed &&
                        !task.remainsVisibleAfterDue ->
                    false

                else -> true
            }

        return TaskScheduleEvaluation(
            isScheduledToday = scheduledToday,
            isCompleted = completed,
            shouldShowToday = shouldShow,
            completionEpochDay =
                completionEpochDay,
            dueTimestampMillis = dueTimestamp,
            upcomingDates =
                upcomingDates(
                    task = task,
                    activeLogs = activeLogs,
                    currentDate = currentDate,

                ),
            wasCompletedToday = wasCompletedToday,
        )
    }

    private fun showOneTime(
        task: TaskEntity,
        currentDate: LocalDate,
        dueTimePassed: Boolean,
    ): Boolean {
        val scheduledDate =
            task.scheduledEpochDay
                ?.let(LocalDate::ofEpochDay)
                ?: return true

        return when {
            currentDate.isBefore(scheduledDate) ->
                false

            currentDate == scheduledDate ->
                !dueTimePassed ||
                        task.remainsVisibleAfterDue

            else ->
                task.remainsVisibleAfterDue
        }
    }

    private fun isActionableOn(
        task: TaskEntity,
        date: LocalDate,
        activeLogs: List<TaskLogEntity>,
    ): Boolean =
        when (task.scheduleType) {
            TaskScheduleTypeDb.ONE_TIME ->
                task.scheduledEpochDay
                    ?.let(LocalDate::ofEpochDay)
                    ?.let { it == date }
                    ?: true

            TaskScheduleTypeDb.DAILY ->
                !date.isBefore(
                    requireStartDate(task)
                )

            TaskScheduleTypeDb.WEEKLY_DAYS ->
                !date.isBefore(
                    requireStartDate(task)
                ) &&
                        task.occursOn(date)

            TaskScheduleTypeDb.INTERVAL ->
                when (
                    requireNotNull(
                        task.intervalBasis
                    )
                ) {
                    TaskIntervalBasisDb.FIXED_SCHEDULE ->
                        occursOnFixedInterval(
                            task = task,
                            date = date,
                        )

                    TaskIntervalBasisDb.FROM_COMPLETION -> {
                        val dueDate =
                            completionBasedDueDate(
                                task = task,
                                activeLogs = activeLogs,
                            )

                        !date.isBefore(dueDate)
                    }
                }
        }

    private fun upcomingDates(
        task: TaskEntity,
        activeLogs: List<TaskLogEntity>,
        currentDate: LocalDate,
    ): List<LocalDate> {
        if (
            task.scheduleType ==
            TaskScheduleTypeDb.ONE_TIME
        ) {
            if (activeLogs.isNotEmpty()) {
                return emptyList()
            }

            val scheduledDate =
                task.scheduledEpochDay
                    ?.let(LocalDate::ofEpochDay)
                    ?: return emptyList()

            return if (
                scheduledDate.isAfter(currentDate) &&
                !scheduledDate.isAfter(
                    currentDate.plusDays(7)
                )
            ) {
                listOf(scheduledDate)
            } else {
                emptyList()
            }
        }

        if (
            task.scheduleType ==
            TaskScheduleTypeDb.INTERVAL &&
            task.intervalBasis ==
            TaskIntervalBasisDb.FROM_COMPLETION
        ) {
            val dueDate =
                completionBasedDueDate(
                    task = task,
                    activeLogs = activeLogs,
                )

            return if (
                dueDate.isAfter(currentDate) &&
                !dueDate.isAfter(
                    currentDate.plusDays(7)
                )
            ) {
                listOf(dueDate)
            } else {
                emptyList()
            }
        }

        return (1L..7L)
            .map(currentDate::plusDays)
            .filter { date ->
                isActionableOn(
                    task = task,
                    date = date,
                    activeLogs = activeLogs,
                )
            }
    }

    private fun occursOnFixedInterval(
        task: TaskEntity,
        date: LocalDate,
    ): Boolean {
        val start = requireStartDate(task)

        if (date.isBefore(start)) {
            return false
        }

        val intervalDays =
            requireNotNull(task.intervalDays)

        val daysSinceStart =
            date.toEpochDay() -
                    start.toEpochDay()

        return daysSinceStart % intervalDays == 0L
    }

    private fun completionBasedDueDate(
        task: TaskEntity,
        activeLogs: List<TaskLogEntity>,
    ): LocalDate {
        val intervalDays =
            requireNotNull(task.intervalDays)
                .toLong()

        val latestCompletion =
            activeLogs.maxByOrNull { log ->
                log.completionTimestampMillis
            }

        return if (latestCompletion == null) {
            requireStartDate(task)
        } else {
            appDayCalculator
                .containing(
                    latestCompletion
                        .completionTimestampMillis
                )
                .date
                .plusDays(intervalDays)
        }
    }

    private fun requireStartDate(
        task: TaskEntity,
    ): LocalDate =
        LocalDate.ofEpochDay(
            requireNotNull(
                task.recurrenceStartEpochDay
            )
        )

    private fun TaskEntity.occursOn(
        date: LocalDate,
    ): Boolean {
        val mask =
            requireNotNull(weekdaysMask)

        val bit =
            1 shl (date.dayOfWeek.value - 1)

        return mask and bit != 0
    }

    private fun activeLogs(
        taskId: Long,
        logs: List<TaskLogEntity>,
    ): List<TaskLogEntity> {
        val reversedIds =
            logs.asSequence()
                .filter { log ->
                    log.delta == -1
                }
                .mapNotNull { log ->
                    log.reversesLogId
                }
                .toSet()

        return logs.filter { log ->
            log.taskId == taskId &&
                    log.delta == 1 &&
                    log.id !in reversedIds
        }
    }
}