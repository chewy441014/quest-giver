package com.prestonhill.questgiver.feature.history

sealed interface HistoryAction {
    data class SelectSection(
        val section: HistorySection,
    ) : HistoryAction

    data class OpenTaskPage(
        val page: TaskHistoryPage,
    ) : HistoryAction

    data object BackToDashboard : HistoryAction

    data class InspectTask(
        val taskId: Long,
    ) : HistoryAction

    data object DismissTask : HistoryAction

    data object DismissError : HistoryAction

    data class SetTaskCompletion(
        val taskId: Long,
        val scheduledEpochDay: Long,
        val completed: Boolean,
    ) : HistoryAction

    data class ShowArchivedTasks(
        val show: Boolean,
    ) : HistoryAction

    data class ArchiveTask(
        val taskId: Long,
    ) : HistoryAction

    data class RestoreTask(
        val taskId: Long,
    ) : HistoryAction

    data class RequestDeleteTask(
        val taskId: Long,
    ) : HistoryAction

    data object ConfirmDelete : HistoryAction

    data object DismissDelete : HistoryAction

    data class SelectNutritionRange(
        val preset:
        NutritionHistoryRangePreset,
    ) : HistoryAction

    data class SetNutritionCustomRange(
        val range:
        NutritionHistoryDateRange,
    ) : HistoryAction

    data object PreviousNutritionMonth :
        HistoryAction

    data object NextNutritionMonth :
        HistoryAction

    data object OpenNutritionCustomRange :
        HistoryAction

    data object DismissNutritionCustomRange :
        HistoryAction

}