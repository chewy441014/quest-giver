package com.prestonhill.questgiver.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.TaskRepository
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.core.time.BoundaryTimer
import com.prestonhill.questgiver.core.time.RealBoundaryTimer
import com.prestonhill.questgiver.feature.tasks.TaskScheduleCalculator
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: TaskRepository,
    private val settings: Flow<AppSettings> =
        flowOf(AppSettings()),
    private val clock: Clock =
        Clock.systemDefaultZone(),
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
    private val mapper: TaskHistoryMapper =
        TaskHistoryMapper(),
) : ViewModel() {
    private val nav =
        MutableStateFlow(HistoryNavState())

    private val changingTaskIds =
        MutableStateFlow<Set<Long>>(
            emptySet()
        )

    private val currentTimestamp =
        MutableStateFlow(clock.millis())

    private val settingsState =
        settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    private val timeState =
        combine(
            currentTimestamp,
            settingsState,
        ) { timestamp, appSettings ->
            createTimeState(
                timestamp = timestamp,
                settings = appSettings,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    createTimeState(
                        timestamp =
                            currentTimestamp.value,
                        settings = AppSettings(),
                    ),
            )

    val uiState =
        combine(
            nav,
            repository.observeAllTasks(),
            repository.observeLogs(),
            timeState,
            changingTaskIds,
        ) { navigation, tasks, logs, time, changing ->
            HistoryScreenUiState(
                section = navigation.section,
                tasks = TaskHistoryUiState(
                    page = navigation.taskPage,
                    allTasks =
                        mapper.tasks(
                            tasks = tasks,
                            logs = logs,
                            appDay = time.appDay,
                            currentTimestampMillis = time.timestamp,
                            calculator = time.calculator,
                            changingTaskIds = changing,
                        ),
                    logDays =
                        mapper.logs(
                            logs = logs,
                            tasks = tasks,
                            changingTaskIds =
                                changing,
                        ),
                    inspectedTaskId =
                        navigation.inspectedTaskId,
                    operationError =
                        navigation.operationError,
                    showArchivedTasks =
                        navigation.showArchivedTasks,
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

    init {
        startTimer()
    }

    fun refresh() {
        currentTimestamp.value = clock.millis()
    }

    private fun startTimer() {
        viewModelScope.launch {
            settings
                .map { appSettings ->
                    HistoryBoundarySettings(
                        dayBoundary =
                            appSettings.dayBoundary,
                        daylightSavingEnabled =
                            appSettings
                                .daylightSavingEnabled,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { boundary ->
                    val calculator =
                        AppDayCalculator(
                            dayBoundary =
                                boundary.dayBoundary,
                            zoneId =
                                zoneFor(
                                    boundary
                                        .daylightSavingEnabled
                                ),
                        )

                    while (true) {
                        val now = clock.millis()

                        val nextBoundary =
                            calculator
                                .containing(now)
                                .endTimestampMillis

                        timer.pause(
                            (nextBoundary - now)
                                .coerceAtLeast(1L)
                        )

                        currentTimestamp.value =
                            clock.millis()
                    }
                }
        }
    }

    private fun createTimeState(
        timestamp: Long,
        settings: AppSettings,
    ): HistoryTimeState {
        val dayCalculator =
            AppDayCalculator(
                dayBoundary =
                    settings.dayBoundary,
                zoneId =
                    zoneFor(
                        settings
                            .daylightSavingEnabled
                    ),
            )

        return HistoryTimeState(
            timestamp = timestamp,
            appDay =
                dayCalculator.containing(
                    timestamp
                ),
            calculator =
                TaskScheduleCalculator(
                    dayCalculator
                ),
        )
    }

    private fun zoneFor(
        daylightSavingEnabled: Boolean,
    ): ZoneId =
        if (daylightSavingEnabled) {
            clock.zone
        } else {
            clock.zone.rules.getStandardOffset(
                clock.instant()
            )
        }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.OpenTaskPage ->
                nav.update {
                    it.copy(
                        taskPage = action.page,
                        showArchivedTasks = false,
                    ).clearOverlays()
                }
            is HistoryAction.SetTaskCompletion ->
                setTaskCompletion(
                    taskId = action.taskId,
                    scheduledEpochDay =
                        action.scheduledEpochDay,
                    completed = action.completed,
                )
            is HistoryAction.SelectSection ->
                nav.update {
                    it.copy(
                        section = action.section,
                        taskPage =
                            TaskHistoryPage
                                .DASHBOARD,
                    ).clearOverlays()
                }

            HistoryAction.BackToDashboard ->
                nav.update {
                    it.copy(
                        taskPage = TaskHistoryPage.DASHBOARD,
                        showArchivedTasks = false
                    ).clearOverlays()
                }

            is HistoryAction.ShowArchivedTasks ->
                nav.update {
                    it.copy(
                        showArchivedTasks =
                            action.show,
                        inspectedTaskId = null,
                        operationError = null,
                    )
                }

            is HistoryAction.ArchiveTask ->
                setArchived(
                    taskId = action.taskId,
                    archived = true,
                )

            is HistoryAction.RestoreTask ->
                setArchived(
                    taskId = action.taskId,
                    archived = false,
                )

            is HistoryAction.InspectTask ->
                nav.update {
                    it.copy(
                        inspectedTaskId = action.taskId,
                        operationError = null,
                    )
                }

            HistoryAction.DismissTask ->
                nav.update {
                    it.copy(
                        inspectedTaskId = null
                    )
                }

            HistoryAction.DismissError ->
                nav.update {
                    it.copy(
                        operationError = null
                    )
                }
        }
    }

    private fun setArchived(
        taskId: Long,
        archived: Boolean,
    ) {
        if (taskId in changingTaskIds.value) {
            return
        }

        val inspectedTaskId =
            nav.value.inspectedTaskId

        nav.update {
            it.copy(
                operationError = null
            )
        }

        changingTaskIds.value += taskId

        viewModelScope.launch {
            try {
                val changed =
                    if (archived) {
                        repository.archiveTask(
                            taskId = taskId,
                            timestampMillis =
                                clock.millis(),
                        )
                    } else {
                        repository.restoreTask(
                            taskId
                        )
                    }

                if (!changed) {
                    nav.update {
                        it.copy(
                            operationError =
                                if (archived) {
                                    "Task could not be archived."
                                } else {
                                    "Task could not be restored."
                                }
                        )
                    }
                } else {
                    nav.update { current ->
                        if (
                            current.inspectedTaskId ==
                            inspectedTaskId
                        ) {
                            current.copy(
                                inspectedTaskId = null
                            )
                        } else {
                            current
                        }
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                nav.update {
                    it.copy(
                        operationError =
                            if (archived) {
                                "Task could not be archived."
                            } else {
                                "Task could not be restored."
                            }
                    )
                }
            } finally {
                changingTaskIds.value -= taskId
            }
        }
    }

    private fun setTaskCompletion(
        taskId: Long,
        scheduledEpochDay: Long,
        completed: Boolean,
    ) {
        if (taskId in changingTaskIds.value) {
            return
        }

        nav.update {
            it.copy(
                operationError = null
            )
        }

        changingTaskIds.value += taskId

        viewModelScope.launch {
            try {
                val now = clock.millis()

                currentTimestamp.value = now

                val result =
                    repository.setCompletion(
                        taskId = taskId,
                        scheduledEpochDay =
                            scheduledEpochDay,
                        completed = completed,
                        completionTimestampMillis =
                            now,
                        recordedTimestampMillis =
                            now,
                    )

                val accepted =
                    if (completed) {
                        result ==
                                TaskCompletionResult.SUCCESS ||
                                result ==
                                TaskCompletionResult
                                    .ALREADY_COMPLETED
                    } else {
                        result ==
                                TaskCompletionResult.SUCCESS ||
                                result ==
                                TaskCompletionResult
                                    .ALREADY_INCOMPLETE
                    }

                if (!accepted) {
                    nav.update {
                        it.copy(
                            operationError =
                                "Task completion could not be changed."
                        )
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                nav.update {
                    it.copy(
                        operationError =
                            "Task completion could not be changed."
                    )
                }
            } finally {
                changingTaskIds.value -= taskId
            }
        }
    }

}

private data class HistoryTimeState(
    val timestamp: Long,
    val appDay: AppDay,
    val calculator: TaskScheduleCalculator,
)

private data class HistoryBoundarySettings(
    val dayBoundary: LocalTime,
    val daylightSavingEnabled: Boolean,
)

private data class HistoryNavState(
    val section: HistorySection =
        HistorySection.TASKS,
    val taskPage: TaskHistoryPage =
        TaskHistoryPage.DASHBOARD,
    val inspectedTaskId: Long? = null,
    val operationError: String? = null,
    val showArchivedTasks: Boolean = false,
)

private fun HistoryNavState.clearOverlays() =
    copy(
        inspectedTaskId = null,
        operationError = null,
    )

class HistoryViewModelFactory(
    private val repository: TaskRepository,
    private val settings: Flow<AppSettings> =
        flowOf(AppSettings()),
    private val clock: Clock =
        Clock.systemDefaultZone(),
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
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
                repository = repository,
                settings = settings,
                clock = clock,
                timer = timer,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: " +
                    modelClass.name
        )
    }
}