package com.prestonhill.questgiver.feature.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class TaskScheduleType {
    ONE_TIME,
    DAILY,
    WEEKLY_DAYS,
    INTERVAL,
}

enum class TaskIntervalBasis {
    FIXED_SCHEDULE,
    FROM_COMPLETION,
}

data class TaskRowUiState(
    val id: Long,
    val name: String,
    val category: String?,
    val scheduledDate: LocalDate?,
    val dueTime: LocalTime?,
    val completionEpochDay: Long,
    val canComplete: Boolean,
    val displayOrder: Int,
    val isCompleted: Boolean,
)

data class TaskDayUiState(
    val date: LocalDate,
    val tasks: List<TaskRowUiState>,
)

data class TaskEditorUiState(
    val taskId: Long? = null,
    val name: String = "",
    val category: String = "",
    val scheduleType: TaskScheduleType =
        TaskScheduleType.ONE_TIME,
    val scheduledDate: LocalDate? = null,
    val recurrenceStartDate: LocalDate? = null,
    val selectedWeekdays: Set<DayOfWeek> =
        emptySet(),
    val intervalDays: String = "3",
    val intervalBasis: TaskIntervalBasis =
        TaskIntervalBasis.FIXED_SCHEDULE,
    val dueTime: LocalTime? = null,
    val remainsVisibleAfterDue: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean
        get() = taskId != null

    val canSave: Boolean
        get() {
            if (
                name.isBlank() ||
                isSaving
            ) {
                return false
            }

            return when (scheduleType) {
                TaskScheduleType.ONE_TIME ->
                    dueTime == null ||
                            scheduledDate != null

                TaskScheduleType.DAILY ->
                    recurrenceStartDate != null

                TaskScheduleType.WEEKLY_DAYS ->
                    recurrenceStartDate != null &&
                            selectedWeekdays.isNotEmpty()

                TaskScheduleType.INTERVAL ->
                    recurrenceStartDate != null &&
                            intervalDays
                                .toIntOrNull()
                                ?.let { it > 0 } == true
            }
        }
}

data class TaskDeleteUiState(
    val taskId: Long,
    val taskName: String,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

data class TaskScreenUiState(
    val today: List<TaskRowUiState> = emptyList(),
    val upcoming: List<TaskDayUiState> = emptyList(),
    val inspectedTaskId: Long? = null,
    val editor: TaskEditorUiState? = null,
    val confirmation: TaskDeleteUiState? = null,
    val operationError: String? = null,
    val hasHiddenToday: Boolean = false,
    val showHiddenToday: Boolean = false,
)