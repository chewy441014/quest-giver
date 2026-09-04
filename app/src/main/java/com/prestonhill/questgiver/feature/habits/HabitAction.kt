package com.prestonhill.questgiver.feature.habits

sealed interface HabitAction {
    data class AddCompletion(val habitId: Long) : HabitAction
    data class RemoveCompletion(val habitId: Long) : HabitAction
    data class InspectHabit(val habitId: Long) : HabitAction
    data class EditHabit(val habitId: Long) : HabitAction
    data class ToggleSection(
        val sectionId: String,
    ) : HabitAction
    data object DismissHabitDetails : HabitAction
    data object AddHabit : HabitAction
    data class UpdateHabitEditor(
        val editor: HabitEditorUiState
    ) : HabitAction

    data object SaveHabit : HabitAction
    data object DismissHabitEditor : HabitAction

    data class ToggleHiddenHabits(
        val sectionId: String,
    ) : HabitAction

    data class ArchiveHabit(
        val habitId: Long,
    ) : HabitAction

    data object ShowArchivedHabits : HabitAction

    data object DismissArchivedHabits : HabitAction

    data class RestoreHabit(
        val habitId: Long,
    ) : HabitAction

    data class RequestDeleteHabit(
        val habitId: Long,
    ) : HabitAction
    data object ConfirmDelete : HabitAction

    data object DismissConfirmation : HabitAction

    data object DismissOperationError : HabitAction
}