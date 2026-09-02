package com.prestonhill.questgiver.feature.nutrition

import java.util.Locale
import kotlin.math.abs

enum class NutritionEntryMode {
    PER_100_GRAMS,
    SERVING,
}

data class NutritionItemComponentUiState(
    val item:
    NutritionItemOptionUiState,
    val gramsText: String,
) {
    val gramsPer100g: Double?
        get() =
            gramsText.trim()
                .toDoubleOrNull()

    val isValid: Boolean
        get() {
            val grams =
                gramsPer100g
                    ?: return false

            return grams.isFinite() &&
                    grams > 0.0
        }

    val calorieContribution: Double
        get() =
            gramsPer100g
                ?.takeIf {
                    it.isFinite()
                }
                ?.let { grams ->
                    item.caloriesPer100g *
                            grams / 100.0
                }
                ?: 0.0

    val proteinContribution: Double
        get() =
            gramsPer100g
                ?.takeIf {
                    it.isFinite()
                }
                ?.let { grams ->
                    item.proteinPer100g *
                            grams / 100.0
                }
                ?: 0.0
}

data class NutritionItemEditorUiState(
    val itemId: Long? = null,
    val originalNameKey: String? = null,
    val version: Int = 0,
    val knownItems:
    List<NutritionItemOptionUiState> =
        emptyList(),
    val nameText: String = "",
    val versionLabelText: String = "",
    val entryMode: NutritionEntryMode = NutritionEntryMode.SERVING,
    val caloriesPer100gText: String = "",
    val proteinPer100gText: String = "",
    val servingWeightText: String = "",
    val servingCaloriesText: String = "",
    val servingProteinText: String = "",
    val components:
    List<NutritionItemComponentUiState> =
        emptyList(),
    val componentSearch: String = "",
    val showComponentPicker: Boolean = false,
    val isSaving: Boolean = false,
    val isRemoving: Boolean = false,
    val showRemovalConfirmation:
    Boolean = false,
    val removalMode:
    NutritionItemRemovalModeUiState? =
        null,
    val errorMessage: String? = null,
    val initialSnapshot:
    NutritionItemEditorSnapshot? =
        null,
    val isArchived: Boolean = false,
) {
    val isEditing: Boolean
        get() = itemId != null

    val isBusy: Boolean
        get() =
            isSaving || isRemoving

    val isComposed: Boolean
        get() = components.isNotEmpty()

    val normalizedNameKey: String
        get() =
            nameText.trim()
                .replace(
                    whitespace,
                    " ",
                )
                .lowercase(Locale.ROOT)

    val versionOptions:
            List<NutritionItemOptionUiState>
        get() =
            originalNameKey
                ?.let { nameKey ->
                    knownItems
                        .filter {
                            it.nameKey == nameKey
                        }
                        .sortedBy {
                            it.version
                        }
                }
                .orEmpty()

    val selectableComponentOptions:
            List<NutritionItemOptionUiState>
        get() {
            val selectedIds =
                components
                    .map { it.item.id }
                    .toSet()

            val query =
                componentSearch.trim()

            return knownItems
                .filter { option ->
                    !option.isArchived &&
                            option.id != itemId &&
                            option.id !in selectedIds &&
                            (
                                    query.isEmpty() ||
                                            option.name.contains(
                                                query,
                                                ignoreCase = true,
                                            ) ||
                                            option.versionLabel
                                                ?.contains(
                                                    query,
                                                    ignoreCase = true,
                                                ) == true ||
                                            "v${option.version}"
                                                .contains(
                                                    query,
                                                    ignoreCase = true,
                                                )
                                    )
                }
                .sortedWith(
                    compareBy<
                            NutritionItemOptionUiState
                            > {
                        it.nameKey
                    }
                        .thenBy {
                            it.version
                        }
                )
        }

    val componentTotalGrams: Double
        get() =
            components.sumOf {
                it.gramsPer100g
                    ?.takeIf(Double::isFinite)
                    ?: 0.0
            }

    val componentWeightRemaining: Double
        get() =
            100.0 -
                    componentTotalGrams

    val calculatedCaloriesPer100g: Double
        get() =
            components.sumOf {
                it.calorieContribution
            }

    val calculatedProteinPer100g: Double
        get() =
            components.sumOf {
                it.proteinContribution
            }

    val componentsValid: Boolean
        get() =
            components.isNotEmpty() &&
                    components.all {
                        it.isValid
                    } &&
                    abs(
                        componentTotalGrams -
                                100.0
                    ) <= COMPONENT_WEIGHT_TOLERANCE

    val manualNutritionValid: Boolean
        get() =
            when (entryMode) {
                NutritionEntryMode
                    .PER_100_GRAMS ->
                    validNonNegative(
                        caloriesPer100gText
                    ) &&
                            validNonNegative(
                                proteinPer100gText
                            )

                NutritionEntryMode.SERVING ->
                    validPositive(
                        servingWeightText
                    ) &&
                            validNonNegative(
                                servingCaloriesText
                            ) &&
                            validNonNegative(
                                servingProteinText
                            )
            }

    val currentSnapshot:
            NutritionItemEditorSnapshot
        get() =
            NutritionItemEditorSnapshot(
                nameText = nameText,
                versionLabelText =
                    versionLabelText,
                entryMode =
                    entryMode.takeUnless {
                        isComposed
                    },
                caloriesPer100gText =
                    caloriesPer100gText
                        .takeUnless {
                            isComposed
                        },
                proteinPer100gText =
                    proteinPer100gText
                        .takeUnless {
                            isComposed
                        },
                servingWeightText =
                    servingWeightText
                        .takeUnless {
                            isComposed
                        },
                servingCaloriesText =
                    servingCaloriesText
                        .takeUnless {
                            isComposed
                        },
                servingProteinText =
                    servingProteinText
                        .takeUnless {
                            isComposed
                        },
                components =
                    components.map {
                        NutritionItemComponentSnapshot(
                            itemId = it.item.id,
                            gramsText =
                                it.gramsText,
                        )
                    },
            )

    val isDirty: Boolean
        get() =
            initialSnapshot == null ||
                    currentSnapshot !=
                    initialSnapshot

    val canSave: Boolean
        get() =
            !isBusy &&
                    nameText.isNotBlank() &&
                    isDirty &&
                    if (isComposed) {
                        componentsValid
                    } else {
                        manualNutritionValid
                    }

    val canSaveAsVersion: Boolean
        get() =
            isEditing && canSave

    val nextVersion: Int
        get() =
            knownItems
                .filter {
                    it.nameKey ==
                            normalizedNameKey
                }
                .maxOfOrNull {
                    it.version
                }
                ?.plus(1)
                ?: 0

    val saveAsVersionText: String
        get() =
            "Save as v$nextVersion"
}

data class NutritionItemEditorSnapshot(
    val nameText: String,
    val versionLabelText: String,
    val entryMode: NutritionEntryMode?,
    val caloriesPer100gText: String?,
    val proteinPer100gText: String?,
    val servingWeightText: String?,
    val servingCaloriesText: String?,
    val servingProteinText: String?,
    val components:
    List<NutritionItemComponentSnapshot>,
)

data class NutritionItemComponentSnapshot(
    val itemId: Long,
    val gramsText: String,
)

enum class NutritionItemRemovalModeUiState {
    ARCHIVE,
    DELETE,
}

private fun validNonNegative(
    text: String,
): Boolean {
    val value =
        text.trim()
            .toDoubleOrNull()
            ?: return false

    return value.isFinite() &&
            value >= 0.0
}

private fun validPositive(
    text: String,
): Boolean {
    val value =
        text.trim()
            .toDoubleOrNull()
            ?: return false

    return value.isFinite() &&
            value > 0.0
}

private val whitespace =
    Regex("\\s+")

private const val COMPONENT_WEIGHT_TOLERANCE =
    0.001