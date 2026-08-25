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
    ALL_LOGS,
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
)

data class HistoryTaskLogUiState(
    val id: Long,
    val taskId: Long?,
    val taskName: String,
    val category: String?,
    val date: LocalDate,
    val completedAtMillis: Long,
    val isCorrected: Boolean,
    val canChangeCompletion: Boolean =
        taskId != null,
    val isChanging: Boolean = false,
    val isCompleted: Boolean = false,
) {
    val canOpenTask: Boolean
        get() = taskId != null

    val canCorrect: Boolean
        get() =
            taskId != null &&
                    !isCorrected

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
    val inspectedLogId: Long? = null,
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
    val confirmation:
    HistoryDeleteUiState? = null,

    val operationError: String? = null,
)

data class HistoryScreenUiState(
    val section: HistorySection =
        HistorySection.TASKS,
    val tasks: TaskHistoryUiState =
        TaskHistoryUiState(),
)