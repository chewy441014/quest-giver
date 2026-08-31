package com.prestonhill.questgiver.feature.settings

import com.prestonhill.questgiver.core.settings.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val nutritionGoalsEditor: NutritionGoalsEditorUiState? = null,
)

data class NutritionGoalsEditorUiState(
    val originalCalorieGoal: Double,
    val originalProteinGoalGrams: Double,
    val calorieGoalText: String,
    val proteinGoalText: String,
) {
    val calorieGoal: Double?
        get() =
            calorieGoalText
                .trim()
                .toIntOrNull()
                ?.toDouble()

    val proteinGoalGrams: Double?
        get() =
            proteinGoalText
                .trim()
                .toIntOrNull()
                ?.toDouble()

    val canSave: Boolean
        get() {
            val calories =
                calorieGoal ?: return false

            val protein =
                proteinGoalGrams
                    ?: return false

            return calories.isFinite() &&
                    calories > 0.0 &&
                    protein.isFinite() &&
                    protein > 0.0 &&
                    (
                            calories !=
                                    originalCalorieGoal ||
                                    protein !=
                                    originalProteinGoalGrams
                            )
        }
}