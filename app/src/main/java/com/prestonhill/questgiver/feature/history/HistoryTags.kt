package com.prestonhill.questgiver.feature.history

import java.time.LocalDate

object HistoryTags {
    const val TASK_DASHBOARD =
        "history_task_dashboard"
    const val ALL_TASKS =
        "history_all_tasks"

    const val ARCHIVED_TOGGLE =
        "history_archived_toggle"

    const val CONFIRM_DELETE =
        "history_confirm_delete"

    const val CANCEL_DELETE =
        "history_cancel_delete"

    const val NUTRITION_DASHBOARD =
        "history_nutrition_dashboard"

    const val NUTRITION_CALORIE_STATS =
        "history_nutrition_calorie_stats"

    const val NUTRITION_PROTEIN_STATS =
        "history_nutrition_protein_stats"

    const val NUTRITION_RANGE_CONFIRM =
        "history_nutrition_range_confirm"

    const val NUTRITION_RANGE_CANCEL =
        "history_nutrition_range_cancel"

    const val NUTRITION_RANGE_LIST =
        "history_nutrition_range_list"

    const val NUTRITION_CALORIE_CHART =
        "history_nutrition_calorie_chart"

    const val NUTRITION_PROTEIN_CHART =
        "history_nutrition_protein_chart"

    const val NUTRITION_GOAL_PROGRESS =
        "history_nutrition_goal_progress"

    const val NUTRITION_CALENDAR =
        "history_nutrition_calendar"

    const val NUTRITION_CALENDAR_PREVIOUS =
        "history_nutrition_calendar_previous"

    const val NUTRITION_CALENDAR_NEXT =
        "history_nutrition_calendar_next"

    const val NUTRITION_STAMP_ALL =
        "history_nutrition_stamp_all"

    const val NUTRITION_DAY_DIALOG =
        "history_nutrition_day_dialog"

    const val NUTRITION_DAY_CLOSE =
        "history_nutrition_day_close"

    const val TASK_STAMP_PREFIX =
        "history_task_stamp"

    const val TASK_STAMP_CALENDAR =
        "${TASK_STAMP_PREFIX}_calendar"

    const val TASK_STAMP_PREVIOUS =
        "${TASK_STAMP_PREFIX}_previous"

    const val TASK_STAMP_NEXT =
        "${TASK_STAMP_PREFIX}_next"

    const val TASK_STAMP_ALL =
        "${TASK_STAMP_PREFIX}_all"

    const val TASK_STAMP_DAY_DIALOG =
        "${TASK_STAMP_PREFIX}_day_dialog"

    const val TASK_STAMP_DAY_CLOSE =
        "${TASK_STAMP_PREFIX}_day_close"

    const val HABIT_DASHBOARD =
        "history_habit_dashboard"

    const val HABIT_ARCHIVED_TOGGLE =
        "history_habit_archived_toggle"

    const val HABIT_STAMP_PREFIX =
        "history_habit_stamp"

    const val HABIT_STAMP_CALENDAR =
        "${HABIT_STAMP_PREFIX}_calendar"

    const val HABIT_STAMP_PREVIOUS =
        "${HABIT_STAMP_PREFIX}_previous"

    const val HABIT_STAMP_NEXT =
        "${HABIT_STAMP_PREFIX}_next"

    const val HABIT_STAMP_ALL =
        "${HABIT_STAMP_PREFIX}_all"

    const val HABIT_STAMP_DAY_DIALOG =
        "${HABIT_STAMP_PREFIX}_day_dialog"

    const val HABIT_RANGE_LIST =
        "history_habit_range_list"

    const val HABIT_RANGE_CONFIRM =
        "history_habit_range_confirm"

    const val HABIT_RANGE_CANCEL =
        "history_habit_range_cancel"

    const val HABIT_COMPLETION_CHART =
        "history_habit_completion_chart"

    const val HABIT_COMPLETION_CHART_ALL =
        "history_habit_completion_chart_all"

    const val HABIT_COMPLETION_PLOT =
        "history_habit_completion_plot"

    fun habitCompletionChartFilter(
        habitId: Long,
    ) =
        "history_habit_completion_chart_filter_" +
                habitId

    fun habitRange(
        preset: HabitHistoryRangePreset,
    ) =
        "history_habit_range_${preset.name}"

    fun habitPerformance(
        habitId: Long,
    ) =
        "history_habit_performance_$habitId"

    fun habitStampFilter(
        key: String,
    ) =
        "${HABIT_STAMP_PREFIX}_filter_$key"

    fun habitStampDay(
        date: LocalDate,
    ) =
        "${HABIT_STAMP_PREFIX}_day_$date"

    fun habitDayStamp(
        key: String,
    ) =
        "${HABIT_STAMP_PREFIX}_" +
                "day_stamp_$key"

    fun habitStampGroup(
        groupLabel: String,
    ) =
        "${HABIT_STAMP_PREFIX}_" +
                "group_$groupLabel"

    fun taskStampFilter(
        key: String,
    ) =
        "${TASK_STAMP_PREFIX}_filter_$key"

    fun taskStampDay(
        date: LocalDate,
    ) =
        "${TASK_STAMP_PREFIX}_day_$date"

    fun taskDayStamp(
        key: String,
    ) =
        "${TASK_STAMP_PREFIX}_" +
                "day_stamp_$key"

    fun taskStampGroup(
        groupLabel: String,
    ) =
        "${TASK_STAMP_PREFIX}_" +
                "group_$groupLabel"

    fun nutritionStampFilter(
        type: NutritionStampType,
    ) =
        "history_nutrition_stamp_${type.name}"

    fun nutritionCalendarDay(
        date: LocalDate,
    ) =
        "history_nutrition_day_$date"

    fun nutritionDayStamp(
        type: NutritionStampType,
    ) =
        "history_nutrition_day_stamp_${type.name}"

    fun nutritionRange(
        preset: NutritionHistoryRangePreset,
    ) =
        "history_nutrition_range_" +
                preset.name

    fun deleteTask(taskId: Long) =
        "history_delete_task_$taskId"

    fun archiveTask(taskId: Long) =
        "history_archive_task_$taskId"

    fun restoreTask(taskId: Long) =
        "history_restore_task_$taskId"

    fun taskCompletion(taskId: Long) =
        "history_task_completion_$taskId"
    fun tab(section: HistorySection) =
        "history_tab_${section.name}"

    fun task(taskId: Long) =
        "history_task_$taskId"
}