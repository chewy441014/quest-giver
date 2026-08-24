package com.prestonhill.questgiver.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleVisibilityDb
import com.prestonhill.questgiver.data.repository.HabitRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.prestonhill.questgiver.core.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import java.time.Clock

class HabitViewModel(
    private val repository: HabitRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
) : ViewModel() {
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
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                createTimeState(
                    timestamp = currentTimestamp.value,
                    settings = AppSettings(),
                ),
        )

    private val expandedCategories =
        MutableStateFlow(HabitCategory.entries.toSet())

    private val inspectedHabitId =
        MutableStateFlow<Long?>(null)

    private val editorState =
        MutableStateFlow<HabitEditorUiState?>(null)

    private val operationError =
        MutableStateFlow<String?>(null)

    private data class HabitDialogState(
        val confirmation: HabitConfirmationUiState?,
        val operationError: String?,
    )

    private val confirmationState =
        MutableStateFlow<HabitConfirmationUiState?>(null)

    private val dialogState =
        combine(
            confirmationState,
            operationError
        ) { confirmation, error ->
            HabitDialogState(
                confirmation = confirmation,
                operationError = error
            )
        }

    private val activeHabits =
        repository.observeActiveHabits()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    private val showArchivedHabits = MutableStateFlow(false)

    private val archivedHabits = repository.observeArchivedHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val overlayState = combine(
        inspectedHabitId,
        editorState,
        showArchivedHabits,
        dialogState,
        archivedHabits,
    ) { inspectedId, editor, showArchived, dialogs, archived ->
        OverlayState(
            inspectedHabitId = inspectedId,
            editor = editor,
            showArchivedHabits = showArchived,
            confirmation = dialogs.confirmation,
            operationError = dialogs.operationError,
            archivedHabits = archived,
        )
    }

    private val categoriesShowingHidden =
        MutableStateFlow<Set<HabitCategory>>(emptySet())

    private val displayState =
        combine(
            expandedCategories,
            categoriesShowingHidden
        ) { expanded, showingHidden ->
            HabitDisplayState(
                expandedCategories = expanded,
                categoriesShowingHidden = showingHidden
            )
        }

    private fun requestDelete(
        habitId: Long,
    ) {
        viewModelScope.launch {
            val habit = repository.getHabit(habitId) ?: return@launch

            confirmationState.value =
                HabitConfirmationUiState.DeleteHabit(
                    habitId = habit.id,
                    habitName = habit.name,
                    )

        }
    }

    private fun confirmDelete() {
        val confirmation =
            confirmationState.value
                    as? HabitConfirmationUiState.DeleteHabit
                ?: return

        if (confirmation.isDeleting) {
            return
        }

        confirmationState.value =
            confirmation.copy(
                isDeleting = true,
                errorMessage = null,
            )

        val deletingLastArchivedHabit =
            showArchivedHabits.value &&
                    archivedHabits.value.singleOrNull()?.id ==
                    confirmation.habitId

        viewModelScope.launch {
            try {
                val deleted =
                    repository.deleteHabit(confirmation.habitId)
                if (deleted) {
                    if (deletingLastArchivedHabit) {
                        showArchivedHabits.value = false
                    }
                    confirmationState.value = null
                } else {
                    confirmationState.value =
                        confirmation.copy(
                            isDeleting = false,
                            errorMessage =
                                "Habit could not be deleted.",
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
                            "Habit could not be deleted.",
                    )
            }
        }
    }

    val uiState =
        combine(
            activeHabits,
            repository.observeAllHabitLogs(),
            timeState,
            displayState,
            overlayState
        ) { habits, logs, time, display, overlay ->
            createUiState(
                habits = habits,
                logs = logs,
                appDay = time.appDay,
                scheduleCalculator =
                    time.scheduleCalculator,
                display = display,
                inspected = overlay.inspectedHabitId,
                editor = overlay.editor,
                overlay = overlay
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyUiState()
        )

    fun onAction(action: HabitAction) {
        when (action) {
            is HabitAction.AddCompletion ->
                changeCompletion(
                    habitId = action.habitId,
                    add = true
                )

            is HabitAction.RemoveCompletion ->
                changeCompletion(
                    habitId = action.habitId,
                    add = false
                )

            is HabitAction.InspectHabit ->
                inspectedHabitId.value = action.habitId

            HabitAction.DismissHabitDetails ->
                inspectedHabitId.value = null

            is HabitAction.ToggleCategory ->
                expandedCategories.update { expanded ->
                    if (action.category in expanded) {
                        expanded - action.category
                    } else {
                        expanded + action.category
                    }
                }

            is HabitAction.ToggleHiddenHabits ->
                categoriesShowingHidden.update { categories ->
                    if (action.category in categories) {
                        categories - action.category
                    } else {
                        categories + action.category
                    }
                }

            HabitAction.AddHabit -> {
                inspectedHabitId.value = null
                editorState.value = HabitEditorUiState()
            }

            is HabitAction.EditHabit ->
                openEditor(action.habitId)

            is HabitAction.UpdateHabitEditor ->
                updateEditor(action.editor)

            HabitAction.SaveHabit ->
                saveHabit()

            HabitAction.DismissHabitEditor ->
                editorState.value = null

            is HabitAction.ArchiveHabit -> {
                archiveHabit(action.habitId)
            }

            HabitAction.ShowArchivedHabits -> {
                showArchivedHabits.value = true
            }

            HabitAction.DismissArchivedHabits -> {
                showArchivedHabits.value = false
            }

            is HabitAction.RestoreHabit -> {
                restoreHabit(action.habitId)
            }

            is HabitAction.RequestDeleteHabit -> {
                requestDelete(action.habitId)
            }

            HabitAction.ConfirmDelete -> {
                confirmDelete()
            }

            HabitAction.DismissConfirmation -> {
                confirmationState.value = null
            }

            HabitAction.DismissOperationError -> {
                operationError.value = null
            }
        }
    }

    fun refreshAppDay() {
        currentTimestamp.value =
            clock.millis()
    }

    private fun openEditor(habitId: Long) {
        viewModelScope.launch {
            val habit = repository.getHabit(habitId)
                ?: return@launch

            inspectedHabitId.value = null
            editorState.value = habit.toEditorState()
        }
    }

    private fun updateEditor(
        updated: HabitEditorUiState
    ) {
        val existing = editorState.value ?: return

        editorState.value =
            updated.copy(
                habitId = existing.habitId,

                // This property cannot change after creation.
                allowsMultipleCompletions =
                    if (existing.isEditing) {
                        existing.allowsMultipleCompletions
                    } else {
                        updated.allowsMultipleCompletions
                    }
            )
    }

    private fun saveHabit() {
        val editor = editorState.value ?: return

        if (!editor.canSave) {
            editorState.value =
                editor.copy(
                    errorMessage =
                        "Enter a valid name and schedule."
                )
            return
        }

        editorState.value =
            editor.copy(
                isSaving = true,
                errorMessage = null
            )

        viewModelScope.launch {
            try {
                val now = clock.millis()

                currentTimestamp.value = now

                if (editor.habitId == null) {
                    repository.createHabit(
                        createHabitEntity(
                            editor = editor,
                            timestampMillis = now
                        )
                    )
                } else {
                    val existing =
                        repository.getHabit(editor.habitId)
                            ?: error("Habit no longer exists.")

                    val updated =
                        updateHabitEntity(
                            existing = existing,
                            editor = editor
                        )

                    check(repository.updateHabit(updated)) {
                        "Habit could not be updated."
                    }
                }

                editorState.value = null
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                editorState.update { current ->
                    current?.copy(
                        isSaving = false,
                        errorMessage =
                            error.message
                                ?: "Habit could not be saved."
                    )
                }
            }
        }
    }

    private fun createHabitEntity(
        editor: HabitEditorUiState,
        timestampMillis: Long
    ): HabitEntity {
        val category = editor.category.toDb()

        return HabitEntity(
            name = editor.name.trim(),
            category = category,
            displayOrder = nextOrder(category),
            allowsMultipleCompletions =
                editor.allowsMultipleCompletions,
            scheduleType = editor.scheduleType.toDb(),
            scheduleTarget = editor.targetValue(),
            intervalDays = editor.intervalValue(),
            intervalBasis = editor.intervalBasisValue(),
            fixedScheduleAnchorEpochDay =
                editor.fixedAnchorValue(),
            extraCompletionsMoveNextDueDate =
                editor.extraCompletionValue(),
            scheduleVisibility =
                editor.scheduleVisibility.toDb(),
            createdAtEpochMillis = timestampMillis
        )
    }

    private fun updateHabitEntity(
        existing: HabitEntity,
        editor: HabitEditorUiState
    ): HabitEntity {
        val newCategory = editor.category.toDb()

        val newOrder =
            if (newCategory == existing.category) {
                existing.displayOrder
            } else {
                nextOrder(newCategory)
            }

        val preserveAnchor =
            existing.scheduleType ==
                    HabitScheduleTypeDb.INTERVAL &&
                    existing.intervalBasis ==
                    HabitIntervalBasisDb.FIXED_SCHEDULE &&
                    editor.scheduleType ==
                    HabitScheduleType.INTERVAL &&
                    editor.intervalBasis ==
                    HabitIntervalBasis.FIXED_SCHEDULE

        return existing.copy(
            name = editor.name.trim(),
            category = newCategory,
            displayOrder = newOrder,
            scheduleType = editor.scheduleType.toDb(),
            scheduleTarget = editor.targetValue(),
            intervalDays = editor.intervalValue(),
            intervalBasis = editor.intervalBasisValue(),
            fixedScheduleAnchorEpochDay =
                if (preserveAnchor) {
                    existing.fixedScheduleAnchorEpochDay
                } else {
                    editor.fixedAnchorValue()
                },
            extraCompletionsMoveNextDueDate =
                editor.extraCompletionValue(),
            scheduleVisibility =
                editor.scheduleVisibility.toDb()
        )
    }

    private fun nextOrder(category: HabitCategoryDb): Int =
        (
                activeHabits.value
                    .filter { habit ->
                        habit.category == category
                    }
                    .maxOfOrNull { habit ->
                        habit.displayOrder
                    } ?: -1
                ) + 1

    private fun HabitEditorUiState.targetValue(): Int =
        if (scheduleType == HabitScheduleType.INTERVAL) {
            1
        } else {
            scheduleTarget.toInt()
        }

    private fun HabitEditorUiState.intervalValue(): Int? =
        if (scheduleType == HabitScheduleType.INTERVAL) {
            intervalDays.toInt()
        } else {
            null
        }

    private fun HabitEditorUiState.intervalBasisValue():
            HabitIntervalBasisDb? =
        if (scheduleType == HabitScheduleType.INTERVAL) {
            intervalBasis.toDb()
        } else {
            null
        }

    private fun HabitEditorUiState.fixedAnchorValue(): Long? =
        if (
            scheduleType == HabitScheduleType.INTERVAL &&
            intervalBasis ==
            HabitIntervalBasis.FIXED_SCHEDULE
        ) {
            appDayAt(
                clock.millis()
            ).date.toEpochDay()
        } else {
            null
        }

    private fun HabitEditorUiState.extraCompletionValue():
            Boolean =
        scheduleType == HabitScheduleType.INTERVAL &&
                intervalBasis ==
                HabitIntervalBasis.FROM_COMPLETION &&
                extraCompletionsMoveNextDueDate

    private fun changeCompletion(
        habitId: Long,
        add: Boolean
    ) {
        viewModelScope.launch {
            val now = clock.millis()
            val appDay = appDayAt(now)

            currentTimestamp.value = now

            if (add) {
                repository.addCompletion(
                    habitId = habitId,
                    completionTimestampMillis = now,
                    appDayStartMillis =
                        appDay.startTimestampMillis,
                    appDayEndMillis =
                        appDay.endTimestampMillis,
                    recordedTimestampMillis = now
                )
            } else {
                repository.removeCompletion(
                    habitId = habitId,
                    appDayStartMillis =
                        appDay.startTimestampMillis,
                    appDayEndMillis =
                        appDay.endTimestampMillis,
                    recordedTimestampMillis = now
                )
            }
        }
    }

    private fun createUiState(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        appDay: AppDay,
        display: HabitDisplayState,
        inspected: Long?,
        editor: HabitEditorUiState?,
        overlay: OverlayState,
        scheduleCalculator: HabitScheduleCalculator,
    ): HabitScreenUiState {
        val rowsByCategory =
            habits.groupBy { habit ->
                habit.category.toUiCategory()
            }

        return HabitScreenUiState(
            operationError = overlay.operationError,
            categories = HabitCategory.entries.map { category ->
                val evaluatedHabits =
                    rowsByCategory[category]
                        .orEmpty()
                        .map { habit ->
                            habit to scheduleCalculator.evaluate(
                                habit = habit,
                                logs = logs,
                                appDay = appDay
                            )
                        }

                val normallyVisible =
                    evaluatedHabits.filter { (habit, evaluation) ->
                        HabitVisibilityEvaluator.shouldShow(
                            visibility = habit.scheduleVisibility,
                            evaluation = evaluation
                        )
                    }

                val hasHiddenHabits =
                    normallyVisible.size < evaluatedHabits.size

                val showHiddenHabits =
                    hasHiddenHabits &&
                            category in
                            display.categoriesShowingHidden

                val displayedHabits =
                    if (showHiddenHabits) {
                        evaluatedHabits
                    } else {
                        normallyVisible
                    }

                HabitCategoryUiState(
                    category = category,
                    isExpanded =
                        category in display.expandedCategories,
                    hasHiddenHabits = hasHiddenHabits,
                    showHiddenHabits = showHiddenHabits,
                    habits = displayedHabits.map {
                            (habit, evaluation) ->
                        HabitRowUiState(
                            id = habit.id,
                            name = habit.name,
                            streakCount = evaluation.streakCount,
                            completionCountToday =
                                evaluation.dailyCompletionCount,
                            allowsMultipleCompletions =
                                habit.allowsMultipleCompletions,
                            scheduleCompletions =
                                evaluation.scheduleCompletionCount,
                            scheduleTarget =
                                evaluation.scheduleTarget,
                            dueStatus = evaluation.dueStatus
                        )
                    }
                )
            },
            inspectedHabitId = inspected?.takeIf { id ->
                habits.any { habit -> habit.id == id }
            },
            editor = editor,
            archivedHabits = overlay.archivedHabits.map { habit ->
                ArchivedHabitUiState(
                    id = habit.id,
                    name = habit.name,
                    category = habit.category.toUiCategory()
                )
            },
            showArchivedHabits = overlay.showArchivedHabits,
            confirmation = overlay.confirmation,
        )
    }

    private fun emptyUiState() =
        HabitScreenUiState(
            categories = HabitCategory.entries.map { category ->
                HabitCategoryUiState(category = category)
            }
        )

    private fun archiveHabit(habitId: Long) {
        operationError.value = null

        viewModelScope.launch {
            try {
                val archived =
                    repository.archiveHabit(habitId)

                if (!archived) {
                    operationError.value =
                        "Habit could not be archived."
                    return@launch
                }

                inspectedHabitId.value = null

                if (editorState.value?.habitId == habitId) {
                    editorState.value = null
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                operationError.value =
                    "Habit could not be archived."
            }
        }
    }

    private fun restoreHabit(habitId: Long) {
        operationError.value = null

        val restoringLastArchivedHabit =
            showArchivedHabits.value &&
                    archivedHabits.value.singleOrNull()?.id ==
                    habitId

        viewModelScope.launch {
            try {
                val restored =
                    repository.restoreHabit(habitId)

                if (!restored) {
                    operationError.value =
                        "Habit could not be restored."
                    return@launch
                }

                if (restoringLastArchivedHabit) {
                    showArchivedHabits.value = false
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                operationError.value =
                    "Habit could not be restored."
            }
        }
    }

    private fun createTimeState(
        timestamp: Long,
        settings: AppSettings,
    ): HabitTimeState {
        val appDayCalculator =
            AppDayCalculator(
                dayBoundary = settings.dayBoundary,
                zoneId = clock.zone
            )

        return HabitTimeState(
            appDay =
                appDayCalculator.containing(timestamp),
            scheduleCalculator =
                HabitScheduleCalculator(
                    appDayCalculator = appDayCalculator,
                    weekStart = settings.weekStart,
                ),
        )
    }

    private fun appDayAt(timestamp: Long): AppDay =
        AppDayCalculator(
            dayBoundary =
                settingsState.value.dayBoundary,
            zoneId = clock.zone
        ).containing(timestamp)
}

private data class OverlayState(
    val inspectedHabitId: Long?,
    val editor: HabitEditorUiState?,
    val showArchivedHabits: Boolean,
    val confirmation: HabitConfirmationUiState?,
    val archivedHabits: List<HabitEntity>,
    val operationError: String?,
)
private data class HabitDisplayState(
    val expandedCategories: Set<HabitCategory>,
    val categoriesShowingHidden: Set<HabitCategory>
)

private data class HabitTimeState(
    val appDay: AppDay,
    val scheduleCalculator:
    HabitScheduleCalculator,
)

private fun HabitEntity.toEditorState() =
    HabitEditorUiState(
        habitId = id,
        name = name,
        category = category.toUiCategory(),
        allowsMultipleCompletions =
            allowsMultipleCompletions,
        scheduleType = scheduleType.toUi(),
        scheduleTarget = scheduleTarget.toString(),
        intervalDays = (intervalDays ?: 3).toString(),
        intervalBasis =
            intervalBasis?.toUi()
                ?: HabitIntervalBasis.FIXED_SCHEDULE,
        extraCompletionsMoveNextDueDate =
            extraCompletionsMoveNextDueDate,
        scheduleVisibility =
            scheduleVisibility.toUi()
    )

private fun HabitCategoryDb.toUiCategory(): HabitCategory =
    when (this) {
        HabitCategoryDb.MORNING ->
            HabitCategory.MORNING

        HabitCategoryDb.ANYTIME ->
            HabitCategory.ANYTIME

        HabitCategoryDb.BEFORE_BED ->
            HabitCategory.BEFORE_BED
    }

private fun HabitCategory.toDb(): HabitCategoryDb =
    when (this) {
        HabitCategory.MORNING ->
            HabitCategoryDb.MORNING

        HabitCategory.ANYTIME ->
            HabitCategoryDb.ANYTIME

        HabitCategory.BEFORE_BED ->
            HabitCategoryDb.BEFORE_BED
    }

private fun HabitScheduleType.toDb(): HabitScheduleTypeDb =
    when (this) {
        HabitScheduleType.DAILY ->
            HabitScheduleTypeDb.DAILY

        HabitScheduleType.WEEKLY_TARGET ->
            HabitScheduleTypeDb.WEEKLY_TARGET

        HabitScheduleType.INTERVAL ->
            HabitScheduleTypeDb.INTERVAL
    }

private fun HabitScheduleTypeDb.toUi(): HabitScheduleType =
    when (this) {
        HabitScheduleTypeDb.DAILY ->
            HabitScheduleType.DAILY

        HabitScheduleTypeDb.WEEKLY_TARGET ->
            HabitScheduleType.WEEKLY_TARGET

        HabitScheduleTypeDb.INTERVAL ->
            HabitScheduleType.INTERVAL
    }

private fun HabitIntervalBasis.toDb(): HabitIntervalBasisDb =
    when (this) {
        HabitIntervalBasis.FIXED_SCHEDULE ->
            HabitIntervalBasisDb.FIXED_SCHEDULE

        HabitIntervalBasis.FROM_COMPLETION ->
            HabitIntervalBasisDb.FROM_COMPLETION
    }

private fun HabitIntervalBasisDb.toUi(): HabitIntervalBasis =
    when (this) {
        HabitIntervalBasisDb.FIXED_SCHEDULE ->
            HabitIntervalBasis.FIXED_SCHEDULE

        HabitIntervalBasisDb.FROM_COMPLETION ->
            HabitIntervalBasis.FROM_COMPLETION
    }

private fun HabitScheduleVisibility.toDb():
        HabitScheduleVisibilityDb =
    when (this) {
        HabitScheduleVisibility.ALWAYS ->
            HabitScheduleVisibilityDb.ALWAYS

        HabitScheduleVisibility.WHEN_DUE ->
            HabitScheduleVisibilityDb.WHEN_DUE

        HabitScheduleVisibility.HIDE_AFTER_TARGET ->
            HabitScheduleVisibilityDb.HIDE_AFTER_TARGET
    }

private fun HabitScheduleVisibilityDb.toUi():
        HabitScheduleVisibility =
    when (this) {
        HabitScheduleVisibilityDb.ALWAYS ->
            HabitScheduleVisibility.ALWAYS

        HabitScheduleVisibilityDb.WHEN_DUE ->
            HabitScheduleVisibility.WHEN_DUE

        HabitScheduleVisibilityDb.HIDE_AFTER_TARGET ->
            HabitScheduleVisibility.HIDE_AFTER_TARGET
    }

class HabitViewModelFactory(
    private val repository: HabitRepository,
    private val settings: Flow<AppSettings>,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                HabitViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(
                repository = repository,
                settings = settings,
                clock = clock,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}