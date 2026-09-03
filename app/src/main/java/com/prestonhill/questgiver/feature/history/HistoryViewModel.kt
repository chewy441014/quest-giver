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
import com.prestonhill.questgiver.data.repository.NutritionRepository
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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
import java.time.LocalDate
import java.time.YearMonth

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
    private val nutritionRepository:
    NutritionRepository,
    private val nutritionMapper:
    NutritionHistoryMapper =
        NutritionHistoryMapper(),
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

    private val nutritionHistoryState =
        combine(
            nav,
            timeState,
            settingsState,
        ) {
                navigation,
                time,
                appSettings,
            ->
            val currentDate =
                time.appDay.date

            val customRange =
                navigation
                    .nutritionCustomRange
                    ?: defaultNutritionCustomRange(
                        currentDate
                    )

            val currentMonth =
                YearMonth.from(currentDate)

            val calendarMonth =
                navigation
                    .nutritionCalendarMonth
                    ?.takeUnless {
                        it.isAfter(
                            currentMonth
                        )
                    }
                    ?: currentMonth

            val selectedRange =
                navigation
                    .nutritionRangePreset
                    .dateRange(
                        currentDate =
                            currentDate,
                        customRange =
                            customRange,
                    )

            val earliestDate =
                listOf(
                    selectedRange.startDate,
                    customRange.startDate,
                    currentDate
                        .withDayOfMonth(1),
                    calendarMonth.atDay(1),
                )
                    .minOrNull()
                    ?: currentDate

            NutritionHistoryQuery(
                startTimestampMillis =
                    time.dayCalculator
                        .forDate(
                            earliestDate
                        )
                        .startTimestampMillis,
                endTimestampMillis =
                    time.dayCalculator
                        .forDate(
                            currentDate
                        )
                        .endTimestampMillis,
                rangePreset =
                    navigation
                        .nutritionRangePreset,
                customRange = customRange,
                calendarMonth =
                    calendarMonth,
                currentDate = currentDate,
                calculator =
                    time.dayCalculator,
                settings = appSettings,
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                nutritionRepository
                    .observeNutritionBetween(
                        startTimestampMillis =
                            query
                                .startTimestampMillis,
                        endTimestampMillis =
                            query
                                .endTimestampMillis,
                    )
                    .map { summary ->
                        nutritionMapper.map(
                            summary = summary,
                            rangePreset =
                                query.rangePreset,
                            customRange =
                                query.customRange,
                            calendarMonth =
                                query.calendarMonth,
                            currentDate =
                                query.currentDate,
                            calculator =
                                query.calculator,
                            settings =
                                query.settings,
                        )
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000
                    ),
                initialValue =
                    NutritionHistoryUiState(),
            )

    private val taskScreenState =
        combine(
            nav,
            repository.observeAllTasks(),
            repository.observeLogs(),
            timeState,
            changingTaskIds,
        ) { navigation, tasks, logs, time, changing ->
            val mappedTasks =
                mapper.tasks(
                    tasks = tasks,
                    logs = logs,
                    appDay = time.appDay,
                    currentTimestampMillis =
                        time.timestamp,
                    calculator = time.calculator,
                    changingTaskIds = changing,
                )

            val deleteConfirmation =
                navigation.deleteTaskId
                    ?.let { taskId ->
                        HistoryDeleteUiState(
                            taskId = taskId,
                            taskName =
                                navigation.deleteTaskName
                                    ?: "Task",
                            isDeleting =
                                taskId in changing,
                            errorMessage =
                                navigation.operationError,
                        )
                    }

            HistoryScreenUiState(
                section = navigation.section,
                tasks = TaskHistoryUiState(
                    page = navigation.taskPage,
                    allTasks = mappedTasks,
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
                        navigation.operationError
                            .takeIf {
                                navigation.deleteTaskId ==
                                        null
                            },
                    showArchivedTasks =
                        navigation.showArchivedTasks,
                    deleteConfirmation =
                        deleteConfirmation,
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

    val uiState =
        combine(
            taskScreenState,
            nutritionHistoryState,
            nav,
        ) {
                screen,
                nutrition,
                navigation,
            ->
            screen.copy(
                nutrition =
                    nutrition.copy(
                        showCustomRangePicker =
                            navigation
                                .showNutritionCustomRangePicker,
                        selectedStampTypes =
                            navigation
                                .selectedNutritionStampTypes,
                        selectedCalendarDate =
                            navigation
                                .selectedNutritionCalendarDate,
                    )
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
            dayCalculator = dayCalculator,
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

            is HistoryAction.RequestDeleteTask ->
                requestDeleteTask(
                    action.taskId
                )

            is HistoryAction.ToggleNutritionStamp ->
                nav.update { current ->
                    val selected =
                        current
                            .selectedNutritionStampTypes

                    val updated =
                        if (action.type in selected) {
                            if (selected.size == 1) {
                                selected
                            } else {
                                selected - action.type
                            }
                        } else {
                            selected + action.type
                        }

                    current.copy(
                        selectedNutritionStampTypes =
                            updated
                    )
                }

            HistoryAction.SelectAllNutritionStamps ->
                nav.update {
                    it.copy(
                        selectedNutritionStampTypes =
                            NutritionStampType
                                .entries
                                .toSet()
                    )
                }

            is HistoryAction.OpenNutritionCalendarDay ->
                nav.update {
                    it.copy(
                        selectedNutritionCalendarDate =
                            action.date
                    )
                }

            HistoryAction.DismissNutritionCalendarDay ->
                nav.update {
                    it.copy(
                        selectedNutritionCalendarDate =
                            null
                    )
                }

            HistoryAction.ConfirmDelete ->
                confirmDelete()

            HistoryAction.DismissDelete ->
                dismissDelete()

            HistoryAction.OpenNutritionCustomRange ->
                nav.update {
                    it.copy(
                        showNutritionCustomRangePicker =
                            true
                    )
                }

            HistoryAction.DismissNutritionCustomRange ->
                nav.update {
                    it.copy(
                        showNutritionCustomRangePicker =
                            false
                    )
                }

            is HistoryAction
            .SelectNutritionRange ->
                nav.update {
                    it.copy(
                        nutritionRangePreset =
                            action.preset
                    )
                }

            is HistoryAction
            .SetNutritionCustomRange -> {
                val currentDate =
                    timeState.value.appDay.date

                if (
                    !action.range.endDate
                        .isAfter(currentDate)
                ) {
                    nav.update {
                        it.copy(
                            nutritionRangePreset =
                                NutritionHistoryRangePreset
                                    .CUSTOM,
                            nutritionCustomRange =
                                action.range,
                            showNutritionCustomRangePicker =
                                false,
                        )
                    }
                }
            }

            HistoryAction.PreviousNutritionMonth ->
                nav.update { current ->
                    val currentMonth =
                        YearMonth.from(
                            timeState.value.appDay.date
                        )

                    current.copy(
                        nutritionCalendarMonth =
                            (
                                    current
                                        .nutritionCalendarMonth
                                        ?: currentMonth
                                    )
                                .minusMonths(1),
                        selectedNutritionCalendarDate = null,
                    )
                }

            HistoryAction.NextNutritionMonth ->
                nav.update { current ->
                    val currentMonth =
                        YearMonth.from(
                            timeState.value.appDay.date
                        )

                    val displayed =
                        current
                            .nutritionCalendarMonth
                            ?: currentMonth

                    current.copy(
                        nutritionCalendarMonth =
                            displayed
                                .plusMonths(1)
                                .coerceAtMost(
                                    currentMonth
                                ),
                        selectedNutritionCalendarDate = null,
                    )
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
                    ).clearOverlays()
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

    private fun requestDeleteTask(
        taskId: Long,
    ) {
        if (taskId in changingTaskIds.value) {
            return
        }

        val task =
            uiState.value.tasks.allTasks
                .firstOrNull {
                    it.id == taskId
                }
                ?: return

        if (!task.isArchived) {
            return
        }

        nav.update {
            it.copy(
                deleteTaskId = task.id,
                deleteTaskName = task.name,
                operationError = null,
            )
        }
    }

    private fun dismissDelete() {
        val taskId =
            nav.value.deleteTaskId
                ?: return

        if (taskId in changingTaskIds.value) {
            return
        }

        nav.update {
            it.copy(
                deleteTaskId = null,
                operationError = null,
                deleteTaskName = null,
            )
        }
    }

    private fun confirmDelete() {
        val taskId =
            nav.value.deleteTaskId
                ?: return

        if (taskId in changingTaskIds.value) {
            return
        }

        nav.update {
            it.copy(operationError = null)
        }

        changingTaskIds.value += taskId

        viewModelScope.launch {
            try {
                val deleted =
                    repository.deleteArchivedTask(
                        taskId
                    )

                if (deleted) {
                    nav.update { current ->
                        current.copy(
                            inspectedTaskId =
                                current
                                    .inspectedTaskId
                                    .takeUnless {
                                        it == taskId
                                    },
                            deleteTaskId = null,
                            operationError = null,
                        )
                    }
                } else {
                    nav.update {
                        it.copy(
                            operationError =
                                "Task could not be deleted."
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                nav.update {
                    it.copy(
                        operationError =
                            "Task could not be deleted."
                    )
                }
            } finally {
                changingTaskIds.value -= taskId
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
    val dayCalculator:
    AppDayCalculator,
    val calculator:
    TaskScheduleCalculator,
)

private data class NutritionHistoryQuery(
    val startTimestampMillis: Long,
    val endTimestampMillis: Long,
    val rangePreset:
    NutritionHistoryRangePreset,
    val customRange:
    NutritionHistoryDateRange,
    val calendarMonth: YearMonth,
    val currentDate: LocalDate,
    val calculator:
    AppDayCalculator,
    val settings: AppSettings,
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
    val deleteTaskId: Long? = null,
    val deleteTaskName: String? = null,
    val nutritionRangePreset: NutritionHistoryRangePreset = NutritionHistoryRangePreset.THIRTY_DAYS,
    val nutritionCustomRange: NutritionHistoryDateRange? = null,
    val nutritionCalendarMonth: YearMonth? = null,
    val showNutritionCustomRangePicker: Boolean = false,
    val selectedNutritionStampTypes: Set<NutritionStampType> = NutritionStampType.entries.toSet(),
    val selectedNutritionCalendarDate: LocalDate? = null,
)

private fun HistoryNavState.clearOverlays() =
    copy(
        inspectedTaskId = null,
        deleteTaskId = null,
        deleteTaskName = null,
        operationError = null,
        showNutritionCustomRangePicker = false,
    )

class HistoryViewModelFactory(
    private val repository: TaskRepository,
    private val settings: Flow<AppSettings> =
        flowOf(AppSettings()),
    private val clock: Clock =
        Clock.systemDefaultZone(),
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
    private val nutritionRepository:
    NutritionRepository,
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
                nutritionRepository =
                    nutritionRepository,
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