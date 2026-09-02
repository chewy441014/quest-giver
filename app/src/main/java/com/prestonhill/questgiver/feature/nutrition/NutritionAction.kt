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

    data class SelectLogFood(
        val nameKey: String,
    ) : NutritionAction

    data object DismissLogVersions :
        NutritionAction

    data class ChangeLogItemSort(
        val sort: NutritionItemSort,
    ) : NutritionAction

    data class ChangeLogMinimumProtein(
        val value: String,
    ) : NutritionAction

    data class ChangeLogMinimumProteinRatio(
        val value: String,
    ) : NutritionAction

    data object ResetLogItemFilters :
        NutritionAction

    data class ChangeManageSearch(
        val value: String,
    ) : NutritionAction

    data class ChangeManageSort(
        val sort: NutritionItemSort,
    ) : NutritionAction

    data class ChangeManageMinimumProtein(
        val value: String,
    ) : NutritionAction

    data class ChangeManageMinimumProteinRatio(
        val value: String,
    ) : NutritionAction

    data class ChangeManageArchiveFilter(
        val filter: NutritionArchiveFilter,
    ) : NutritionAction

    data object ResetManageFilters :
        NutritionAction

    data object OpenAddItem :
        NutritionAction

    data class InspectItem(
        val itemId: Long,
    ) : NutritionAction

    data object DismissItemEditor :
        NutritionAction

    data class SelectItemEditorVersion(
        val itemId: Long,
    ) : NutritionAction

    data class ChangeItemName(
        val value: String,
    ) : NutritionAction

    data class ChangeItemVersionLabel(
        val value: String,
    ) : NutritionAction

    data class ChangeItemEntryMode(
        val mode: NutritionEntryMode,
    ) : NutritionAction

    data class ChangeItemCaloriesPer100g(
        val value: String,
    ) : NutritionAction

    data class ChangeItemProteinPer100g(
        val value: String,
    ) : NutritionAction

    data class ChangeItemServingWeight(
        val value: String,
    ) : NutritionAction

    data class ChangeItemServingCalories(
        val value: String,
    ) : NutritionAction

    data class ChangeItemServingProtein(
        val value: String,
    ) : NutritionAction

    data object OpenItemComponentPicker :
        NutritionAction

    data object DismissItemComponentPicker :
        NutritionAction

    data class ChangeItemComponentSearch(
        val value: String,
    ) : NutritionAction

    data class AddItemComponent(
        val itemId: Long,
    ) : NutritionAction

    data class ChangeItemComponentWeight(
        val itemId: Long,
        val value: String,
    ) : NutritionAction

    data class RemoveItemComponent(
        val itemId: Long,
    ) : NutritionAction

    data object SaveItem :
        NutritionAction

    data object SaveItemAsVersion :
        NutritionAction
}