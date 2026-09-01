package com.prestonhill.questgiver.feature.nutrition

import java.time.LocalDate
import java.time.LocalTime

data class NutritionLogRowUiState(
    val logId: Long,
    val itemId: Long,
    val itemName: String,
    val itemVersion: Int,
    val versionLabel: String?,
    val consumedTime: LocalTime,
    val weightGrams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val isItemArchived: Boolean,
)

data class NutritionScreenUiState(
    val selectedDate: LocalDate? = null,
    val currentDate: LocalDate? = null,
    val isCurrentDay: Boolean = true,
    val canSelectNextDay: Boolean = false,
    val showDatePicker: Boolean = false,
    val logs:
    List<NutritionLogRowUiState> =
        emptyList(),
    val totalCalories: Double = 0.0,
    val totalProteinGrams: Double = 0.0,
    val calorieGoal: Double = 1_500.0,
    val proteinGoalGrams: Double = 40.0,
    val calorieProgress: Float = 0f,
    val proteinProgress: Float = 0f,
    val isLoading: Boolean = true,
    val operationError: String? = null,
    val destination: NutritionDestination? = null,
    val logEditor: NutritionLogEditorUiState? = null,
    val manage: NutritionManageUiState = NutritionManageUiState(),
    val itemEditor: NutritionItemEditorUiState? = null,
)

data class NutritionItemOptionUiState(
    val id: Long,
    val name: String,
    val nameKey: String,
    val version: Int,
    val versionLabel: String?,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val createdAtEpochMillis: Long,
    val lastConsumedAtEpochMillis:
    Long?,
    val isArchived: Boolean,
) {
    val displayName: String
        get() =
            "$name · " +
                    (
                            versionLabel
                                ?: "v$version"
                            )

    val latestActivityEpochMillis: Long
        get() =
            maxOf(
                createdAtEpochMillis,
                lastConsumedAtEpochMillis
                    ?: Long.MIN_VALUE,
            )

    val proteinPer100Calories: Double
        get() =
            when {
                caloriesPer100g > 0.0 ->
                    proteinPer100g /
                            caloriesPer100g *
                            100.0

                proteinPer100g > 0.0 ->
                    Double
                        .POSITIVE_INFINITY

                else -> 0.0
            }
}

enum class NutritionItemSort {
    RECENT,
    NEWEST_ADDED,
    NAME,
    CALORIES,
    PROTEIN,
    PROTEIN_RATIO,
}

enum class NutritionArchiveFilter {
    ACTIVE,
    ARCHIVED,
    ALL,
}

data class NutritionFoodGroupUiState(
    val name: String,
    val nameKey: String,
    val versions:
    List<NutritionItemOptionUiState>,
) {
    val latestActivityEpochMillis: Long
        get() =
            versions.maxOf {
                it.latestActivityEpochMillis
            }

    val newestCreatedAtEpochMillis: Long
        get() =
            versions.maxOf {
                it.createdAtEpochMillis
            }

    val maximumCaloriesPer100g: Double
        get() =
            versions.maxOf {
                it.caloriesPer100g
            }

    val maximumProteinPer100g: Double
        get() =
            versions.maxOf {
                it.proteinPer100g
            }

    val maximumProteinRatio: Double
        get() =
            versions.maxOf {
                it.proteinPer100Calories
            }
}

data class NutritionLogEditorUiState(
    val logId: Long? = null,
    val date: LocalDate,
    val itemOptions:
    List<NutritionItemOptionUiState>,
    val selectedItemId: Long? = null,
    val itemSearch: String = "",
    val weightText: String = "",
    val time: LocalTime,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation:
    Boolean = false,
    val errorMessage: String? = null,
    val itemSort:
    NutritionItemSort =
        NutritionItemSort.RECENT,
    val minimumProteinText: String = "",
    val minimumProteinRatioText:
    String = "",
    val versionGroupNameKey:
    String? = null,
) {
    val isEditing: Boolean
        get() = logId != null

    val isBusy: Boolean
        get() =
            isSaving || isDeleting

    val weightGrams: Double?
        get() =
            weightText
                .trim()
                .toDoubleOrNull()

    val filtersValid: Boolean
        get() =
            validOptionalNumber(
                minimumProteinText
            ) &&
                    validOptionalNumber(
                        minimumProteinRatioText
                    )

    val visibleFoodGroups:
            List<NutritionFoodGroupUiState>
        get() {
            val groups =
                filteredFoodGroups(
                    itemOptions = itemOptions,
                    query = itemSearch,
                    sort = itemSort,
                    minimumProteinText =
                        minimumProteinText,
                    minimumProteinRatioText =
                        minimumProteinRatioText,
                )

            val isDefaultView =
                itemSearch.isBlank() &&
                        minimumProteinText.isBlank() &&
                        minimumProteinRatioText
                            .isBlank() &&
                        itemSort ==
                        NutritionItemSort.RECENT

            return if (isDefaultView) {
                groups.take(
                    DEFAULT_ITEM_RESULT_LIMIT
                )
            } else {
                groups
            }
        }

    val versionChoices:
            List<NutritionItemOptionUiState>
        get() =
            versionGroupNameKey
                ?.let { nameKey ->
                    itemOptions
                        .filter {
                            it.nameKey == nameKey
                        }
                        .sortedBy {
                            it.version
                        }
                }
                .orEmpty()

    val canSave: Boolean
        get() {
            val weight =
                weightGrams
                    ?: return false

            return !isBusy &&
                    selectedItemId != null &&
                    itemOptions.any {
                        it.id == selectedItemId
                    } &&
                    weight.isFinite() &&
                    weight > 0.0
        }
}

