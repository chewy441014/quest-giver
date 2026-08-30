package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "food_logs",
    foreignKeys = [
        ForeignKey(
            entity =
                NutritionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["itemId"]),
        Index(
            value =
                ["consumedAtEpochMillis"]
        ),
    ],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val itemId: Long,
    val consumedAtEpochMillis: Long,
    val weightGrams: Double,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)