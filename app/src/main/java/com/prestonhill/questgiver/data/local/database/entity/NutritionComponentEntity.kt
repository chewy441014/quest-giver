package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "nutrition_components",
    primaryKeys = [
        "parentItemId",
        "componentItemId",
    ],
    foreignKeys = [
        ForeignKey(
            entity =
                NutritionItemEntity::class,
            parentColumns = ["id"],
            childColumns =
                ["parentItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity =
                NutritionItemEntity::class,
            parentColumns = ["id"],
            childColumns =
                ["componentItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["parentItemId"]),
        Index(value = ["componentItemId"]),
    ],
)
data class NutritionComponentEntity(
    val parentItemId: Long,
    val componentItemId: Long,

    // Component grams in 100 g of the
    // completed parent item.
    val gramsPer100g: Double,

    val displayOrder: Int = 0,
)