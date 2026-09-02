package com.prestonhill.questgiver.data.repository

import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import kotlin.math.max

data class NutritionItemUsage(
    val item: NutritionItemEntity,
    val lastConsumedAtEpochMillis:
    Long?,
    val totalConsumedGrams:
    Double = 0.0,
    val consumptionCount:
    Int = 0,
) {
    val latestActivityEpochMillis: Long
        get() =
            max(
                item.createdAtEpochMillis,
                lastConsumedAtEpochMillis
                    ?: Long.MIN_VALUE,
            )
}