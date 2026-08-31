package com.prestonhill.questgiver.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AppSettingsRepository,
) : ViewModel() {
    private val isSaving =
        MutableStateFlow(false)

    private val errorMessage =
        MutableStateFlow<String?>(null)

    private val nutritionGoalsEditor = MutableStateFlow<NutritionGoalsEditorUiState?>(null)

    val uiState =
        combine(
            repository.settings,
            isSaving,
            errorMessage,
            nutritionGoalsEditor,
        ) {
                settings,
                saving,
                error,
                goalsEditor,
            ->
            SettingsUiState(
                settings = settings,
                isLoading = false,
                isSaving = saving,
                errorMessage = error,
                nutritionGoalsEditor =
                    goalsEditor,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted
                    .WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetDayBoundary -> {
                save("Day boundary could not be saved.") {
                    repository.setDayBoundary(action.time)
                }
            }

            is SettingsAction.SetWeekStart -> {
                save("Week start could not be saved.") {
                    repository.setWeekStart(action.day)
                }
            }

            is SettingsAction.SetDaylightSaving -> {
                save(
                    "Daylight saving setting could not be saved."
                ) {
                    repository.setDaylightSaving(
                        action.enabled
                    )
                }
            }

            is SettingsAction.EditNutritionGoals -> {
                if (!isSaving.value) {
                    val settings =
                        uiState.value.settings

                    nutritionGoalsEditor.value =
                        NutritionGoalsEditorUiState(
                            originalCalorieGoal =
                                settings.calorieGoal,
                            originalProteinGoalGrams =
                                settings
                                    .proteinGoalGrams,
                            calorieGoalText =
                                goalText(
                                    settings.calorieGoal
                                ),
                            proteinGoalText =
                                goalText(
                                    settings
                                        .proteinGoalGrams
                                ),
                        )
                }
            }

            is SettingsAction.ChangeCalorieGoal -> {
                nutritionGoalsEditor.update {
                    it?.copy(
                        calorieGoalText =
                            action.value
                    )
                }
            }

            is SettingsAction.ChangeProteinGoal -> {
                nutritionGoalsEditor.update {
                    it?.copy(
                        proteinGoalText =
                            action.value
                    )
                }
            }

            SettingsAction.SaveNutritionGoals -> {
                saveNutritionGoals()
            }

            SettingsAction.DismissNutritionGoals -> {
                if (!isSaving.value) {
                    nutritionGoalsEditor.value = null
                }
            }

            SettingsAction.DismissError -> {
                errorMessage.value = null
            }
        }
    }

    private fun saveNutritionGoals() {
        val editor =
            nutritionGoalsEditor.value
                ?: return

        if (!editor.canSave) {
            return
        }

        val calorieGoal =
            editor.calorieGoal ?: return

        val proteinGoalGrams =
            editor.proteinGoalGrams
                ?: return

        save(
            "Nutrition goals could not be saved."
        ) {
            repository.setNutritionGoals(
                calorieGoal = calorieGoal,
                proteinGoalGrams =
                    proteinGoalGrams,
            )

            nutritionGoalsEditor.value = null
        }
    }

    private fun goalText(
        value: Double,
    ): String =
        value.toString()
            .removeSuffix(".0")

    private fun save(
        failureMessage: String,
        operation: suspend () -> Unit,
    ) {
        if (isSaving.value) {
            return
        }

        isSaving.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                errorMessage.value = failureMessage
            } finally {
                isSaving.value = false
            }
        }
    }
}

class SettingsViewModelFactory(
    private val repository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                SettingsViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}