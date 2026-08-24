package com.prestonhill.questgiver.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: TaskRepository,
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
                    confirmation =
                        navigation.confirmation,
                    operationError =
                        navigation.operationError,
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
        when (action) {
            is HistoryAction.SelectSection ->
                nav.update {
                    it.copy(
                        section = action.section,
                        taskPage =
                            TaskHistoryPage
                                .DASHBOARD,
                    ).clearOverlays()
                }

            is HistoryAction.OpenTaskPage ->
                nav.update {
                    it.copy(
                        taskPage = action.page
                    ).clearOverlays()
                }

            HistoryAction.BackToDashboard ->
                nav.update {
                    it.copy(
                        taskPage =
                            TaskHistoryPage
                                .DASHBOARD
                    ).clearOverlays()
                }

            is HistoryAction.InspectTask ->
                nav.update {
                    it.copy(
                        inspectedTaskId =
                            action.taskId,
                        inspectedLogId = null,
                        confirmation = null,
                        operationError = null,
                    )
                }

            HistoryAction.DismissTask ->
                nav.update {
                    it.copy(
                        inspectedTaskId = null
                    )
                }

            is HistoryAction.InspectLog ->
                nav.update {
                    it.copy(
                        inspectedLogId =
                            action.logId,
                        inspectedTaskId = null,
                        confirmation = null,
                        operationError = null,
                    )
                }

            HistoryAction.DismissLog ->
                nav.update {
                    it.copy(
                        inspectedLogId = null
                    )
                }

            is HistoryAction.RequestDeleteLog ->
                requestDelete(action.logId)

            HistoryAction.ConfirmDeleteLog ->
                confirmDelete()

            HistoryAction.DismissDeleteLog ->
                dismissDelete()

            HistoryAction.DismissError ->
                nav.update {
                    it.copy(
                        operationError = null
                    )
                }
        }
    }

    private fun requestDelete(
        logId: Long,
    ) {
        val log =
            uiState.value.tasks.findLog(logId)

        if (log?.canDelete != true) {
            nav.update {
                it.copy(
                    operationError =
                        "History cannot be deleted."
                )
            }

            return
        }

        nav.update {
            it.copy(
                inspectedLogId = null,
                confirmation =
                    HistoryDeleteUiState(
                        logId = log.id,
                        taskName = log.taskName,
                    ),
                operationError = null,
            )
        }
    }

    private fun confirmDelete() {
        val confirmation =
            nav.value.confirmation
                ?: return

        if (confirmation.isDeleting) {
            return
        }

        nav.update {
            it.copy(
                confirmation =
                    confirmation.copy(
                        isDeleting = true,
                        errorMessage = null,
                    )
            )
        }

        viewModelScope.launch {
            val deleted =
                try {
                    repository.deleteHistory(
                        positiveLogId =
                            confirmation.logId
                    )
                } catch (error: Exception) {
                    if (
                        error is
                                CancellationException
                    ) {
                        throw error
                    }

                    false
                }

            nav.update { current ->
                val active =
                    current.confirmation

                if (
                    active?.logId !=
                    confirmation.logId
                ) {
                    current
                } else if (deleted) {
                    current.copy(
                        confirmation = null
                    )
                } else {
                    current.copy(
                        confirmation =
                            active.copy(
                                isDeleting = false,
                                errorMessage =
                                    "History could not be deleted.",
                            )
                    )
                }
            }
        }
    }

    private fun dismissDelete() {
        nav.update { current ->
            if (
                current.confirmation
                    ?.isDeleting == true
            ) {
                current
            } else {
                current.copy(
                    confirmation = null
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
    val confirmation:
    HistoryDeleteUiState? = null,
    val operationError: String? = null,
)

private fun HistoryNavState.clearOverlays() =
    copy(
        inspectedTaskId = null,
        inspectedLogId = null,
        confirmation = null,
        operationError = null,
    )

private fun TaskHistoryUiState.findLog(
    logId: Long,
): HistoryTaskLogUiState? =
    logDays.asSequence()
        .flatMap { it.logs.asSequence() }
        .firstOrNull {
            it.id == logId
        }


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