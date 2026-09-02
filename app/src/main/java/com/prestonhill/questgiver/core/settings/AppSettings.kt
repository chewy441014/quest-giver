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
    val calorieGoal: Double =
        DEFAULT_CALORIE_GOAL,
    val maximumCalorieGoal: Double? =
        null,
    val proteinGoalGrams: Double =
        DEFAULT_PROTEIN_GOAL_GRAMS,
    val maximumProteinGoalGrams:
    Double? = null,
) {
    companion object {
        const val DEFAULT_CALORIE_GOAL =
            1_500.0

        const val DEFAULT_PROTEIN_GOAL_GRAMS =
            40.0

        const val LOWEST_CALORIE_GOAL =
            400.0

        const val LOWEST_PROTEIN_GOAL_GRAMS =
            5.0
    }
}