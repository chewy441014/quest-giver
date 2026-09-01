package com.prestonhill.questgiver.data.repository

import androidx.room3.withWriteTransaction
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.local.database.entity.NutritionComponentEntity
import com.prestonhill.questgiver.data.local.database.entity.FoodLogEntity
import kotlinx.coroutines.flow.combine
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

    fun observeNutritionBetween(
        startTimestampMillis: Long,
        endTimestampMillis: Long,
    ): Flow<NutritionDaySummary> {
        require(
            startTimestampMillis <
                    endTimestampMillis
        )

        return combine(
            dao.observeLogsBetween(
                startTimestampMillis =
                    startTimestampMillis,
                endTimestampMillis =
                    endTimestampMillis,
            ),
            dao.observeAllItems(),
        ) { logs, items ->
            val itemsById =
                items.associateBy { it.id }

            val entries =
                logs.mapNotNull { log ->
                    val item =
                        itemsById[log.itemId]
                            ?: return@mapNotNull null

                    val multiplier =
                        log.weightGrams / 100.0

                    NutritionLogEntry(
                        log = log,
                        item = item,
                        calories =
                            item.caloriesPer100g *
                                    multiplier,
                        proteinGrams =
                            item.proteinPer100g *
                                    multiplier,
                    )
                }

            NutritionDaySummary(
                entries = entries,
                totalCalories =
                    entries.sumOf {
                        it.calories
                    },
                totalProteinGrams =
                    entries.sumOf {
                        it.proteinGrams
                    },
            )
        }
    }

    suspend fun getLog(
        logId: Long,
    ): FoodLogEntity? =
        dao.getLog(logId)

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

    suspend fun getRemovalMode(
        itemId: Long,
    ): NutritionItemRemovalMode? =
        database.withWriteTransaction {
            dao.getItem(itemId)
                ?: return@withWriteTransaction null

            if (
                dao.countIncomingReferences(
                    itemId
                ) > 0
            ) {
                NutritionItemRemovalMode.ARCHIVE
            } else {
                NutritionItemRemovalMode.DELETE
            }
        }

    suspend fun removeItem(
        itemId: Long,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): NutritionItemRemovalResult =
        database.withWriteTransaction {
            val item =
                dao.getItem(itemId) ?: return@withWriteTransaction NutritionItemRemovalResult.ITEM_NOT_FOUND

            val isReferenced =
                dao.countIncomingReferences(
                    itemId
                ) > 0

            if (isReferenced) {
                if (
                    item.archivedAtEpochMillis !=
                    null
                ) {
                    return@withWriteTransaction NutritionItemRemovalResult .ALREADY_ARCHIVED
                }

                check(
                    dao.archiveReferencedItem(
                        itemId = itemId,
                        timestampMillis =
                            timestampMillis,
                    ) == 1
                )

                NutritionItemRemovalResult.ARCHIVED
            } else {
                check(
                    dao.deleteUnreferencedItem(
                        itemId
                    ) == 1
                )

                NutritionItemRemovalResult.DELETED
            }
        }

    suspend fun restoreItem(
        itemId: Long,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Boolean =
        dao.restoreItem(
            itemId = itemId,
            timestampMillis =
                timestampMillis,
        ) == 1

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

            val updated =
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
                )

            if (updated != 1) {
                return@withWriteTransaction false
            }

            // Saving manual nutrition removes any
            // previous component structure.
            dao.deleteComponents(itemId)

            recalculateAncestors(
                changedItemId = itemId,
                timestampMillis =
                    timestampMillis,
            )

            true
        }

    suspend fun updateComposedItem(
        itemId: Long,
        draft: ComposedNutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Boolean =
        database.withWriteTransaction {
            val existing =
                dao.getItem(itemId)
                    ?: return@withWriteTransaction false

            val identity =
                cleanIdentity(
                    name = draft.name,
                    versionLabel =
                        draft.versionLabel,
                )

            val existingComponentIds =
                dao.getComponents(itemId)
                    .map { it.componentItemId }
                    .toSet()

            val prepared =
                prepareComponents(
                    components =
                        draft.components,
                    allowedArchivedIds =
                        existingComponentIds,
                )

            requireNoCycle(
                parentItemId = itemId,
                componentIds =
                    prepared.components
                        .map { it.itemId },
            )

            val updated =
                dao.updateItem(
                    existing.copy(
                        name = identity.name,
                        nameKey =
                            identity.nameKey,
                        versionLabel =
                            identity.versionLabel,
                        caloriesPer100g =
                            prepared
                                .caloriesPer100g,
                        proteinPer100g =
                            prepared
                                .proteinPer100g,
                        updatedAtEpochMillis =
                            timestampMillis,
                    )
                )

            if (updated != 1) {
                return@withWriteTransaction false
            }

            replaceComponents(
                parentItemId = itemId,
                components =
                    prepared.components,
            )

            recalculateAncestors(
                changedItemId = itemId,
                timestampMillis =
                    timestampMillis,
            )

            true
        }

    suspend fun saveComposedAsVersion(
        itemId: Long,
        draft: ComposedNutritionItemDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Long? =
        database.withWriteTransaction {
            dao.getItem(itemId)
                ?: return@withWriteTransaction null

            val identity =
                cleanIdentity(
                    name = draft.name,
                    versionLabel =
                        draft.versionLabel,
                )

            // Archived components already used by
            // the source version may be retained.
            val existingComponentIds =
                dao.getComponents(itemId)
                    .map { it.componentItemId }
                    .toSet()

            val prepared =
                prepareComponents(
                    components =
                        draft.components,
                    allowedArchivedIds =
                        existingComponentIds,
                )

            val version =
                dao.getMaxVersion(
                    identity.nameKey
                ) + 1

            val newItemId =
                dao.insertItem(
                    NutritionItemEntity(
                        name = identity.name,
                        nameKey =
                            identity.nameKey,
                        version = version,
                        versionLabel =
                            identity.versionLabel,
                        caloriesPer100g =
                            prepared
                                .caloriesPer100g,
                        proteinPer100g =
                            prepared
                                .proteinPer100g,
                        createdAtEpochMillis =
                            timestampMillis,
                        updatedAtEpochMillis =
                            timestampMillis,
                    )
                )

            insertComponents(
                parentItemId = newItemId,
                components =
                    prepared.components,
            )

            newItemId
        }

    suspend fun createLog(
        draft: FoodLogDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Long? {
        validateLogDraft(draft)

        return database.withWriteTransaction {
            val item =
                dao.getItem(draft.itemId)
                    ?: return@withWriteTransaction null

            if (
                item.archivedAtEpochMillis !=
                null
            ) {
                return@withWriteTransaction null
            }

            dao.insertLog(
                FoodLogEntity(
                    itemId = draft.itemId,
                    consumedAtEpochMillis =
                        draft.consumedAtEpochMillis,
                    weightGrams =
                        draft.weightGrams,
                    createdAtEpochMillis =
                        timestampMillis,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            )
        }
    }

    suspend fun updateLog(
        logId: Long,
        draft: FoodLogDraft,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Boolean {
        validateLogDraft(draft)

        return database.withWriteTransaction {
            val existing =
                dao.getLog(logId)
                    ?: return@withWriteTransaction false

            val selectedItem =
                dao.getItem(draft.itemId)
                    ?: return@withWriteTransaction false

            val canUseSelectedItem =
                selectedItem
                    .archivedAtEpochMillis == null ||
                        selectedItem.id ==
                        existing.itemId

            if (!canUseSelectedItem) {
                return@withWriteTransaction false
            }

            dao.updateLog(
                existing.copy(
                    itemId = draft.itemId,
                    consumedAtEpochMillis =
                        draft.consumedAtEpochMillis,
                    weightGrams =
                        draft.weightGrams,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            ) == 1
        }
    }

    suspend fun deleteLog(
        logId: Long,
    ): Boolean =
        dao.deleteLog(logId) == 1

    private fun validateLogDraft(
        draft: FoodLogDraft,
    ) {
        require(draft.itemId > 0L)

        require(
            draft.weightGrams.isFinite() &&
                    draft.weightGrams > 0.0
        )
    }

    fun observeItemUsage():
            Flow<List<NutritionItemUsage>> =
        combine(
            dao.observeAllItems(),
            dao.observeAllLogs(),
        ) { items, logs ->
            val lastConsumedByItem =
                logs.groupingBy {
                    it.itemId
                }
                    .fold(
                        initialValue =
                            Long.MIN_VALUE
                    ) { latest, log ->
                        maxOf(
                            latest,
                            log.consumedAtEpochMillis,
                        )
                    }

            items.map { item ->
                NutritionItemUsage(
                    item = item,
                    lastConsumedAtEpochMillis =
                        lastConsumedByItem[
                            item.id
                        ]
                            ?.takeUnless {
                                it == Long.MIN_VALUE
                            },
                )
            }
        }

    private suspend fun prepareComponents(
        components:
        List<NutritionComponentDraft>,
        allowedArchivedIds: Set<Long>,
    ): PreparedComponents {
        val cleaned =
            cleanComponents(components)

        val itemsById =
            dao.getItemsByIds(
                cleaned.map {
                    it.itemId
                }
            )
                .associateBy { it.id }

        require(
            itemsById.size ==
                    cleaned.size
        )

        require(
            itemsById.values.all { item ->
                item.archivedAtEpochMillis ==
                        null ||
                        item.id in allowedArchivedIds
            }
        )

        val caloriesPer100g =
            cleaned.sumOf { component ->
                requireNotNull(
                    itemsById[component.itemId]
                ).caloriesPer100g *
                        component.gramsPer100g /
                        100.0
            }

        val proteinPer100g =
            cleaned.sumOf { component ->
                requireNotNull(
                    itemsById[component.itemId]
                ).proteinPer100g *
                        component.gramsPer100g /
                        100.0
            }

        validateNutrition(
            calories = caloriesPer100g,
            proteinGrams =
                proteinPer100g,
        )

        return PreparedComponents(
            components = cleaned,
            caloriesPer100g =
                caloriesPer100g,
            proteinPer100g =
                proteinPer100g,
        )
    }

    private suspend fun replaceComponents(
        parentItemId: Long,
        components:
        List<NutritionComponentDraft>,
    ) {
        dao.deleteComponents(parentItemId)

        insertComponents(
            parentItemId = parentItemId,
            components = components,
        )
    }

    private suspend fun insertComponents(
        parentItemId: Long,
        components:
        List<NutritionComponentDraft>,
    ) {
        dao.insertComponents(
            components.mapIndexed {
                    index,
                    component,
                ->
                NutritionComponentEntity(
                    parentItemId =
                        parentItemId,
                    componentItemId =
                        component.itemId,
                    gramsPer100g =
                        component.gramsPer100g,
                    displayOrder = index,
                )
            }
        )
    }

    private suspend fun requireNoCycle(
        parentItemId: Long,
        componentIds: List<Long>,
    ) {
        val relationships =
            dao.getAllComponents()

        val childrenByParent =
            relationships.groupBy(
                keySelector = {
                    it.parentItemId
                },
                valueTransform = {
                    it.componentItemId
                },
            )

        componentIds.forEach { componentId ->
            require(
                !reachesItem(
                    currentItemId =
                        componentId,
                    targetItemId =
                        parentItemId,
                    childrenByParent =
                        childrenByParent,
                    visited = mutableSetOf(),
                )
            )
        }
    }

    private fun reachesItem(
        currentItemId: Long,
        targetItemId: Long,
        childrenByParent:
        Map<Long, List<Long>>,
        visited: MutableSet<Long>,
    ): Boolean {
        if (currentItemId == targetItemId) {
            return true
        }

        if (!visited.add(currentItemId)) {
            return false
        }

        return childrenByParent[
            currentItemId
        ]
            .orEmpty()
            .any { childId ->
                reachesItem(
                    currentItemId = childId,
                    targetItemId =
                        targetItemId,
                    childrenByParent =
                        childrenByParent,
                    visited = visited,
                )
            }
    }

    private suspend fun recalculateAncestors(
        changedItemId: Long,
        timestampMillis: Long,
        path: Set<Long> = emptySet(),
    ) {
        require(changedItemId !in path)

        val nextPath =
            path + changedItemId

        dao.getParentIds(changedItemId)
            .forEach { parentItemId ->
                require(parentItemId !in nextPath)

                recalculateComposedItem(
                    itemId = parentItemId,
                    timestampMillis =
                        timestampMillis,
                )

                recalculateAncestors(
                    changedItemId =
                        parentItemId,
                    timestampMillis =
                        timestampMillis,
                    path = nextPath,
                )
            }
    }

    private suspend fun recalculateComposedItem(
        itemId: Long,
        timestampMillis: Long,
    ) {
        val item =
            dao.getItem(itemId)
                ?: return

        val existingComponents =
            dao.getComponents(itemId)

        if (existingComponents.isEmpty()) {
            return
        }

        val componentDrafts =
            existingComponents.map {
                NutritionComponentDraft(
                    itemId =
                        it.componentItemId,
                    gramsPer100g =
                        it.gramsPer100g,
                )
            }

        val prepared =
            prepareComponents(
                components =
                    componentDrafts,
                // Existing references remain valid
                // even if components are archived.
                allowedArchivedIds =
                    componentDrafts
                        .map { it.itemId }
                        .toSet(),
            )

        require(
            dao.updateItem(
                item.copy(
                    caloriesPer100g =
                        prepared
                            .caloriesPer100g,
                    proteinPer100g =
                        prepared
                            .proteinPer100g,
                    updatedAtEpochMillis =
                        timestampMillis,
                )
            ) == 1
        )
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

    private data class PreparedComponents(
        val components:
        List<NutritionComponentDraft>,
        val caloriesPer100g: Double,
        val proteinPer100g: Double,
    )

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

enum class NutritionItemRemovalMode {
    ARCHIVE,
    DELETE,
}

enum class NutritionItemRemovalResult {
    ARCHIVED,
    DELETED,
    ITEM_NOT_FOUND,
    ALREADY_ARCHIVED,
}