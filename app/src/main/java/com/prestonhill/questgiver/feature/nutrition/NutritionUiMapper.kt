package com.prestonhill.questgiver.feature.nutrition

import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.data.repository.NutritionDaySummary
import java.time.Instant
import java.time.ZoneId

class NutritionUiMapper {
    fun map(
        summary: NutritionDaySummary,
        selectedDay: AppDay,
        currentDay: AppDay,
        zoneId: ZoneId,
        settings: AppSettings,
        showDatePicker: Boolean = false,
        operationError: String? = null,
        destination: NutritionDestination? = null,
    ): NutritionScreenUiState {
        val logs =
            summary.entries
                .sortedWith(
                    compareBy(
                        {
                            it.log
                                .consumedAtEpochMillis
                        },
                        {
                            it.log.id
                        },
                    )
                )
                .map { entry ->
                    NutritionLogRowUiState(
                        logId = entry.log.id,
                        itemId =
                            entry.log.itemId,
                        itemName =
                            entry.item.name,
                        itemVersion =
                            entry.item.version,
                        versionLabel =
                            entry.item.versionLabel,
                        consumedTime =
                            Instant
                                .ofEpochMilli(
                                    entry.log
                                        .consumedAtEpochMillis
                                )
                                .atZone(zoneId)
                                .toLocalTime(),
                        weightGrams =
                            entry.log.weightGrams,
                        calories =
                            entry.calories,
                        proteinGrams =
                            entry.proteinGrams,
                        isItemArchived =
                            entry.item
                                .archivedAtEpochMillis !=
                                    null,
                    )
                }

        return NutritionScreenUiState(
            selectedDate = selectedDay.date,
            currentDate = currentDay.date,
            isCurrentDay =
                selectedDay.date ==
                        currentDay.date,
            canSelectNextDay =
                selectedDay.date <
                        currentDay.date,
            showDatePicker =
                showDatePicker,
            logs = logs,
            totalCalories =
                summary.totalCalories,
            totalProteinGrams =
                summary.totalProteinGrams,
            calorieGoal =
                settings.calorieGoal,
            proteinGoalGrams =
                settings.proteinGoalGrams,
            calorieProgress =
                progress(
                    total =
                        summary.totalCalories,
                    goal =
                        settings.calorieGoal,
                ),
            proteinProgress =
                progress(
                    total =
                        summary
                            .totalProteinGrams,
                    goal =
                        settings
                            .proteinGoalGrams,
                ),
            isLoading = false,
            destination = destination,
            operationError =
                operationError,
        )
    }

    private fun progress(
        total: Double,
        goal: Double,
    ): Float {
        if (
            !total.isFinite() ||
            !goal.isFinite() ||
            goal <= 0.0
        ) {
            return 0f
        }

        return (total / goal)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}