package com.prestonhill.questgiver.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HistoryViewModel(
    repository: TaskRepository,
    private val mapper: TaskHistoryMapper =
        TaskHistoryMapper(),
) : ViewModel() {
    private val nav =
        MutableStateFlow(HistoryNavState())

    val uiState =
        combine(
            nav,
            repository.observeTasks(),
            repository.observeLogs(),
        ) { navigation, tasks, logs ->
            HistoryScreenUiState(
                section = navigation.section,
                tasks = TaskHistoryUiState(
                    page = navigation.taskPage,
                    allTasks =
                        mapper.tasks(tasks),
                    logDays =
                        mapper.logs(logs),
                    inspectedTaskId =
                        navigation.inspectedTaskId,
                    inspectedLogId =
                        navigation.inspectedLogId,
                ),
            )
        }
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000
                    ),
                initialValue =
                    HistoryScreenUiState(),
            )

    fun onAction(action: HistoryAction) {
        nav.update { current ->
            when (action) {
                is HistoryAction.SelectSection ->
                    current.copy(
                        section = action.section,
                        taskPage =
                            TaskHistoryPage
                                .DASHBOARD,
                        inspectedTaskId = null,
                        inspectedLogId = null,
                    )

                is HistoryAction.OpenTaskPage ->
                    current.copy(
                        taskPage = action.page,
                        inspectedTaskId = null,
                        inspectedLogId = null,
                    )

                is HistoryAction.InspectTask ->
                    current.copy(
                        inspectedTaskId = action.taskId,
                        inspectedLogId = null,
                    )

                HistoryAction.DismissTask ->
                    current.copy(
                        inspectedTaskId = null
                    )

                is HistoryAction.InspectLog ->
                    current.copy(
                        inspectedLogId = action.logId,
                        inspectedTaskId = null,
                    )

                HistoryAction.DismissLog ->
                    current.copy(
                        inspectedLogId = null
                    )

                HistoryAction.BackToDashboard ->
                    current.copy(
                        taskPage =
                            TaskHistoryPage
                                .DASHBOARD
                    )
            }
        }
    }
}

private data class HistoryNavState(
    val section: HistorySection =
        HistorySection.TASKS,
    val taskPage: TaskHistoryPage =
        TaskHistoryPage.DASHBOARD,
    val inspectedTaskId: Long? = null,
    val inspectedLogId: Long? = null,
)

class HistoryViewModelFactory(
    private val repository: TaskRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                HistoryViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(
                repository = repository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: " +
                    modelClass.name
        )
    }
}