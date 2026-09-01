package com.prestonhill.questgiver.data.repository

import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity

data class NutritionItemDetails(
    val item: NutritionItemEntity,
    val components:
    List<NutritionItemComponent>,
)

data class NutritionItemComponent(
    val item: NutritionItemEntity,
    val gramsPer100g: Double,
)