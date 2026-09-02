package com.prestonhill.questgiver.feature.nutrition

import kotlin.math.abs
import kotlin.math.round

internal fun nutritionAmountText(
    value: Double,
): String {
    val rounded =
        round(value * 10.0) / 10.0

    return if (
        abs(
            rounded -
                    rounded.toLong()
        ) < 0.000_001
    ) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

internal fun validNutritionFilterText(
    value: String,
): Boolean {
    if (value.isBlank()) {
        return true
    }

    val parsed =
        value.trim()
            .toDoubleOrNull()
            ?: return false

    return parsed.isFinite() &&
            parsed >= 0.0
}

internal fun NutritionArchiveFilter.displayName():
        String =
    when (this) {
        NutritionArchiveFilter.ACTIVE ->
            "Active"

        NutritionArchiveFilter.ARCHIVED ->
            "Archived"

        NutritionArchiveFilter.ALL ->
            "All"
    }

internal fun NutritionItemSort.displayName():
        String =
    when (this) {
        NutritionItemSort.RECENT ->
            "Recent"

        NutritionItemSort.NEWEST_ADDED ->
            "Newest"

        NutritionItemSort.NAME ->
            "Name"

        NutritionItemSort.CALORIES ->
            "Calories"

        NutritionItemSort.PROTEIN ->
            "Protein"

        NutritionItemSort.PROTEIN_RATIO ->
            "Protein ratio"
    }