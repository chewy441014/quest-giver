package com.prestonhill.questgiver.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.repository.HabitRepository
import com.prestonhill.questgiver.core.time.AppDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HabitViewModel(
    private val repository: HabitRepository,
    private val appDayCalculator: AppDayCalculator,
    private val scheduleCalculator: HabitScheduleCalculator
) : ViewModel() {
    private val currentAppDay =
        MutableStateFlow(
            appDayCalculator.containing(
                System.currentTimeMillis()
            )
        )

    private val expandedCategories =
        MutableStateFlow(HabitCategory.entries.toSet())

    private val inspectedHabitId =
        MutableStateFlow<Long?>(null)

    val uiState =
        combine(
            repository.observeActiveHabits(),
            repository.observeAllHabitLogs(),
            currentAppDay,
            expandedCategories,
            inspectedHabitId
        ) { habits, logs, appDay, expanded, inspected ->
            createUiState(
                habits = habits,
                logs = logs,
                appDay = appDay,
                expanded = expanded,
                inspected = inspected
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

            is HabitAction.EditHabit -> {
                // Editor navigation will be added later.
            }

            is HabitAction.UpdateHabitEditor -> Unit
            HabitAction.SaveHabit -> Unit
            HabitAction.DismissHabitEditor -> Unit

            HabitAction.AddHabit -> {
                // Creation dialog will be added later.
            }
        }
    }

    fun refreshAppDay() {
        currentAppDay.value =
            appDayCalculator.containing(
                System.currentTimeMillis()
            )
    }

    private fun changeCompletion(
        habitId: Long,
        add: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val appDay = appDayCalculator.containing(now)

            currentAppDay.value = appDay

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
        expanded: Set<HabitCategory>,
        inspected: Long?
    ): HabitScreenUiState {
        val rowsByCategory =
            habits.groupBy { habit ->
                habit.category.toUiCategory()
            }

        return HabitScreenUiState(
            categories = HabitCategory.entries.map { category ->
                HabitCategoryUiState(
                    category = category,
                    isExpanded = category in expanded,
                    habits = rowsByCategory[category]
                        .orEmpty()
                        .map { habit ->
                            val evaluation =
                                scheduleCalculator.evaluate(
                                    habit = habit,
                                    logs = logs,
                                    appDay = appDay
                                )

                            HabitRowUiState(
                                id = habit.id,
                                name = habit.name,
                                streakCount =
                                    evaluation.streakCount,
                                completionCountToday =
                                    evaluation.dailyCompletionCount,
                                allowsMultipleCompletions =
                                    habit.allowsMultipleCompletions,
                                scheduleCompletions =
                                    evaluation.scheduleCompletionCount,
                                scheduleTarget =
                                    evaluation.scheduleTarget,
                                dueStatus =
                                    evaluation.dueStatus
                            )
                        }
                )
            },
            inspectedHabitId = inspected?.takeIf { id ->
                habits.any { habit -> habit.id == id }
            }
        )
    }

    private fun emptyUiState() =
        HabitScreenUiState(
            categories = HabitCategory.entries.map { category ->
                HabitCategoryUiState(
                    category = category
                )
            }
        )
}

private fun HabitCategoryDb.toUiCategory(): HabitCategory =
    when (this) {
        HabitCategoryDb.MORNING ->
            HabitCategory.MORNING

        HabitCategoryDb.ANYTIME ->
            HabitCategory.ANYTIME

        HabitCategoryDb.BEFORE_BED ->
            HabitCategory.BEFORE_BED
    }

class HabitViewModelFactory(
    private val repository: HabitRepository,
    private val appDayCalculator: AppDayCalculator,
    private val scheduleCalculator: HabitScheduleCalculator
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
                appDayCalculator = appDayCalculator,
                scheduleCalculator = scheduleCalculator
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}