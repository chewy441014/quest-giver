package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.core.settings.AppSettings
import java.time.LocalDate
import java.time.YearMonth

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

enum class NutritionHistoryRangePreset(
    val label: String,
) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    NINETY_DAYS("90 days"),
    ONE_YEAR("1 year"),
    CUSTOM("Custom"),
}

data class NutritionHistoryDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(
            !startDate.isAfter(endDate)
        )
    }
}

data class NutritionHistoryDayUiState(
    val date: LocalDate,
    val calories: Double,
    val proteinGrams: Double,
    val hasLogs: Boolean,
    val calorieGoalMet: Boolean,
    val proteinGoalMet: Boolean,
    val isFuture: Boolean = false,
)

data class NutritionHistoryMetricUiState(
    val loggedDays: Int = 0,
    val average: Double? = null,
    val minimumNonZero: Double? = null,
    val maximum: Double? = null,
)

data class NutritionGoalCompletionUiState(
    val metDays: Int = 0,
    val totalDays: Int = 0,
    val progress: Float = 0f,
)

data class NutritionHistoryUiState(
    val rangePreset:
    NutritionHistoryRangePreset =
        NutritionHistoryRangePreset
            .THIRTY_DAYS,
    val selectedRange:
    NutritionHistoryDateRange? = null,
    val customRange:
    NutritionHistoryDateRange? = null,
    val selectedDays:
    List<NutritionHistoryDayUiState> =
        emptyList(),
    val calorieStatistics:
    NutritionHistoryMetricUiState =
        NutritionHistoryMetricUiState(),
    val proteinStatistics:
    NutritionHistoryMetricUiState =
        NutritionHistoryMetricUiState(),
    val currentMonthCalories:
    NutritionGoalCompletionUiState =
        NutritionGoalCompletionUiState(),
    val customRangeCalories:
    NutritionGoalCompletionUiState =
        NutritionGoalCompletionUiState(),
    val currentMonthProtein:
    NutritionGoalCompletionUiState =
        NutritionGoalCompletionUiState(),
    val customRangeProtein:
    NutritionGoalCompletionUiState =
        NutritionGoalCompletionUiState(),
    val calendarMonth: YearMonth? = null,
    val calendarDays:
    List<NutritionHistoryDayUiState> =
        emptyList(),
    val currentDate: LocalDate? = null,
    val showCustomRangePicker:
    Boolean = false,
    val calorieGoal: Double =
        AppSettings.DEFAULT_CALORIE_GOAL,
    val maximumCalorieGoal: Double? =
        null,
    val proteinGoalGrams: Double =
        AppSettings.DEFAULT_PROTEIN_GOAL_GRAMS,
    val maximumProteinGoalGrams:
    Double? = null,
)

data class HistoryDeleteUiState(
    val taskId: Long,
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
    val taskId: Long,
    val taskName: String,
    val category: String?,
    val date: LocalDate,
    val completedAtMillis: Long,
    val isTaskCompletionChanging: Boolean =
        false,
)

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
    val deleteConfirmation:
    HistoryDeleteUiState? = null,
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
    val nutrition: NutritionHistoryUiState =
        NutritionHistoryUiState(),
)