package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
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
            )
        }

    fun logs(
        logs: List<TaskLogEntity>,
    ): List<HistoryTaskDayUiState> {
        val correctedIds =
            logs.asSequence()
                .filter { it.delta == -1 }
                .mapNotNull { it.reversesLogId }
                .toSet()

        return logs.asSequence()
            .filter { it.delta == 1 }
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
                    isCorrected =
                        log.id in correctedIds,
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