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
}