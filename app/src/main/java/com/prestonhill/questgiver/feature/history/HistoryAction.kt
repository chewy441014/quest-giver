package com.prestonhill.questgiver.feature.history

sealed interface HistoryAction {
    data class SelectSection(
        val section: HistorySection,
    ) : HistoryAction

    data class OpenTaskPage(
        val page: TaskHistoryPage,
    ) : HistoryAction

    data object BackToDashboard : HistoryAction
}