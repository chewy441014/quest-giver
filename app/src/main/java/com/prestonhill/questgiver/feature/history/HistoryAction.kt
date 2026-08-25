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

    data class InspectLog(
        val logId: Long,
    ) : HistoryAction

    data object DismissLog : HistoryAction

    data object ConfirmDeleteLog : HistoryAction

    data object DismissDeleteLog : HistoryAction
    data class RequestDeleteLog(
        val logId: Long,
    ) : HistoryAction

    data object DismissError : HistoryAction

    data class SetTaskCompletion(
        val taskId: Long,
        val scheduledEpochDay: Long,
        val completed: Boolean,
    ) : HistoryAction
}