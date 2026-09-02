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
    val originalMaximumCalorieGoal: Double?,
    val originalMaximumProteinGoalGrams: Double?,
    val maximumCalorieGoalText: String,
    val maximumProteinGoalText: String,
) {
    val calorieGoal: Double?
        get() =
            requiredGoal(
                text = calorieGoalText,
                default =
                    AppSettings
                        .DEFAULT_CALORIE_GOAL,
            )

    val proteinGoalGrams: Double?
        get() =
            requiredGoal(
                text = proteinGoalText,
                default =
                    AppSettings
                        .DEFAULT_PROTEIN_GOAL_GRAMS,
            )

    val maximumCalorieGoal: Double?
        get() =
            optionalGoal(
                maximumCalorieGoalText
            )

    val maximumProteinGoalGrams: Double?
        get() =
            optionalGoal(
                maximumProteinGoalText
            )

    val canSave: Boolean
        get() {
            val calories =
                calorieGoal ?: return false

            val protein =
                proteinGoalGrams
                    ?: return false

            if (
                calories <
                AppSettings.LOWEST_CALORIE_GOAL
            ) {
                return false
            }

            if (
                protein <
                AppSettings
                    .LOWEST_PROTEIN_GOAL_GRAMS
            ) {
                return false
            }

            val maximumCalories =
                maximumCalorieGoal

            if (
                maximumCalorieGoalText
                    .isNotBlank() &&
                maximumCalories == null
            ) {
                return false
            }

            if (
                maximumCalories != null &&
                maximumCalories < calories
            ) {
                return false
            }

            val maximumProtein =
                maximumProteinGoalGrams

            if (
                maximumProteinGoalText
                    .isNotBlank() &&
                maximumProtein == null
            ) {
                return false
            }

            if (
                maximumProtein != null &&
                maximumProtein < protein
            ) {
                return false
            }

            return calories !=
                    originalCalorieGoal ||
                    maximumCalories !=
                    originalMaximumCalorieGoal ||
                    protein !=
                    originalProteinGoalGrams ||
                    maximumProtein !=
                    originalMaximumProteinGoalGrams
        }

    private fun requiredGoal(
        text: String,
        default: Double,
    ): Double? =
        if (text.isBlank()) {
            default
        } else {
            text.trim()
                .toIntOrNull()
                ?.toDouble()
        }

    private fun optionalGoal(
        text: String,
    ): Double? =
        text.trim()
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()
            ?.toDouble()

}