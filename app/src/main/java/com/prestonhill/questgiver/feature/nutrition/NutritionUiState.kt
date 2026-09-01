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
    val logEditor: NutritionLogEditorUiState? = null,
)

data class NutritionItemOptionUiState(
    val id: Long,
    val name: String,
    val version: Int,
    val versionLabel: String?,
    val isArchived: Boolean,
) {
    val displayName: String
        get() =
            "$name · " +
                    (
                            versionLabel
                                ?: "v$version"
                            )
}

data class NutritionLogEditorUiState(
    val logId: Long? = null,
    val date: LocalDate,
    val itemOptions:
    List<NutritionItemOptionUiState>,
    val selectedItemId: Long? = null,
    val itemSearch: String = "",
    val weightText: String = "",
    val time: LocalTime,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation:
    Boolean = false,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean
        get() = logId != null

    val isBusy: Boolean
        get() =
            isSaving || isDeleting

    val weightGrams: Double?
        get() =
            weightText
                .trim()
                .toDoubleOrNull()

    val filteredItemOptions:
            List<NutritionItemOptionUiState>
        get() {
            val query =
                itemSearch.trim()

            if (query.isEmpty()) {
                return itemOptions
            }

            return itemOptions.filter {
                it.displayName.contains(
                    query,
                    ignoreCase = true,
                )
            }
        }

    val canSave: Boolean
        get() {
            val weight =
                weightGrams
                    ?: return false

            return !isBusy &&
                    selectedItemId != null &&
                    itemOptions.any {
                        it.id == selectedItemId
                    } &&
                    weight.isFinite() &&
                    weight > 0.0
        }
}

sealed interface NutritionDestination {
    data object AddLog :
        NutritionDestination

    data class EditLog(
        val logId: Long,
    ) : NutritionDestination

    data object Manage :
        NutritionDestination
}