data class NutritionManageUiState(
    val itemOptions:
    List<NutritionItemOptionUiState> =
        emptyList(),
    val itemSearch: String = "",
    val itemSort:
    NutritionItemSort =
        NutritionItemSort.RECENT,
    val minimumProteinText: String = "",
    val minimumProteinRatioText:
    String = "",
    val archiveFilter:
    NutritionArchiveFilter =
        NutritionArchiveFilter.ACTIVE,
) {
    val filtersValid: Boolean
        get() =
            validOptionalNumber(
                minimumProteinText
            ) &&
                    validOptionalNumber(
                        minimumProteinRatioText
                    )

    val visibleFoodGroups:
            List<NutritionFoodGroupUiState>
        get() {
            val archiveFiltered =
                itemOptions.filter { option ->
                    when (archiveFilter) {
                        NutritionArchiveFilter.ACTIVE ->
                            !option.isArchived

                        NutritionArchiveFilter.ARCHIVED ->
                            option.isArchived

                        NutritionArchiveFilter.ALL ->
                            true
                    }
                }

            return filteredFoodGroups(
                itemOptions = archiveFiltered,
                query = itemSearch,
                sort = itemSort,
                minimumProteinText =
                    minimumProteinText,
                minimumProteinRatioText =
                    minimumProteinRatioText,
            )
        }
}

private fun filteredFoodGroups(
    itemOptions:
    List<NutritionItemOptionUiState>,
    query: String,
    sort: NutritionItemSort,
    minimumProteinText: String,
    minimumProteinRatioText: String,
): List<NutritionFoodGroupUiState> {
    if (
        !validOptionalNumber(
            minimumProteinText
        ) ||
        !validOptionalNumber(
            minimumProteinRatioText
        )
    ) {
        return emptyList()
    }

    val normalizedQuery =
        query.trim()

    val minimumProtein =
        optionalNumber(
            minimumProteinText
        )

    val minimumRatio =
        optionalNumber(
            minimumProteinRatioText
        )

    return itemOptions
        .filter { option ->
            val searchMatches =
                normalizedQuery.isEmpty() ||
                        option.name.contains(
                            normalizedQuery,
                            ignoreCase = true,
                        ) ||
                        option.versionLabel
                            ?.contains(
                                normalizedQuery,
                                ignoreCase = true,
                            ) == true ||
                        "v${option.version}"
                            .contains(
                                normalizedQuery,
                                ignoreCase = true,
                            )

            val proteinMatches =
                minimumProtein == null ||
                        option.proteinPer100g >=
                        minimumProtein

            val ratioMatches =
                minimumRatio == null ||
                        option.proteinPer100Calories >=
                        minimumRatio

            searchMatches &&
                    proteinMatches &&
                    ratioMatches
        }
        .groupBy {
            it.nameKey
        }
        .map { (nameKey, versions) ->
            NutritionFoodGroupUiState(
                name = versions.first().name,
                nameKey = nameKey,
                versions =
                    versions.sortedBy {
                        it.version
                    },
            )
        }
        .sortedWith(
            foodGroupComparator(sort)
        )
}

private fun optionalNumber(
    value: String,
): Double? =
    value.trim()
        .takeIf(String::isNotEmpty)
        ?.toDoubleOrNull()

private fun validOptionalNumber(
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

private fun foodGroupComparator(
    sort: NutritionItemSort,
): Comparator<NutritionFoodGroupUiState> =
    when (sort) {
        NutritionItemSort.RECENT ->
            compareByDescending<
                    NutritionFoodGroupUiState
                    > {
                it.latestActivityEpochMillis
            }
                .thenBy {
                    it.nameKey
                }

        NutritionItemSort.NEWEST_ADDED ->
            compareByDescending<
                    NutritionFoodGroupUiState
                    > {
                it.newestCreatedAtEpochMillis
            }
                .thenBy {
                    it.nameKey
                }

        NutritionItemSort.NAME ->
            compareBy {
                it.nameKey
            }

        NutritionItemSort.CALORIES ->
            compareByDescending {
                it.maximumCaloriesPer100g
            }

        NutritionItemSort.PROTEIN ->
            compareByDescending {
                it.maximumProteinPer100g
            }

        NutritionItemSort.PROTEIN_RATIO ->
            compareByDescending {
                it.maximumProteinRatio
            }
    }

private const val DEFAULT_ITEM_RESULT_LIMIT =
    10

sealed interface NutritionDestination {
    data object AddLog :
        NutritionDestination

    data class EditLog(
        val logId: Long,
    ) : NutritionDestination

    data object Manage :
        NutritionDestination
}