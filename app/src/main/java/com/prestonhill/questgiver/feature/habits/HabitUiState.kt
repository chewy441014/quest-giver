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
    val habits: List<HabitRowUiState> = emptyList()
)

data class HabitScreenUiState(
    val categories: List<HabitCategoryUiState> = emptyList(),
    val inspectedHabitId: Long? = null
)