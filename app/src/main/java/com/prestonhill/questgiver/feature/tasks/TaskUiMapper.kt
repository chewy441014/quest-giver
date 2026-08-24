package com.prestonhill.questgiver.feature.tasks

import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import java.time.LocalDate
import java.time.LocalTime

class TaskUiMapper(
    private val calculator: TaskScheduleCalculator,
) {
    fun map(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        appDay: AppDay,
        currentTimestampMillis: Long,
        inspectedTaskId: Long? = null,
        editor: TaskEditorUiState? = null,
        confirmation: TaskDeleteUiState? = null,
        operationError: String? = null,
    ): TaskScreenUiState {
        val today = mutableListOf<TaskRowUiState>()
        val upcoming =
            mutableListOf<TaskRowUiState>()

        tasks.forEach { task ->
            val evaluation =
                calculator.evaluate(
                    task = task,
                    logs = logs,
                    appDay = appDay,
                    currentTimestampMillis =
                        currentTimestampMillis,
                )

            if (evaluation.shouldShowToday) {
                val completionDay =
                    requireNotNull(
                        evaluation.completionEpochDay
                    )

                today +=
                    task.toRow(
                        scheduledDate =
                            LocalDate.ofEpochDay(
                                completionDay
                            ),
                        completionEpochDay =
                            completionDay,
                        canComplete = true,
                    )

                // A task shown today cannot also appear
                // in Upcoming.
                return@forEach
            }

            val nextDate =
                evaluation.upcomingDates
                    .firstOrNull()
                    ?: return@forEach

            upcoming +=
                task.toRow(
                    scheduledDate = nextDate,
                    completionEpochDay =
                        nextDate.toEpochDay(),
                    canComplete = false,
                )
        }

        val rowOrder =
            compareBy<TaskRowUiState> {
                it.displayOrder
            }
                .thenBy {
                    it.dueTime == null
                }
                .thenBy {
                    it.dueTime
                }
                .thenBy {
                    it.name.lowercase()
                }

        val upcomingDays =
            upcoming
                .groupBy(TaskRowUiState::scheduledDate)
                .mapNotNull { (date, rows) ->
                    date?.let {
                        TaskDayUiState(
                            date = it,
                            tasks =
                                rows.sortedWith(
                                    rowOrder
                                ),
                        )
                    }
                }
                .sortedBy(TaskDayUiState::date)

        return TaskScreenUiState(
            today =
                today.sortedWith(rowOrder),
            upcoming = upcomingDays,
            inspectedTaskId =
                inspectedTaskId?.takeIf { id ->
                    tasks.any { task ->
                        task.id == id
                    }
                },
            editor = editor,
            confirmation = confirmation,
            operationError = operationError,
        )
    }

    private fun TaskEntity.toRow(
        scheduledDate: LocalDate,
        completionEpochDay: Long,
        canComplete: Boolean,
    ): TaskRowUiState =
        TaskRowUiState(
            id = id,
            name = name,
            category = category,
            scheduledDate = scheduledDate,
            dueTime =
                dueMinuteOfDay?.let { minute ->
                    LocalTime.ofSecondOfDay(
                        minute * 60L
                    )
                },
            completionEpochDay =
                completionEpochDay,
            canComplete = canComplete,
            displayOrder = displayOrder,
        )
}