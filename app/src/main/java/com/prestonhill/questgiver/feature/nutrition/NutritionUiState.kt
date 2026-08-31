package com.prestonhill.questgiver.feature.nutrition

import java.time.LocalDate
import java.time.LocalTime

data class NutritionLogRowUiState(
    val logId: Long,
    val itemId: Long,
    val itemName: String,
    val itemVersion: Int,
    val versionLabel: String?,
    val consumedTime: LocalTime,
    val weightGrams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val isItemArchived: Boolean,
)

data class NutritionScreenUiState(
    val selectedDate: LocalDate? = null,
    val currentDate: LocalDate? = null,
    val isCurrentDay: Boolean = true,
    val canSelectNextDay: Boolean = false,
    val showDatePicker: Boolean = false,
    val logs:
    List<NutritionLogRowUiState> =
        emptyList(),
    val totalCalories: Double = 0.0,
    val totalProteinGrams: Double = 0.0,
    val calorieGoal: Double = 1_500.0,
    val proteinGoalGrams: Double = 40.0,
    val calorieProgress: Float = 0f,
    val proteinProgress: Float = 0f,
    val isLoading: Boolean = true,
    val operationError: String? = null,
    val destination: NutritionDestination? = null,
)

sealed interface NutritionDestination {
    data object AddLog :
        NutritionDestination

    data class EditLog(
        val logId: Long,
    ) : NutritionDestination

    data object Manage :
        NutritionDestination
}