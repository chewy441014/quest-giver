package com.prestonhill.questgiver.data.repository

data class NutritionItemDraft(
    val name: String,
    val versionLabel: String? = null,
    val nutrition: NutritionValuesInput,
)

sealed interface NutritionValuesInput {
    data class Per100Grams(
        val calories: Double,
        val proteinGrams: Double,
    ) : NutritionValuesInput

    data class Serving(
        val weightGrams: Double,
        val calories: Double,
        val proteinGrams: Double,
    ) : NutritionValuesInput
}

data class ComposedNutritionItemDraft(
    val name: String,
    val versionLabel: String? = null,
    val components:
    List<NutritionComponentDraft>,
)

data class NutritionComponentDraft(
    val itemId: Long,
    val gramsPer100g: Double,
)