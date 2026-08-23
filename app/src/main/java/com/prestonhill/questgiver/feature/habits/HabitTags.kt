package com.prestonhill.questgiver.feature.habits

object HabitTags {
    const val ADD = "habit_add"
    const val NAME = "habit_name"
    const val SAVE = "habit_save"
    const val EDIT = "habit_edit"
    const val ARCHIVE = "habit_archive"
    const val ARCHIVED = "habit_archived"
    const val CONFIRM_DELETE = "habit_confirm_delete"
    const val CANCEL_DELETE = "habit_cancel_delete"

    fun row(habitId: Long) =
        "habit_row_$habitId"

    fun completion(habitId: Long) =
        "habit_completion_$habitId"

    fun delete(habitId: Long) =
        "habit_delete_$habitId"

    fun restore(habitId: Long) =
        "habit_restore_$habitId"

    fun hidden(category: HabitCategory) =
        "habit_hidden_${category.name}"

    fun visibility(visibility: HabitScheduleVisibility) =
        "habit_visibility_${visibility.name}"
}