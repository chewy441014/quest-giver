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
            repository.observeTasks(),
            repository.observeLogs(),
            timeState,
        ) { navigation, tasks, logs, time ->
            HistoryScreenUiState(
                section = navigation.section,
                tasks = TaskHistoryUiState(
                    page = navigation.taskPage,
                    allTasks =
                        mapper.tasks(
                            tasks = tasks,
                            logs = logs,
                            appDay = time.appDay,
                            currentTimestampMillis =
                                time.timestamp,
                            calculator = time.calculator,
                        ),
                    logDays =
                        mapper.logs(
                            logs = logs,
                            tasks = tasks,
                        ),
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