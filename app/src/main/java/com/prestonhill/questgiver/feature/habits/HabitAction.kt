package com.prestonhill.questgiver.feature.habits

sealed interface HabitAction {
    data class AddCompletion(val habitId: Long) : HabitAction
    data class RemoveCompletion(val habitId: Long) : HabitAction
    data class InspectHabit(val habitId: Long) : HabitAction
    data class EditHabit(val habitId: Long) : HabitAction
    data class ToggleCategory(val category: HabitCategory) : HabitAction

    data object DismissHabitDetails : HabitAction
    data object AddHabit : HabitAction
}