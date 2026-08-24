package com.prestonhill.questgiver.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.core.time.BoundaryTimer
import com.prestonhill.questgiver.core.time.RealBoundaryTimer
import com.prestonhill.questgiver.data.repository.TaskRepository
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
    private val timer: BoundaryTimer,
) : ViewModel() {
    private val currentTimestamp =
        MutableStateFlow(clock.millis())

    private val operationError =
        MutableStateFlow<String?>(null)

    private val inspectedTaskId =
        MutableStateFlow<Long?>(null)

    private val confirmationState =
        MutableStateFlow<TaskDeleteUiState?>(null)

    private val editorState =
        MutableStateFlow<TaskEditorUiState?>(null)
    private val showHiddenToday =
        MutableStateFlow(false)

    private val overlayState =
        combine(
            inspectedTaskId,
            editorState,
            confirmationState,
            operationError,
        ) { inspected, editor, confirmation, error ->
            TaskOverlayState(
                inspectedTaskId = inspected,
                editor = editor,
                confirmation = confirmation,
                operationError = error,
            )
        }
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
        }.stateIn(
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
            repository.observeTasks(),
            repository.observeLogs(),
            timeState,
            overlayState,
            showHiddenToday,
        ) { tasks, logs, time, overlay, showHidden ->
            time.mapper.map(
                tasks = tasks,
                logs = logs,
                appDay = time.appDay,
                currentTimestampMillis =
                    time.timestamp,
                inspectedTaskId =
                    overlay.inspectedTaskId,
                editor = overlay.editor,
                confirmation =
                    overlay.confirmation,
                operationError =
                    overlay.operationError,
                showHiddenToday = showHidden,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    5_000
                ),
            initialValue = TaskScreenUiState(),
        )

    init {
        startTimer()
    }

    fun onAction(action: TaskAction) {
        when (action) {
            is TaskAction.Complete ->
                complete(
                    taskId = action.taskId,
                    completionEpochDay =
                        action.completionEpochDay,
                )

            is TaskAction.Inspect ->
                inspectedTaskId.value =
                    action.taskId

            is TaskAction.RequestDelete ->
                requestDelete(action.taskId)

            TaskAction.Add ->
                openNewEditor()

            is TaskAction.Edit ->
                openEditor(action.taskId)

            is TaskAction.UpdateEditor ->
                updateEditor(action.editor)

            TaskAction.Save ->
                saveTask()

            TaskAction.DismissEditor ->
                dismissEditor()

            TaskAction.DismissDetails ->
                inspectedTaskId.value = null

            TaskAction.DismissDelete ->
                dismissDelete()

            TaskAction.DeleteTask ->
                confirmDelete(
                    deleteHistory = false
                )

            TaskAction.DeleteTaskAndHistory ->
                confirmDelete(
                    deleteHistory = true
                )

            TaskAction.DismissError ->
                dismissError()

            TaskAction.ToggleHidden ->
                showHiddenToday.value =
                    !showHiddenToday.value
        }
    }
    fun refresh() {
        val timestamp = clock.millis()

        currentTimestamp.value = timestamp

        viewModelScope.launch {
            cleanup(
                timestamp = timestamp,
                settings = settingsState.value,
            )
        }
    }

    fun dismissError() {
        operationError.value = null
    }

    private fun startTimer() {
        viewModelScope.launch {
            settings
                .map { appSettings ->
                    TaskBoundarySettings(
                        dayBoundary =
                            appSettings.dayBoundary,
                        daylightSavingEnabled =
                            appSettings
                                .daylightSavingEnabled,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { boundarySettings ->
                    val calculator =
                        createDayCalculator(
                            boundarySettings
                        )

                    cleanup(
                        timestamp = clock.millis(),
                        settings =
                            settingsState.value,
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

                        val refreshed =
                            clock.millis()

                        currentTimestamp.value =
                            refreshed

                        cleanup(
                            timestamp = refreshed,
                            settings =
                                settingsState.value,
                        )
                    }
                }
        }
    }

    private suspend fun cleanup(
        timestamp: Long,
        settings: AppSettings,
    ) {
        try {
            val calculator =
                AppDayCalculator(
                    dayBoundary =
                        settings.dayBoundary,
                    zoneId =
                        zoneFor(
                            settings
                                .daylightSavingEnabled
                        ),
                )

            val currentDay =
                calculator.containing(timestamp)

            /*
             * At the beginning of app-day D + 7,
             * delete one-time tasks completed during D.
             */
            val completedBefore =
                calculator
                    .forDate(
                        currentDay.date.minusDays(6)
                    )
                    .startTimestampMillis

            repository.deleteExpiredTasks(
                completedBefore = completedBefore
            )
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }

            operationError.value =
                "Completed tasks could not be cleaned up."
        }
    }

    private fun createTimeState(
        timestamp: Long,
        settings: AppSettings,
    ): TaskTimeState {
        val dayCalculator =
            AppDayCalculator(
                dayBoundary =
                    settings.dayBoundary,
                zoneId =
                    zoneFor(
                        settings.daylightSavingEnabled
                    ),
            )

        return TaskTimeState(
            timestamp = timestamp,
            appDay =
                dayCalculator.containing(
                    timestamp
                ),
            mapper =
                TaskUiMapper(
                    TaskScheduleCalculator(
                        dayCalculator
                    )
                ),
        )
    }

    private fun createDayCalculator(
        settings: TaskBoundarySettings,
    ): AppDayCalculator =
        AppDayCalculator(
            dayBoundary =
                settings.dayBoundary,
            zoneId =
                zoneFor(
                    settings
                        .daylightSavingEnabled
                ),
        )

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

    private fun complete(
        taskId: Long,
        completionEpochDay: Long,
    ) {
        operationError.value = null

        viewModelScope.launch {
            try {
                val now = clock.millis()

                currentTimestamp.value = now

                val result =
                    repository.complete(
                        taskId = taskId,
                        scheduledEpochDay =
                            completionEpochDay,
                        completionTimestampMillis =
                            now,
                        recordedTimestampMillis =
                            now,
                    )

                when (result) {
                    TaskCompletionResult.SUCCESS,
                    TaskCompletionResult.ALREADY_COMPLETED -> {
                        if (
                            inspectedTaskId.value ==
                            taskId
                        ) {
                            inspectedTaskId.value =
                                null
                        }
                    }

                    else -> {
                        operationError.value =
                            "Task could not be completed."
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                operationError.value =
                    "Task could not be completed."
            }
        }
    }

    private fun requestDelete(
        taskId: Long,
    ) {
        viewModelScope.launch {
            try {
                val task =
                    repository.getTask(taskId)

                if (task == null) {
                    operationError.value =
                        "Task could not be found."
                    return@launch
                }

                confirmationState.value =
                    TaskDeleteUiState(
                        taskId = task.id,
                        taskName = task.name,
                    )
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                operationError.value =
                    "Task could not be found."
            }
        }
    }

    private fun dismissDelete() {
        val confirmation =
            confirmationState.value

        if (
            confirmation?.isDeleting == true
        ) {
            return
        }

        confirmationState.value = null
    }

    private fun confirmDelete(
        deleteHistory: Boolean,
    ) {
        val confirmation =
            confirmationState.value
                ?: return

        if (confirmation.isDeleting) {
            return
        }

        confirmationState.value =
            confirmation.copy(
                isDeleting = true,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                val deleted =
                    repository.deleteTask(
                        taskId =
                            confirmation.taskId,
                        deleteHistory =
                            deleteHistory,
                    )

                if (deleted) {
                    if (
                        inspectedTaskId.value ==
                        confirmation.taskId
                    ) {
                        inspectedTaskId.value =
                            null
                    }
                    if (
                        editorState.value?.taskId ==
                        confirmation.taskId
                    ) {
                        editorState.value = null
                    }

                    confirmationState.value = null
                } else {
                    confirmationState.value =
                        confirmation.copy(
                            isDeleting = false,
                            errorMessage =
                                "Task could not be deleted.",
                        )
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                confirmationState.value =
                    confirmation.copy(
                        isDeleting = false,
                        errorMessage =
                            "Task could not be deleted.",
                    )
            }
        }
    }

    private fun openNewEditor() {
        val currentDate =
            timeState.value.appDay.date

        inspectedTaskId.value = null

        editorState.value =
            TaskEditorUiState(
                scheduledDate = currentDate,
                recurrenceStartDate = currentDate,
            )
    }

    private fun openEditor(
        taskId: Long,
    ) {
        viewModelScope.launch {
            try {
                val task =
                    repository.getTask(taskId)

                if (task == null) {
                    operationError.value =
                        "Task could not be opened."
                    return@launch
                }

                inspectedTaskId.value = null
                editorState.value =
                    task.toEditorState()
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                operationError.value =
                    "Task could not be opened."
            }
        }
    }

    private fun updateEditor(
        editor: TaskEditorUiState,
    ) {
        val current =
            editorState.value
                ?: return

        if (current.isSaving) {
            return
        }

        editorState.value =
            editor.copy(
                taskId = current.taskId,
                isSaving = false,
                errorMessage = null,
            )
    }

    private fun dismissEditor() {
        if (
            editorState.value?.isSaving == true
        ) {
            return
        }

        editorState.value = null
    }

    private fun saveTask() {
        val editor =
            editorState.value
                ?: return

        if (!editor.canSave) {
            return
        }

        editorState.value =
            editor.copy(
                isSaving = true,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                val now = clock.millis()

                val saved =
                    if (editor.taskId == null) {
                        repository.createTask(
                            editor.toEntity(
                                existing = null,
                                timestamp = now,
                            )
                        )

                        true
                    } else {
                        val existing =
                            repository.getTask(
                                editor.taskId
                            )

                        if (existing == null) {
                            false
                        } else {
                            repository.updateTask(
                                editor.toEntity(
                                    existing = existing,
                                    timestamp = now,
                                )
                            )
                        }
                    }

                if (saved) {
                    currentTimestamp.value = now
                    editorState.value = null
                } else {
                    showSaveError(editor)
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                showSaveError(editor)
            }
        }
    }

    private fun showSaveError(
        editor: TaskEditorUiState,
    ) {
        editorState.value =
            editor.copy(
                isSaving = false,
                errorMessage =
                    "Task could not be saved.",
            )
    }
}

private data class TaskTimeState(
    val timestamp: Long,
    val appDay: AppDay,
    val mapper: TaskUiMapper,
)

private data class TaskBoundarySettings(
    val dayBoundary: LocalTime,
    val daylightSavingEnabled: Boolean,
)

private data class TaskOverlayState(
    val inspectedTaskId: Long?,
    val editor: TaskEditorUiState?,
    val confirmation: TaskDeleteUiState?,
    val operationError: String?,
)

private fun TaskEntity.toEditorState() =
    TaskEditorUiState(
        taskId = id,
        name = name,
        category = category.orEmpty(),
        scheduleType = scheduleType.toUi(),
        scheduledDate =
            scheduledEpochDay?.let(
                LocalDate::ofEpochDay
            ),
        recurrenceStartDate =
            recurrenceStartEpochDay?.let(
                LocalDate::ofEpochDay
            ),
        selectedWeekdays =
            weekdaysMask
                ?.let(::weekdaysFromMask)
                ?: emptySet(),
        intervalDays =
            (intervalDays ?: 3).toString(),
        intervalBasis =
            intervalBasis?.toUi()
                ?: TaskIntervalBasis.FIXED_SCHEDULE,
        dueTime =
            dueMinuteOfDay?.let { minute ->
                LocalTime.ofSecondOfDay(
                    minute * 60L
                )
            },
        remainsVisibleAfterDue =
            remainsVisibleAfterDue,
    )

private fun TaskEditorUiState.toEntity(
    existing: TaskEntity?,
    timestamp: Long,
): TaskEntity =
    TaskEntity(
        id = existing?.id ?: 0,
        name = name,
        category = category,
        displayOrder =
            existing?.displayOrder ?: 0,
        scheduleType =
            scheduleType.toDb(),
        scheduledEpochDay =
            if (
                scheduleType ==
                TaskScheduleType.ONE_TIME
            ) {
                scheduledDate?.toEpochDay()
            } else {
                null
            },
        recurrenceStartEpochDay =
            if (
                scheduleType !=
                TaskScheduleType.ONE_TIME
            ) {
                recurrenceStartDate
                    ?.toEpochDay()
            } else {
                null
            },
        weekdaysMask =
            if (
                scheduleType ==
                TaskScheduleType.WEEKLY_DAYS
            ) {
                selectedWeekdays.toMask()
            } else {
                null
            },
        intervalDays =
            if (
                scheduleType ==
                TaskScheduleType.INTERVAL
            ) {
                intervalDays.toInt()
            } else {
                null
            },
        intervalBasis =
            if (
                scheduleType ==
                TaskScheduleType.INTERVAL
            ) {
                intervalBasis.toDb()
            } else {
                null
            },
        dueMinuteOfDay =
            dueTime?.let { time ->
                time.hour * 60 +
                        time.minute
            },
        remainsVisibleAfterDue =
            remainsVisibleAfterDue,
        createdAtEpochMillis =
            existing?.createdAtEpochMillis
                ?: timestamp,
    )

private fun TaskScheduleType.toDb():
        TaskScheduleTypeDb =
    when (this) {
        TaskScheduleType.ONE_TIME ->
            TaskScheduleTypeDb.ONE_TIME

        TaskScheduleType.DAILY ->
            TaskScheduleTypeDb.DAILY

        TaskScheduleType.WEEKLY_DAYS ->
            TaskScheduleTypeDb.WEEKLY_DAYS

        TaskScheduleType.INTERVAL ->
            TaskScheduleTypeDb.INTERVAL
    }

private fun TaskScheduleTypeDb.toUi():
        TaskScheduleType =
    when (this) {
        TaskScheduleTypeDb.ONE_TIME ->
            TaskScheduleType.ONE_TIME

        TaskScheduleTypeDb.DAILY ->
            TaskScheduleType.DAILY

        TaskScheduleTypeDb.WEEKLY_DAYS ->
            TaskScheduleType.WEEKLY_DAYS

        TaskScheduleTypeDb.INTERVAL ->
            TaskScheduleType.INTERVAL
    }

private fun TaskIntervalBasis.toDb():
        TaskIntervalBasisDb =
    when (this) {
        TaskIntervalBasis.FIXED_SCHEDULE ->
            TaskIntervalBasisDb.FIXED_SCHEDULE

        TaskIntervalBasis.FROM_COMPLETION ->
            TaskIntervalBasisDb.FROM_COMPLETION
    }

private fun TaskIntervalBasisDb.toUi():
        TaskIntervalBasis =
    when (this) {
        TaskIntervalBasisDb.FIXED_SCHEDULE ->
            TaskIntervalBasis.FIXED_SCHEDULE

        TaskIntervalBasisDb.FROM_COMPLETION ->
            TaskIntervalBasis.FROM_COMPLETION
    }

private fun Set<DayOfWeek>.toMask(): Int =
    fold(0) { mask, day ->
        mask or
                (
                        1 shl
                                (day.value - 1)
                        )
    }

private fun weekdaysFromMask(
    mask: Int,
): Set<DayOfWeek> =
    DayOfWeek.entries
        .filter { day ->
            mask and
                    (
                            1 shl
                                    (day.value - 1)
                            ) != 0
        }
        .toSet()

class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                TaskViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(
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