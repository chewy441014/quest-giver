package com.prestonhill.questgiver.feature.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HistoryViewModel : ViewModel() {
    private val state =
        MutableStateFlow(
            HistoryScreenUiState()
        )

    val uiState = state.asStateFlow()

    fun onAction(action: HistoryAction) {
        state.update { current ->
            when (action) {
                is HistoryAction.SelectSection ->
                    current.copy(
                        section = action.section,
                        tasks = current.tasks.copy(
                            page =
                                TaskHistoryPage
                                    .DASHBOARD
                        ),
                    )

                is HistoryAction.OpenTaskPage ->
                    current.copy(
                        tasks = current.tasks.copy(
                            page = action.page
                        )
                    )

                HistoryAction.BackToDashboard ->
                    current.copy(
                        tasks = current.tasks.copy(
                            page =
                                TaskHistoryPage
                                    .DASHBOARD
                        )
                    )
            }
        }
    }
}