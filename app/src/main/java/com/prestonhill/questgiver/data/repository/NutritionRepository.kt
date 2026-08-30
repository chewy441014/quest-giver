package com.prestonhill.questgiver.data.repository

import androidx.room3.withWriteTransaction
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionComponentEntity
import kotlin.math.abs
import java.util.Locale
import kotlinx.coroutines.flow.Flow

class NutritionRepository(
    private val database: QuestGiverDatabase,
) {
    private val dao =
        database.nutritionDao()

    fun observeActiveItems():
            Flow<List<NutritionItemEntity>> =
        dao.observeActiveItems()

    fun observeAllItems():
            Flow<List<NutritionItemEntity>> =
        dao.observeAllItems()

    fun observeArchivedItems():
            Flow<List<NutritionItemEntity>> =
        dao.observeArchivedItems()

    suspend fun getItem(
        itemId: Long,
    ): NutritionItemEntity? =
        dao.getItem(itemId)

    suspend fun getVersions(
        name: String,
    ): List<NutritionItemEntity> {
        val cleanedName = cleanName(name)

        return dao.getVersions(
            nameKey(cleanedName)
        )
    }

    suspend fun createItem(
        draft: NutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Long =
        database.withWriteTransaction {
            val cleaned =
                cleanDraft(draft)

            val version =
                dao.getMaxVersion(
                    cleaned.nameKey
                ) + 1

            dao.insertItem(
                NutritionItemEntity(
                    name = cleaned.name,
                    nameKey =
                        cleaned.nameKey,
                    version = version,
                    versionLabel =
                        cleaned.versionLabel,
                    caloriesPer100g =
                        cleaned.caloriesPer100g,
                    proteinPer100g =
                        cleaned.proteinPer100g,
                    createdAtEpochMillis =
                        timestampMillis,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            )
        }

    suspend fun createComposedItem(
        draft: ComposedNutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Long =
        database.withWriteTransaction {
            val identity =
                cleanIdentity(
                    name = draft.name,
                    versionLabel =
                        draft.versionLabel,
                )

            val components =
                cleanComponents(
                    draft.components
                )

            val componentItems =
                dao.getItemsByIds(
                    components.map {
                        it.itemId
                    }
                )
                    .associateBy { it.id }

            require(
                componentItems.size ==
                        components.size
            )

            require(
                componentItems.values.all {
                    it.archivedAtEpochMillis ==
                            null
                }
            )

            val caloriesPer100g =
                components.sumOf { component ->
                    requireNotNull(
                        componentItems[
                            component.itemId
                        ]
                    ).caloriesPer100g *
                            component.gramsPer100g /
                            100.0
                }

            val proteinPer100g =
                components.sumOf { component ->
                    requireNotNull(
                        componentItems[
                            component.itemId
                        ]
                    ).proteinPer100g *
                            component.gramsPer100g /
                            100.0
                }

            validateNutrition(
                calories = caloriesPer100g,
                proteinGrams =
                    proteinPer100g,
            )

            val version =
                dao.getMaxVersion(
                    identity.nameKey
                ) + 1

            val parentId =
                dao.insertItem(
                    NutritionItemEntity(
                        name = identity.name,
                        nameKey =
                            identity.nameKey,
                        version = version,
                        versionLabel =
                            identity.versionLabel,
                        caloriesPer100g =
                            caloriesPer100g,
                        proteinPer100g =
                            proteinPer100g,
                        createdAtEpochMillis =
                            timestampMillis,
                        updatedAtEpochMillis =
                            timestampMillis,
                    )
                )

            dao.insertComponents(
                components.mapIndexed {
                        index,
                        component,
                    ->
                    NutritionComponentEntity(
                        parentItemId = parentId,
                        componentItemId =
                            component.itemId,
                        gramsPer100g =
                            component.gramsPer100g,
                        displayOrder = index,
                    )
                }
            )

            parentId
        }

    suspend fun updateItem(
        itemId: Long,
        draft: NutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Boolean =
        database.withWriteTransaction {
            val existing =
                dao.getItem(itemId)
                    ?: return@withWriteTransaction false

            val cleaned =
                cleanDraft(draft)

            dao.updateItem(
                existing.copy(
                    name = cleaned.name,
                    nameKey =
                        cleaned.nameKey,
                    versionLabel =
                        cleaned.versionLabel,
                    caloriesPer100g =
                        cleaned.caloriesPer100g,
                    proteinPer100g =
                        cleaned.proteinPer100g,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            ) == 1
        }

    suspend fun saveAsVersion(
        itemId: Long,
        draft: NutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Long? =
        database.withWriteTransaction {
            dao.getItem(itemId)
                ?: return@withWriteTransaction null

            val cleaned =
                cleanDraft(draft)

            val version =
                dao.getMaxVersion(
                    cleaned.nameKey
                ) + 1

            dao.insertItem(
                NutritionItemEntity(
                    name = cleaned.name,
                    nameKey =
                        cleaned.nameKey,
                    version = version,
                    versionLabel =
                        cleaned.versionLabel,
                    caloriesPer100g =
                        cleaned.caloriesPer100g,
                    proteinPer100g =
                        cleaned.proteinPer100g,
                    createdAtEpochMillis =
                        timestampMillis,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            )
        }

    private fun cleanComponents(
        components:
        List<NutritionComponentDraft>,
    ): List<NutritionComponentDraft> {
        require(components.isNotEmpty())

        require(
            components.all { component ->
                component.itemId > 0L &&
                        component.gramsPer100g
                            .isFinite() &&
                        component.gramsPer100g >
                        0.0
            }
        )

        require(
            components
                .map { it.itemId }
                .distinct()
                .size == components.size
        )

        val total =
            components.sumOf {
                it.gramsPer100g
            }

        require(
            total.isFinite() &&
                    abs(total - 100.0) <=
                    COMPONENT_WEIGHT_TOLERANCE
        )

        return components
    }

    private fun cleanIdentity(
        name: String,
        versionLabel: String?,
    ): CleanNutritionIdentity {
        val cleanedName =
            cleanName(name)

        val cleanedLabel =
            versionLabel
                ?.trim()
                ?.replace(
                    whitespace,
                    " ",
                )
                ?.takeIf(String::isNotEmpty)

        return CleanNutritionIdentity(
            name = cleanedName,
            nameKey =
                nameKey(cleanedName),
            versionLabel =
                cleanedLabel,
        )
    }

    private fun cleanDraft(
        draft: NutritionItemDraft,
    ): CleanNutritionItem {
        val identity =
            cleanIdentity(
                name = draft.name,
                versionLabel =
                    draft.versionLabel,
            )

        val nutrition =
            normalize(draft.nutrition)

        return CleanNutritionItem(
            name = identity.name,
            nameKey = identity.nameKey,
            versionLabel =
                identity.versionLabel,
            caloriesPer100g =
                nutrition.caloriesPer100g,
            proteinPer100g =
                nutrition.proteinPer100g,
        )
    }

    private fun cleanName(
        name: String,
    ): String {
        val cleaned =
            name.trim()
                .replace(
                    whitespace,
                    " ",
                )

        require(cleaned.isNotEmpty())

        return cleaned
    }

    private fun nameKey(
        name: String,
    ): String =
        name.lowercase(Locale.ROOT)

    private fun normalize(
        input: NutritionValuesInput,
    ): NormalizedNutrition =
        when (input) {
            is NutritionValuesInput
            .Per100Grams -> {
                validateNutrition(
                    calories =
                        input.calories,
                    proteinGrams =
                        input.proteinGrams,
                )

                NormalizedNutrition(
                    caloriesPer100g =
                        input.calories,
                    proteinPer100g =
                        input.proteinGrams,
                )
            }

            is NutritionValuesInput
            .Serving -> {
                require(
                    input.weightGrams
                        .isFinite() &&
                            input.weightGrams > 0.0
                )

                validateNutrition(
                    calories =
                        input.calories,
                    proteinGrams =
                        input.proteinGrams,
                )

                NormalizedNutrition(
                    caloriesPer100g =
                        input.calories /
                                input.weightGrams *
                                100.0,
                    proteinPer100g =
                        input.proteinGrams /
                                input.weightGrams *
                                100.0,
                )
            }
        }

    private fun validateNutrition(
        calories: Double,
        proteinGrams: Double,
    ) {
        require(
            calories.isFinite() &&
                    calories >= 0.0
        )

        require(
            proteinGrams.isFinite() &&
                    proteinGrams >= 0.0
        )
    }

    private data class CleanNutritionIdentity(
        val name: String,
        val nameKey: String,
        val versionLabel: String?,
    )

    private data class CleanNutritionItem(
        val name: String,
        val nameKey: String,
        val versionLabel: String?,
        val caloriesPer100g: Double,
        val proteinPer100g: Double,
    )

    private data class NormalizedNutrition(
        val caloriesPer100g: Double,
        val proteinPer100g: Double,
    )

    private companion object {
        val whitespace = Regex("\\s+")
        const val COMPONENT_WEIGHT_TOLERANCE =
            0.001
    }
}