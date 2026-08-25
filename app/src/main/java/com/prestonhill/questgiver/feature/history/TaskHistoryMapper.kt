package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.feature.tasks.TaskScheduleCalculator
import java.time.LocalDate

class TaskHistoryMapper {
    fun tasks(
        tasks: List<TaskEntity>,
    ): List<HistoryTaskUiState> =
        tasks.map { task ->
            HistoryTaskUiState(
                id = task.id,
                name = task.name,
                category = task.category,
                schedule = task.scheduleText(),
                isArchived =
                    task.archivedAtEpochMillis != null,
            )
        }

    fun tasks(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        appDay: AppDay,
        currentTimestampMillis: Long,
        calculator: TaskScheduleCalculator,
        changingTaskIds: Set<Long> =
            emptySet(),
    ): List<HistoryTaskUiState> =
        tasks.map { task ->
            val evaluation =
                calculator.evaluate(
                    task = task,
                    logs = logs,
                    appDay = appDay,
                    currentTimestampMillis =
                        currentTimestampMillis,
                )

            val canChange =
                task.archivedAtEpochMillis == null &&
                        evaluation.completionEpochDay != null &&
                        (
                                evaluation.isScheduledToday ||
                                        evaluation.shouldShowToday ||
                                        evaluation.isCompleted
                                )

            HistoryTaskUiState(
                id = task.id,
                name = task.name,
                category = task.category,
                schedule = task.scheduleText(),
                completionEpochDay =
                    evaluation.completionEpochDay,
                isCompleted =
                    evaluation.isCompleted,
                canChangeCompletion =
                    canChange,
                isChanging =
                    task.id in changingTaskIds,
                isArchived =
                    task.archivedAtEpochMillis != null,
            )
        }

    fun logs(
        logs: List<TaskLogEntity>,
        tasks: List<TaskEntity> = emptyList(),
        changingTaskIds: Set<Long> = emptySet(),
    ): List<HistoryTaskDayUiState> {
        val correctedIds =
            logs.asSequence()
                .filter { it.delta == -1 }
                .mapNotNull { it.reversesLogId }
                .toSet()

        val tasksById =
            tasks.associateBy { it.id }

        val activeLogs =
            logs.asSequence()
                .filter { log ->
                    log.delta == 1 &&
                            log.id !in correctedIds
                }
                .sortedWith(
                    compareByDescending<
                            TaskLogEntity
                            > {
                        it.completionTimestampMillis
                    }
                        .thenByDescending {
                            it.id
                        }
                )
                .distinctBy { log ->
                    val task =
                        log.taskId?.let {
                            tasksById[it]
                        }

                    when {
                        log.taskId == null ->
                            TaskLogSlot(
                                logId = log.id,
                            )

                        task?.scheduleType ==
                                TaskScheduleTypeDb.ONE_TIME ->
                            TaskLogSlot(
                                taskId = log.taskId,
                            )

                        else ->
                            TaskLogSlot(
                                taskId = log.taskId,
                                scheduledEpochDay =
                                    log.scheduledEpochDay,
                            )
                    }
                }

        return activeLogs
            .map { log ->
                HistoryTaskLogUiState(
                    id = log.id,
                    taskId = log.taskId,
                    taskName =
                        log.taskNameSnapshot,
                    category =
                        log.categorySnapshot,
                    date =
                        LocalDate.ofEpochDay(
                            log.scheduledEpochDay
                        ),
                    completedAtMillis =
                        log.completionTimestampMillis,
                    isTaskCompletionChanging =
                        log.taskId != null &&
                                log.taskId in
                                changingTaskIds,
                )
            }
            .sortedWith(
                compareByDescending<
                        HistoryTaskLogUiState
                        > { it.date }
                    .thenByDescending {
                        it.completedAtMillis
                    }
                    .thenByDescending {
                        it.id
                    }
            )
            .groupBy { it.date }
            .map { (date, dayLogs) ->
                HistoryTaskDayUiState(
                    date = date,
                    logs = dayLogs,
                )
            }
    }
}

private data class TaskLogSlot(
    val taskId: Long? = null,
    val scheduledEpochDay: Long? = null,
    val logId: Long? = null,
)

private fun TaskEntity.scheduleText(): String =
    when (scheduleType) {
        TaskScheduleTypeDb.ONE_TIME -> {
            val date =
                scheduledEpochDay?.let(
                    LocalDate::ofEpochDay
                )

            if (date == null) {
                "One time · Anytime"
            } else {
                "One time · $date"
            }
        }

        TaskScheduleTypeDb.DAILY ->
            "Daily"

        TaskScheduleTypeDb.WEEKLY_DAYS ->
            weeklyText(weekdaysMask ?: 0)

        TaskScheduleTypeDb.INTERVAL -> {
            val days = intervalDays ?: 1

            if (
                intervalBasis ==
                TaskIntervalBasisDb.FROM_COMPLETION
            ) {
                "Every $days days after completion"
            } else {
                "Every $days days"
            }
        }
    }

private fun weeklyText(mask: Int): String {
    val labels =
        listOf(
            "Mon",
            "Tue",
            "Wed",
            "Thu",
            "Fri",
            "Sat",
            "Sun",
        )

    val selected =
        labels.filterIndexed { index, _ ->
            mask and (1 shl index) != 0
        }

    return selected.joinToString(
        prefix = "Weekly · ",
        separator = ", ",
    )
}