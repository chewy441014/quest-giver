package com.prestonhill.questgiver.feature.nutrition

import java.time.LocalDate

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
}