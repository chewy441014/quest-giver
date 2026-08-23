package com.prestonhill.questgiver.feature.habits

enum class HabitCategory {
    MORNING,
    ANYTIME,
    BEFORE_BED
}

enum class HabitDueStatus {
    DUE,
    COMPLETED,
    NOT_DUE
}

enum class HabitScheduleType {
    DAILY,
    WEEKLY_TARGET,
    INTERVAL
}

enum class HabitIntervalBasis {
    FIXED_SCHEDULE,
    FROM_COMPLETION
}

enum class HabitScheduleVisibility {
    ALWAYS,
    WHEN_DUE,
    HIDE_AFTER_TARGET
}

data class HabitEditorUiState(
    val habitId: Long? = null,
    val name: String = "",
    val category: HabitCategory = HabitCategory.ANYTIME,
    val allowsMultipleCompletions: Boolean = false,
    val scheduleType: HabitScheduleType =
        HabitScheduleType.DAILY,
    val scheduleTarget: String = "1",
    val intervalDays: String = "3",
    val intervalBasis: HabitIntervalBasis =
        HabitIntervalBasis.FIXED_SCHEDULE,
    val extraCompletionsMoveNextDueDate: Boolean = false,
    val scheduleVisibility: HabitScheduleVisibility =
        HabitScheduleVisibility.ALWAYS,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isEditing: Boolean
        get() = habitId != null

    val canSave: Boolean
        get() {
            val validTarget =
                scheduleTarget.toIntOrNull() in 1..100

            val validInterval =
                scheduleType != HabitScheduleType.INTERVAL ||
                        intervalDays.toIntOrNull()
                            ?.let { it > 0 } == true

            return name.isNotBlank() &&
                    validTarget &&
                    validInterval &&
                    !isSaving
        }
}
data class HabitRowUiState(
    val id: Long,
    val name: String,
    val streakCount: Int,
    val completionCountToday: Int,
    val allowsMultipleCompletions: Boolean,
    val scheduleCompletions: Int,
    val scheduleTarget: Int,
    val dueStatus: HabitDueStatus
) {
    val isCompleted: Boolean
        get() = completionCountToday > 0

    val showsPlusButton: Boolean
        get() = allowsMultipleCompletions && isCompleted
}

data class HabitCategoryUiState(
    val category: HabitCategory,
    val isExpanded: Boolean = true,
    val habits: List<HabitRowUiState> = emptyList(),
    val hasHiddenHabits: Boolean = false,
    val showHiddenHabits: Boolean = false
)

data class HabitScreenUiState(
    val categories: List<HabitCategoryUiState> = emptyList(),
    val archivedHabits: List<ArchivedHabitUiState> = emptyList(),
    val inspectedHabitId: Long? = null,
    val editor: HabitEditorUiState? = null,
    val showArchivedHabits: Boolean = false,
    val confirmation: HabitConfirmationUiState? = null,
)

data class ArchivedHabitUiState(
    val id: Long,
    val name: String,
    val category: HabitCategory,
)

sealed interface HabitConfirmationUiState {
    val habitId: Long
    val habitName: String

    data class DeleteHabit(
        override val habitId: Long,
        override val habitName: String,
    ) : HabitConfirmationUiState
}