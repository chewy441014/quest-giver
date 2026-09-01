package com.prestonhill.questgiver.feature.nutrition

import java.time.LocalDate
import java.time.LocalTime

sealed interface NutritionAction {
    data object OpenDatePicker :
        NutritionAction

    data object DismissDatePicker :
        NutritionAction

    data class SelectDate(
        val date: LocalDate,
    ) : NutritionAction

    data object OpenAddLog :
        NutritionAction

    data class InspectLog(
        val logId: Long,
    ) : NutritionAction

    data object OpenManage :
        NutritionAction

    data object DismissOperationError :
        NutritionAction

    data object DismissDestination :
        NutritionAction

    data class ChangeLogItemSearch(
        val value: String,
    ) : NutritionAction

    data class SelectLogItem(
        val itemId: Long,
    ) : NutritionAction

    data class ChangeLogWeight(
        val value: String,
    ) : NutritionAction

    data class ChangeLogTime(
        val time: LocalTime,
    ) : NutritionAction

    data object SaveLog :
        NutritionAction

    data object DismissLogEditor :
        NutritionAction

    data object RequestDeleteLog :
        NutritionAction

    data object DismissDeleteLog :
        NutritionAction

    data object DeleteLog :
        NutritionAction
}