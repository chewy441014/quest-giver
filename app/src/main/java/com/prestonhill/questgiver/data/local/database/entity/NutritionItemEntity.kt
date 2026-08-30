package com.prestonhill.questgiver.data.local.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "nutrition_items",
    indices = [
        Index(
            value = [
                "nameKey",
                "version",
            ],
            unique = true,
        ),
        Index(
            value = ["archivedAtEpochMillis"],
        ),
    ],
)
data class NutritionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    // Trimmed, case-normalized name used for
    // grouping and version allocation.
    val nameKey: String,

    val version: Int = 0,
    val versionLabel: String? = null,

    // Manually entered for base items and
    // calculated for composed items.
    val caloriesPer100g: Double,
    val proteinPer100g: Double,

    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
)