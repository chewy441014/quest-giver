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
    const val HISTORY_CATEGORY =
        "habit_history_category"

    const val SECTIONS = "habit_sections"
    const val SECTION_MANAGER =
        "habit_section_manager"
    const val ADD_SECTION =
        "habit_add_section"
    const val SECTION_NAME =
        "habit_section_name"
    const val SAVE_SECTION =
        "habit_save_section"
    const val CANCEL_SECTION =
        "habit_cancel_section"
    const val CONFIRM_SECTION_DELETE =
        "habit_confirm_section_delete"
    const val CANCEL_SECTION_DELETE =
        "habit_cancel_section_delete"

    const val NEW_SECTION =
        "habit_new_section"

    const val NEW_SECTION_NAME =
        "habit_new_section_name"

    fun sectionRow(sectionId: String) =
        "habit_section_row_$sectionId"

    fun moveSectionUp(sectionId: String) =
        "habit_section_up_$sectionId"

    fun moveSectionDown(sectionId: String) =
        "habit_section_down_$sectionId"

    fun deleteSection(sectionId: String) =
        "habit_section_delete_$sectionId"

    fun editorSection(sectionId: String) =
        "habit_editor_section_$sectionId"

    fun hidden(sectionId: String) =
        "habit_hidden_$sectionId"

    fun row(habitId: Long) =
        "habit_row_$habitId"

    fun completion(habitId: Long) =
        "habit_completion_$habitId"

    fun delete(habitId: Long) =
        "habit_delete_$habitId"

    fun restore(habitId: Long) =
        "habit_restore_$habitId"

    fun visibility(visibility: HabitScheduleVisibility) =
        "habit_visibility_${visibility.name}"
}