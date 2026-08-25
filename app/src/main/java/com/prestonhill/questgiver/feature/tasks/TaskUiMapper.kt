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
        operationError: String? = null,
        showHiddenToday: Boolean = false,
        changingTaskIds: Set<Long> = emptySet(),
    ): TaskScreenUiState {
        val visibleToday =
            mutableListOf<TaskRowUiState>()

        val hiddenToday =
            mutableListOf<TaskRowUiState>()

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

                visibleToday +=
                    task.toRow(
                        scheduledDate =
                            LocalDate.ofEpochDay(
                                completionDay
                            ),
                        completionEpochDay =
                            completionDay,
                        canComplete = true,
                        isCompleted = false,
                        isChanging =
                            task.id in changingTaskIds,
                    )

                return@forEach
            }

            val isHiddenToday =
                evaluation.wasCompletedToday ||
                        (
                                evaluation.isScheduledToday &&
                                        !evaluation.shouldShowToday
                                )

            if (isHiddenToday) {
                val completionDay =
                    requireNotNull(
                        evaluation.completionEpochDay
                    )

                hiddenToday +=
                    task.toRow(
                        scheduledDate =
                            LocalDate.ofEpochDay(
                                completionDay
                            ),
                        completionEpochDay =
                            completionDay,
                        canComplete = true,
                        isCompleted =
                            evaluation.isCompleted,
                        isChanging =
                            task.id in changingTaskIds,
                    )

                // Reserve this task for Today even when
                // the hidden toggle is off.
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
                    isCompleted = false,
                    isChanging =
                        task.id in changingTaskIds,
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
                (
                        visibleToday +
                                if (showHiddenToday) {
                                    hiddenToday
                                } else {
                                    emptyList()
                                }
                        ).sortedWith(rowOrder),
            hasHiddenToday = hiddenToday.isNotEmpty(),
            showHiddenToday =
                showHiddenToday &&
                        hiddenToday.isNotEmpty(),
            upcoming = upcomingDays,
            inspectedTaskId =
                inspectedTaskId?.takeIf { id ->
                    tasks.any { task ->
                        task.id == id
                    }
                },
            editor = editor,
            operationError = operationError,
        )
    }

    private fun TaskEntity.toRow(
        scheduledDate: LocalDate,
        completionEpochDay: Long,
        canComplete: Boolean,
        isCompleted: Boolean,
        isChanging: Boolean,
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
            isCompleted = isCompleted,
            displayOrder = displayOrder,
            isChanging = isChanging,
        )
}