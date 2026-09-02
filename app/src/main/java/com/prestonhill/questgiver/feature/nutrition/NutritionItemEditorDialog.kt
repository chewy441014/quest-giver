package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round

object NutritionItemEditorTags {
    const val EDITOR =
        "nutrition_item_editor"
    const val NAME =
        "nutrition_item_name"
    const val VERSION_LABEL =
        "nutrition_item_version_label"
    const val VERSION_SELECTOR =
        "nutrition_item_version_selector"
    const val PER_100_GRAMS =
        "nutrition_item_per_100_grams"
    const val SERVING =
        "nutrition_item_serving"
    const val CALORIES_PER_100 =
        "nutrition_item_calories_per_100"
    const val PROTEIN_PER_100 =
        "nutrition_item_protein_per_100"
    const val SERVING_WEIGHT =
        "nutrition_item_serving_weight"
    const val SERVING_CALORIES =
        "nutrition_item_serving_calories"
    const val SERVING_PROTEIN =
        "nutrition_item_serving_protein"
    const val ADD_COMPONENT =
        "nutrition_item_add_component"
    const val COMPONENT_SEARCH =
        "nutrition_item_component_search"
    const val SAVE =
        "nutrition_item_save"
    const val SAVE_AS_VERSION =
        "nutrition_item_save_as_version"
    const val CANCEL =
        "nutrition_item_cancel"
    const val ARCHIVE =
        "nutrition_item_archive"
    const val DELETE =
        "nutrition_item_delete"
    const val RESTORE =
        "nutrition_item_restore"
    const val REMOVE_CONFIRM =
        "nutrition_item_remove_confirm"
    const val REMOVE_CANCEL =
        "nutrition_item_remove_cancel"

    fun version(itemId: Long) =
        "nutrition_item_version_$itemId"

    fun component(itemId: Long) =
        "nutrition_item_component_$itemId"

    fun componentWeight(itemId: Long) =
        "nutrition_item_component_weight_$itemId"

    fun removeComponent(itemId: Long) =
        "nutrition_item_remove_component_$itemId"
}

