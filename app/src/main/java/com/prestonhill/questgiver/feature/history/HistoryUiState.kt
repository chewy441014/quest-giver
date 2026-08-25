package com.prestonhill.questgiver.feature.history

import java.time.LocalDate

enum class HistorySection(
    val label: String,
) {
    HABITS("Habits"),
    TASKS("Tasks"),
    NUTRITION("Nutrition"),
}

enum class TaskHistoryPage {
    DASHBOARD,
    ALL_TASKS,
}

data class HistoryDeleteUiState(
    val logId: Long,
    val taskName: String,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

data class HistoryGraphUiState(
    val id: String,
    val title: String,
    val message: String,
)

data class HistoryTaskUiState(
    val id: Long,
    val name: String,
    val category: String?,
    val schedule: String,
    val completionEpochDay: Long? = null,
    val isCompleted: Boolean = false,
    val canChangeCompletion: Boolean = false,
    val isChanging: Boolean = false,
    val isArchived: Boolean = false,
)

data class HistoryTaskLogUiState(
    val id: Long,
    val taskId: Long?,
    val taskName: String,
    val category: String?,
    val date: LocalDate,
    val completedAtMillis: Long,
    val isTaskCompletionChanging: Boolean =
        false,
) {
    val canOpenTask: Boolean
        get() = taskId != null

    val canChangeTaskCompletion: Boolean
        get() = taskId != null

    val canDelete: Boolean
        get() = taskId == null
}

data class HistoryTaskDayUiState(
    val date: LocalDate,
    val logs: List<HistoryTaskLogUiState>,
)

data class TaskHistoryUiState(
    val page: TaskHistoryPage =
        TaskHistoryPage.DASHBOARD,
    val inspectedTaskId: Long? = null,
    val allTasks: List<HistoryTaskUiState> =
        emptyList(),
    val logDays: List<HistoryTaskDayUiState> =
        emptyList(),
    val categoryGraph: HistoryGraphUiState =
        HistoryGraphUiState(
            id = "task_categories",
            title = "Tasks completed by category",
            message =
                "Category graph placeholder",
        ),
    val pinnedGraphs: List<HistoryGraphUiState> =
        listOf(
            HistoryGraphUiState(
                id = "pinned_preview",
                title = "Pinned graph",
                message =
                    "Pinned category and task graphs will appear here.",
            )
        ),
    val operationError: String? = null,
    val showArchivedTasks: Boolean = false,
) {
    val visibleTasks: List<HistoryTaskUiState>
        get() =
            allTasks.filter { task ->
                task.isArchived ==
                        showArchivedTasks
            }
}

data class HistoryScreenUiState(
    val section: HistorySection =
        HistorySection.TASKS,
    val tasks: TaskHistoryUiState =
        TaskHistoryUiState(),
)