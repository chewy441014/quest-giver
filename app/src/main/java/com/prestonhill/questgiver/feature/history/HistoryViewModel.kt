package com.prestonhill.questgiver.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.time.Clock
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
    private val clock: Clock =
        Clock.systemDefaultZone(),
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

            is HistoryAction.RequestCorrect ->
                request(
                    logId = action.logId,
                    operation =
                        HistoryLogOperation.CORRECT,
                )

            is HistoryAction.RequestDeleteLog ->
                request(
                    logId = action.logId,
                    operation =
                        HistoryLogOperation.DELETE,
                )

            HistoryAction.ConfirmLog ->
                confirm()

            HistoryAction.DismissConfirm ->
                dismissConfirm()

            HistoryAction.DismissError ->
                nav.update {
                    it.copy(
                        operationError = null
                    )
                }
        }
    }

    private fun request(
        logId: Long,
        operation: HistoryLogOperation,
    ) {
        val log =
            uiState.value.tasks.findLog(logId)

        val allowed =
            when (operation) {
                HistoryLogOperation.CORRECT ->
                    log?.canCorrect == true

                HistoryLogOperation.DELETE ->
                    log?.canDelete == true
            }

        if (!allowed || log == null) {
            nav.update {
                it.copy(
                    operationError =
                        when (operation) {
                            HistoryLogOperation.CORRECT ->
                                "Completion cannot be corrected."

                            HistoryLogOperation.DELETE ->
                                "History cannot be deleted."
                        }
                )
            }

            return
        }

        nav.update {
            it.copy(
                inspectedLogId = null,
                confirmation =
                    HistoryLogConfirmationUiState(
                        logId = log.id,
                        taskName = log.taskName,
                        operation = operation,
                    ),
                operationError = null,
            )
        }
    }

    private fun confirm() {
        val confirmation =
            nav.value.confirmation
                ?: return

        if (confirmation.isWorking) {
            return
        }

        nav.update {
            it.copy(
                confirmation =
                    confirmation.copy(
                        isWorking = true,
                        errorMessage = null,
                    )
            )
        }

        viewModelScope.launch {
            val succeeded =
                try {
                    when (
                        confirmation.operation
                    ) {
                        HistoryLogOperation.CORRECT ->
                            repository.correctCompletion(
                                logId =
                                    confirmation.logId,
                                recordedTimestampMillis =
                                    clock.millis(),
                            ) ==
                                    TaskCompletionResult
                                        .SUCCESS

                        HistoryLogOperation.DELETE ->
                            repository.deleteHistory(
                                positiveLogId =
                                    confirmation.logId
                            )
                    }
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
                    confirmation.logId ||
                    active.operation !=
                    confirmation.operation
                ) {
                    current
                } else if (succeeded) {
                    current.copy(
                        confirmation = null
                    )
                } else {
                    current.copy(
                        confirmation =
                            active.copy(
                                isWorking = false,
                                errorMessage =
                                    confirmation
                                        .operation
                                        .errorMessage(),
                            )
                    )
                }
            }
        }
    }

    private fun dismissConfirm() {
        nav.update { current ->
            if (
                current.confirmation
                    ?.isWorking == true
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
    HistoryLogConfirmationUiState? = null,
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

private fun HistoryLogOperation.errorMessage():
        String =
    when (this) {
        HistoryLogOperation.CORRECT ->
            "Completion could not be corrected."

        HistoryLogOperation.DELETE ->
            "History could not be deleted."
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