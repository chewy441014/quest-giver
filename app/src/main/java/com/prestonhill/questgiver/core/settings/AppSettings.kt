package com.prestonhill.questgiver.core.settings

import java.time.DayOfWeek
import java.time.LocalTime

data class AppSettings(
    val dayBoundary:
    LocalTime = LocalTime.MIDNIGHT,
    val weekStart:
    DayOfWeek = DayOfWeek.MONDAY,
    val daylightSavingEnabled:
    Boolean = true,
    val calorieGoal: Double = 1_500.0,
    val proteinGoalGrams: Double = 40.0,
)