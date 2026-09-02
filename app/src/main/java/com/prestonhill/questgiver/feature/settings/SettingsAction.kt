package com.prestonhill.questgiver.feature.settings

import java.time.DayOfWeek
import java.time.LocalTime

sealed interface SettingsAction {
    data class SetDayBoundary(
        val time: LocalTime,
    ) : SettingsAction

    data class SetWeekStart(
        val day: DayOfWeek,
    ) : SettingsAction

    data object DismissError : SettingsAction

    data class SetDaylightSaving(
        val enabled: Boolean,
    ) : SettingsAction

    data object EditNutritionGoals :
        SettingsAction

    data class ChangeCalorieGoal(
        val value: String,
    ) : SettingsAction

    data class ChangeProteinGoal(
        val value: String,
    ) : SettingsAction

    data object SaveNutritionGoals :
        SettingsAction

    data object DismissNutritionGoals :
        SettingsAction

    data class ChangeMaximumCalorieGoal(
        val value: String,
    ) : SettingsAction

    data class ChangeMaximumProteinGoal(
        val value: String,
    ) : SettingsAction

}