package com.prestonhill.questgiver.data.repository

import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity

data class FoodLogDraft(
    val itemId: Long,
    val consumedAtEpochMillis: Long,
    val weightGrams: Double,
)

data class NutritionLogEntry(
    val log: FoodLogEntity,
    val item: NutritionItemEntity,
    val calories: Double,
    val proteinGrams: Double,
)

data class NutritionDaySummary(
    val entries: List<NutritionLogEntry>,
    val totalCalories: Double,
    val totalProteinGrams: Double,
)