@Composable
fun NutritionItemEditorDialog(
    editor: NutritionItemEditorUiState,
    onAction: (NutritionAction) -> Unit,
) {
    var versionsExpanded by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        modifier =
            Modifier.testTag(
                NutritionItemEditorTags.EDITOR
            ),
        onDismissRequest = {
            if (!editor.isBusy) {
                onAction(
                    NutritionAction
                        .DismissItemEditor
                )
            }
        },
        title = {
            Text(
                if (editor.isEditing) {
                    "Edit food"
                } else {
                    "Add food"
                }
            )
        },
        text = {
            LazyColumn(
                modifier =
                    Modifier.heightIn(
                        max = 360.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                if (
                    editor.isEditing &&
                    editor.versionOptions.size > 1
                ) {

                    item {
                        Column {
                            OutlinedButton(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            NutritionItemEditorTags
                                                .VERSION_SELECTOR
                                        ),
                                enabled =
                                    !editor.isBusy &&
                                            !editor.isDirty,
                                onClick = {
                                    versionsExpanded =
                                        true
                                },
                            ) {
                                Text(
                                    editor.versionOptions
                                        .firstOrNull {
                                            it.id ==
                                                    editor.itemId
                                        }
                                        ?.displayName
                                        ?: "v${editor.version}"
                                )
                            }

                            DropdownMenu(
                                expanded =
                                    versionsExpanded,
                                onDismissRequest = {
                                    versionsExpanded =
                                        false
                                },
                            ) {
                                editor.versionOptions
                                    .forEach {
                                            version ->
                                        DropdownMenuItem(
                                            modifier =
                                                Modifier.testTag(
                                                    NutritionItemEditorTags
                                                        .version(
                                                            version.id
                                                        )
                                                ),
                                            text = {
                                                Text(
                                                    version.displayName +
                                                            if (
                                                                version.isArchived
                                                            ) {
                                                                " · Archived"
                                                            } else {
                                                                ""
                                                            }
                                                )
                                            },
                                            onClick = {
                                                versionsExpanded =
                                                    false

                                                onAction(
                                                    NutritionAction
                                                        .SelectItemEditorVersion(
                                                            version.id
                                                        )
                                                )
                                            },
                                        )
                                    }
                            }

                            if (editor.isDirty) {
                                Text(
                                    "Save or cancel changes before switching versions.",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelMedium,
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NutritionItemEditorTags
                                        .NAME
                                ),
                        value = editor.nameText,
                        enabled = !editor.isBusy,
                        singleLine = true,
                        label = {
                            Text("Name")
                        },
                        onValueChange = {
                            onAction(
                                NutritionAction
                                    .ChangeItemName(it)
                            )
                        },
                    )
                }

                item {
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NutritionItemEditorTags
                                        .VERSION_LABEL
                                ),
                        value =
                            editor.versionLabelText,
                        enabled = !editor.isBusy,
                        singleLine = true,
                        label = {
                            Text(
                                "Version label (optional)"
                            )
                        },
                        onValueChange = {
                            onAction(
                                NutritionAction
                                    .ChangeItemVersionLabel(
                                        it
                                    )
                            )
                        },
                    )
                }

                if (!editor.isComposed) {
                    item {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                        ) {
                            FilterChip(
                                modifier =
                                    Modifier.testTag(
                                        NutritionItemEditorTags
                                            .SERVING
                                    ),
                                selected =
                                    editor.entryMode ==
                                            NutritionEntryMode
                                                .SERVING,
                                enabled =
                                    !editor.isBusy,
                                onClick = {
                                    onAction(
                                        NutritionAction
                                            .ChangeItemEntryMode(
                                                NutritionEntryMode
                                                    .SERVING
                                            )
                                    )
                                },
                                label = {
                                    Text("Serving")
                                },
                            )

                            FilterChip(
                                modifier =
                                    Modifier.testTag(
                                        NutritionItemEditorTags
                                            .PER_100_GRAMS
                                    ),
                                selected =
                                    editor.entryMode ==
                                            NutritionEntryMode
                                                .PER_100_GRAMS,
                                enabled =
                                    !editor.isBusy,
                                onClick = {
                                    onAction(
                                        NutritionAction
                                            .ChangeItemEntryMode(
                                                NutritionEntryMode
                                                    .PER_100_GRAMS
                                            )
                                    )
                                },
                                label = {
                                    Text("Per 100 g")
                                },
                            )
                        }
                    }

                    when (editor.entryMode) {
                        NutritionEntryMode.SERVING -> {
                            item {
                                NumericField(
                                    tag =
                                        NutritionItemEditorTags
                                            .SERVING_WEIGHT,
                                    value =
                                        editor.servingWeightText,
                                    label =
                                        "Serving weight (g)",
                                    enabled =
                                        !editor.isBusy,
                                    onValueChange = {
                                        onAction(
                                            NutritionAction
                                                .ChangeItemServingWeight(
                                                    it
                                                )
                                        )
                                    },
                                )
                            }

                            item {
                                NumericField(
                                    tag =
                                        NutritionItemEditorTags
                                            .SERVING_CALORIES,
                                    value =
                                        editor.servingCaloriesText,
                                    label =
                                        "Calories",
                                    enabled =
                                        !editor.isBusy,
                                    onValueChange = {
                                        onAction(
                                            NutritionAction
                                                .ChangeItemServingCalories(
                                                    it
                                                )
                                        )
                                    },
                                )
                            }

                            item {
                                NumericField(
                                    tag =
                                        NutritionItemEditorTags
                                            .SERVING_PROTEIN,
                                    value =
                                        editor.servingProteinText,
                                    label =
                                        "Protein (g)",
                                    enabled =
                                        !editor.isBusy,
                                    onValueChange = {
                                        onAction(
                                            NutritionAction
                                                .ChangeItemServingProtein(
                                                    it
                                                )
                                        )
                                    },
                                )
                            }
                        }

                        NutritionEntryMode
                            .PER_100_GRAMS -> {
                            item {
                                NumericField(
                                    tag =
                                        NutritionItemEditorTags
                                            .CALORIES_PER_100,
                                    value =
                                        editor.caloriesPer100gText,
                                    label =
                                        "Calories per 100 g",
                                    enabled =
                                        !editor.isBusy,
                                    onValueChange = {
                                        onAction(
                                            NutritionAction
                                                .ChangeItemCaloriesPer100g(
                                                    it
                                                )
                                        )
                                    },
                                )
                            }

                            item {
                                NumericField(
                                    tag =
                                        NutritionItemEditorTags
                                            .PROTEIN_PER_100,
                                    value =
                                        editor.proteinPer100gText,
                                    label =
                                        "Protein per 100 g",
                                    enabled =
                                        !editor.isBusy,
                                    onValueChange = {
                                        onAction(
                                            NutritionAction
                                                .ChangeItemProteinPer100g(
                                                    it
                                                )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (editor.isComposed) {
                    item {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    4.dp
                                ),
                        ) {
                            Text(
                                "Calculated per 100 g",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleSmall,
                            )

                            Text(
                                "${amountText(editor.calculatedCaloriesPer100g)} kcal" +
                                        " · " +
                                        "${amountText(editor.calculatedProteinPer100g)} g protein"
                            )

                            Text(
                                "${amountText(editor.componentTotalGrams)} / 100 g"
                            )

                            if (!editor.componentsValid) {
                                Text(
                                    "Component weights must total 100 g.",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                )
                            }
                        }
                    }
                }

                items(
                    items = editor.components,
                    key = {
                        it.item.id
                    },
                ) { component ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NutritionItemEditorTags
                                        .component(
                                            component
                                                .item.id
                                        )
                                ),
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    12.dp
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                        ) {
                            Text(
                                component.item
                                    .displayName,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleSmall,
                            )

                            if (
                                component.item
                                    .isArchived
                            ) {
                                Text(
                                    "Archived component"
                                )
                            }

                            NumericField(
                                tag =
                                    NutritionItemEditorTags
                                        .componentWeight(
                                            component
                                                .item.id
                                        ),
                                value =
                                    component.gramsText,
                                label =
                                    "Grams per 100 g",
                                enabled =
                                    !editor.isBusy,
                                onValueChange = {
                                    onAction(
                                        NutritionAction
                                            .ChangeItemComponentWeight(
                                                itemId =
                                                    component
                                                        .item.id,
                                                value = it,
                                            )
                                    )
                                },
                            )

                            TextButton(
                                modifier =
                                    Modifier.testTag(
                                        NutritionItemEditorTags
                                            .removeComponent(
                                                component
                                                    .item.id
                                            )
                                    ),
                                enabled =
                                    !editor.isBusy,
                                onClick = {
                                    onAction(
                                        NutritionAction
                                            .RemoveItemComponent(
                                                component
                                                    .item.id
                                            )
                                    )
                                },
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NutritionItemEditorTags
                                        .ADD_COMPONENT
                                ),
                        enabled = !editor.isBusy,
                        onClick = {
                            onAction(
                                NutritionAction
                                    .OpenItemComponentPicker
                            )
                        },
                    ) {
                        Text(
                            if (editor.isComposed) {
                                "Add another food"
                            } else {
                                "Build from existing foods"
                            }
                        )
                    }
                }

                if (
                    editor.isEditing &&
                    editor.isDirty
                ) {
                    item {
                        Text(
                            "Save or cancel changes before changing this food’s status.",
                            style =
                                MaterialTheme.typography
                                    .labelMedium,
                        )
                    }
                }

                editor.errorMessage
                    ?.let { message ->
                        item {
                            Text(
                                message,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                            )
                        }
                    }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                if (
                    editor.isEditing &&
                    editor.isDirty
                ) {
                    OutlinedButton(
                        modifier =
                            Modifier.testTag(
                                NutritionItemEditorTags
                                    .SAVE_AS_VERSION
                            ),
                        enabled =
                            editor
                                .canSaveAsVersion,
                        onClick = {
                            onAction(
                                NutritionAction
                                    .SaveItemAsVersion
                            )
                        },
                    ) {
                        Text(
                            editor.saveAsVersionText
                        )
                    }
                }

                Button(
                    modifier =
                        Modifier.testTag(
                            NutritionItemEditorTags
                                .SAVE
                        ),
                    enabled = editor.canSave,
                    onClick = {
                        onAction(
                            NutritionAction.SaveItem
                        )
                    },
                ) {
                    Text(
                        when {
                            editor.isSaving ->
                                "Saving..."

                            editor.isEditing ->
                                "Save"

                            else ->
                                "Add food"
                        }
                    )
                }
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                if (editor.isEditing) {
                    if (editor.isArchived) {
                        OutlinedButton(
                            modifier =
                                Modifier.testTag(
                                    NutritionItemEditorTags
                                        .RESTORE
                                ),
                            enabled =
                                !editor.isBusy &&
                                        !editor.isDirty,
                            onClick = {
                                onAction(
                                    NutritionAction
                                        .RestoreItem
                                )
                            },
                        ) {
                            Text("Restore")
                        }
                    }

                    val canOfferRemoval =
                        !editor.isArchived ||
                                editor.removalMode ==
                                NutritionItemRemovalModeUiState
                                    .DELETE

                    if (canOfferRemoval) {
                        TextButton(
                            modifier =
                                Modifier.testTag(
                                    when (
                                        editor.removalMode
                                    ) {
                                        NutritionItemRemovalModeUiState
                                            .ARCHIVE ->
                                            NutritionItemEditorTags
                                                .ARCHIVE

                                        NutritionItemRemovalModeUiState
                                            .DELETE,
                                        null,
                                            ->
                                            NutritionItemEditorTags
                                                .DELETE
                                    }
                                ),
                            enabled =
                                !editor.isBusy &&
                                        !editor.isDirty &&
                                        editor.removalMode !=
                                        null,
                            onClick = {
                                onAction(
                                    NutritionAction
                                        .RequestRemoveItem
                                )
                            },
                        ) {
                            Text(
                                when (
                                    editor.removalMode
                                ) {
                                    NutritionItemRemovalModeUiState
                                        .ARCHIVE ->
                                        "Archive"

                                    NutritionItemRemovalModeUiState
                                        .DELETE ->
                                        "Delete"

                                    null -> "Remove"
                                }
                            )
                        }
                    }
                }

                TextButton(
                    modifier =
                        Modifier.testTag(
                            NutritionItemEditorTags
                                .CANCEL
                        ),
                    enabled = !editor.isBusy,
                    onClick = {
                        onAction(
                            NutritionAction
                                .DismissItemEditor
                        )
                    },
                ) {
                    Text("Cancel")
                }
            }
        },
    )

    if (editor.showComponentPicker) {
        NutritionComponentPicker(
            editor = editor,
            onAction = onAction,
        )
    }

    if (editor.showRemovalConfirmation) {
        val deleting =
            editor.removalMode ==
                    NutritionItemRemovalModeUiState
                        .DELETE

        AlertDialog(
            onDismissRequest = {
                if (!editor.isRemoving) {
                    onAction(
                        NutritionAction
                            .DismissRemoveItem
                    )
                }
            },
            title = {
                Text(
                    if (deleting) {
                        "Delete food?"
                    } else {
                        "Archive food?"
                    }
                )
            },
            text = {
                Text(
                    if (deleting) {
                        "This permanently deletes the food and all of its consumption history."
                    } else {
                        "This hides the food from new selections. Existing logs and foods that use it remain intact."
                    }
                )
            },
            confirmButton = {
                Button(
                    modifier =
                        Modifier.testTag(
                            NutritionItemEditorTags
                                .REMOVE_CONFIRM
                        ),
                    enabled =
                        !editor.isRemoving,
                    onClick = {
                        onAction(
                            NutritionAction
                                .ConfirmRemoveItem
                        )
                    },
                ) {
                    Text(
                        if (deleting) {
                            "Delete"
                        } else {
                            "Archive"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    modifier =
                        Modifier.testTag(
                            NutritionItemEditorTags
                                .REMOVE_CANCEL
                        ),
                    enabled =
                        !editor.isRemoving,
                    onClick = {
                        onAction(
                            NutritionAction
                                .DismissRemoveItem
                        )
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NutritionComponentPicker(
    editor: NutritionItemEditorUiState,
    onAction: (NutritionAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onAction(
                NutritionAction
                    .DismissItemComponentPicker
            )
        },
        title = {
            Text("Add existing food")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionItemEditorTags
                                    .COMPONENT_SEARCH
                            ),
                    value =
                        editor.componentSearch,
                    singleLine = true,
                    label = {
                        Text("Search foods")
                    },
                    onValueChange = {
                        onAction(
                            NutritionAction
                                .ChangeItemComponentSearch(
                                    it
                                )
                        )
                    },
                )

                if (
                    editor.selectableComponentOptions
                        .isEmpty()
                ) {
                    Text("No matching foods.")
                } else {
                    LazyColumn(
                        modifier =
                            Modifier.heightIn(
                                max = 300.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            ),
                    ) {
                        items(
                            items =
                                editor
                                    .selectableComponentOptions,
                            key = {
                                it.id
                            },
                        ) { option ->
                            OutlinedButton(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            NutritionItemEditorTags
                                                .component(
                                                    option.id
                                                )
                                        ),
                                onClick = {
                                    onAction(
                                        NutritionAction
                                            .AddItemComponent(
                                                option.id
                                            )
                                    )
                                },
                            ) {
                                Text(
                                    option.displayName
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        NutritionAction
                            .DismissItemComponentPicker
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun NumericField(
    tag: String,
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag),
        value = value,
        enabled = enabled,
        singleLine = true,
        label = {
            Text(label)
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Decimal
            ),
        onValueChange = onValueChange,
    )
}

private fun amountText(
    value: Double,
): String {
    val rounded =
        round(value * 10.0) / 10.0

    return if (
        abs(
            rounded -
                    rounded.toLong()
        ) < 0.000_001
    ) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}