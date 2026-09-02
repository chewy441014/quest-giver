@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)

package com.prestonhill.questgiver.feature.nutrition

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalLocale

object NutritionLogEditorTags {
    const val EDITOR =
        "nutrition_log_editor"
    const val SEARCH =
        "nutrition_log_search"
    const val FILTER =
        "nutrition_log_filter"
    const val SORT =
        "nutrition_log_sort"
    const val WEIGHT =
        "nutrition_log_weight"
    const val TIME =
        "nutrition_log_time"
    const val SAVE =
        "nutrition_log_save"
    const val CANCEL =
        "nutrition_log_cancel"
    const val DELETE =
        "nutrition_log_delete"

    const val FILTER_PROTEIN =
        "nutrition_filter_protein"
    const val FILTER_RATIO =
        "nutrition_filter_ratio"
    const val FILTER_APPLY =
        "nutrition_filter_apply"
    const val FILTER_RESET =
        "nutrition_filter_reset"

    const val TIME_CONFIRM =
        "nutrition_time_confirm"
    const val TIME_CANCEL =
        "nutrition_time_cancel"

    const val DELETE_CONFIRM =
        "nutrition_log_delete_confirm"
    const val DELETE_CANCEL =
        "nutrition_log_delete_cancel"

    fun food(nameKey: String) =
        "nutrition_food_$nameKey"

    fun version(itemId: Long) =
        "nutrition_version_$itemId"

    fun sort(sort: NutritionItemSort) =
        "nutrition_sort_${sort.name}"
}

@Composable
fun NutritionLogEditorDialog(
    editor: NutritionLogEditorUiState,
    onAction: (NutritionAction) -> Unit,
) {
    var showFilters by remember {
        mutableStateOf(false)
    }

    var showTimePicker by remember {
        mutableStateOf(false)
    }

    var sortExpanded by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        modifier =
            Modifier.testTag(
                NutritionLogEditorTags.EDITOR
            ),
        onDismissRequest = {
            if (!editor.isBusy) {
                onAction(
                    NutritionAction
                        .DismissLogEditor
                )
            }
        },
        title = {
            Text(
                if (editor.isEditing) {
                    "Edit food log"
                } else {
                    "Add food log"
                }
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    ),
            ) {
                Text(
                    editor.date.format(
                        DateTimeFormatter
                            .ofPattern(
                                "EEE, MMM d, yyyy",
                                LocalLocale.current.platformLocale,
                            )
                    )
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionLogEditorTags
                                    .SEARCH
                            ),
                    value =
                        editor.itemSearch,
                    enabled = !editor.isBusy,
                    singleLine = true,
                    label = {
                        Text("Search foods")
                    },
                    onValueChange = {
                        onAction(
                            NutritionAction
                                .ChangeLogItemSearch(
                                    it
                                )
                        )
                    },
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),
                ) {
                    OutlinedButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(
                                    NutritionLogEditorTags
                                        .FILTER
                                ),
                        enabled =
                            !editor.isBusy,
                        onClick = {
                            showFilters = true
                        },
                    ) {
                        Text(
                            if (
                                editor
                                    .minimumProteinText
                                    .isBlank() &&
                                editor
                                    .minimumProteinRatioText
                                    .isBlank()
                            ) {
                                "Filter"
                            } else {
                                "Filter active"
                            }
                        )
                    }

                    Box(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        OutlinedButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NutritionLogEditorTags
                                            .SORT
                                    ),
                            enabled =
                                !editor.isBusy,
                            onClick = {
                                sortExpanded = true
                            },
                        ) {
                            Text(
                                editor.itemSort
                                    .displayName()
                            )
                        }

                        DropdownMenu(
                            expanded =
                                sortExpanded,
                            onDismissRequest = {
                                sortExpanded = false
                            },
                        ) {
                            NutritionItemSort.entries
                                .forEach { sort ->
                                    DropdownMenuItem(
                                        modifier =
                                            Modifier.testTag(
                                                NutritionLogEditorTags
                                                    .sort(sort)
                                            ),
                                        text = {
                                            Text(
                                                sort.displayName()
                                            )
                                        },
                                        onClick = {
                                            sortExpanded = false

                                            onAction(
                                                NutritionAction
                                                    .ChangeLogItemSort(
                                                        sort
                                                    )
                                            )
                                        },
                                    )
                                }
                        }
                    }
                }

                FoodSearchResults(
                    editor = editor,
                    onAction = onAction,
                )

                editor.selectedItemId
                    ?.let { selectedId ->
                        editor.itemOptions
                            .firstOrNull {
                                it.id ==
                                        selectedId
                            }
                    }
                    ?.let { selected ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical =
                                            4.dp
                                    ),
                        ) {
                            Text(
                                "Selected food",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium,
                            )

                            Text(
                                selected.displayName,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                            )

                            if (
                                selected.isArchived
                            ) {
                                Text(
                                    "Archived food",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelMedium,
                                )
                            }
                        }
                    }

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionLogEditorTags
                                    .WEIGHT
                            ),
                    value =
                        editor.weightText,
                    enabled = !editor.isBusy,
                    singleLine = true,
                    label = {
                        Text("Weight (g)")
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),
                    onValueChange = {
                        onAction(
                            NutritionAction
                                .ChangeLogWeight(
                                    it
                                )
                        )
                    },
                )

                val context =
                    LocalContext.current

                val timeFormatter =
                    remember(
                        DateFormat
                            .is24HourFormat(
                                context
                            )
                    ) {
                        DateTimeFormatter
                            .ofLocalizedTime(
                                FormatStyle.SHORT
                            )
                    }

                OutlinedButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionLogEditorTags
                                    .TIME
                            ),
                    enabled = !editor.isBusy,
                    onClick = {
                        showTimePicker = true
                    },
                ) {
                    Text(
                        "Consumed at " +
                                editor.time.format(
                                    timeFormatter
                                )
                    )
                }

                editor.errorMessage
                    ?.let { message ->
                        Text(
                            text = message,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                        )
                    }
            }
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .SAVE
                    ),
                enabled = editor.canSave,
                onClick = {
                    onAction(
                        NutritionAction.SaveLog
                    )
                },
            ) {
                Text(
                    when {
                        editor.isSaving ->
                            "Saving..."

                        editor.isEditing ->
                            "Save"

                        else -> "Add"
                    }
                )
            }
        },
        dismissButton = {
            Row {
                if (editor.isEditing) {
                    TextButton(
                        modifier =
                            Modifier.testTag(
                                NutritionLogEditorTags
                                    .DELETE
                            ),
                        enabled =
                            !editor.isBusy,
                        onClick = {
                            onAction(
                                NutritionAction
                                    .RequestDeleteLog
                            )
                        },
                    ) {
                        Text(
                            if (
                                editor.isDeleting
                            ) {
                                "Deleting..."
                            } else {
                                "Delete"
                            }
                        )
                    }
                }

                TextButton(
                    modifier =
                        Modifier.testTag(
                            NutritionLogEditorTags
                                .CANCEL
                        ),
                    enabled = !editor.isBusy,
                    onClick = {
                        onAction(
                            NutritionAction
                                .DismissLogEditor
                        )
                    },
                ) {
                    Text("Cancel")
                }
            }
        },
    )

    if (showFilters) {
        LogItemFilterDialog(
            editor = editor,
            onDismiss = {
                showFilters = false
            },
            onAction = onAction,
        )
    }

    if (showTimePicker) {
        LogTimePickerDialog(
            time = editor.time,
            onDismiss = {
                showTimePicker = false
            },
            onTimeSelected = {
                showTimePicker = false

                onAction(
                    NutritionAction
                        .ChangeLogTime(it)
                )
            },
        )
    }

    if (
        editor.versionGroupNameKey !=
        null
    ) {
        LogVersionDialog(
            versions =
                editor.versionChoices,
            onAction = onAction,
        )
    }

    if (
        editor.showDeleteConfirmation
    ) {
        LogDeleteConfirmation(
            editor = editor,
            onAction = onAction,
        )
    }
}

@Composable
private fun FoodSearchResults(
    editor: NutritionLogEditorUiState,
    onAction: (NutritionAction) -> Unit,
) {
    if (!editor.filtersValid) {
        Text(
            "Enter valid filter values.",
            color =
                MaterialTheme
                    .colorScheme.error,
        )

        return
    }

    if (
        editor.visibleFoodGroups
            .isEmpty()
    ) {
        Text("No matching foods.")
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
        verticalArrangement =
            Arrangement.spacedBy(6.dp),
    ) {
        items(
            items =
                editor.visibleFoodGroups,
            key = {
                it.nameKey
            },
        ) { group ->
            OutlinedButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            NutritionLogEditorTags
                                .food(
                                    group.nameKey
                                )
                        ),
                enabled = !editor.isBusy,
                onClick = {
                    onAction(
                        NutritionAction
                            .SelectLogFood(
                                group.nameKey
                            )
                    )
                },
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    val representative =
                        group.representativeVersion

                    Text("${group.name} · " + (representative.versionLabel ?: "v${representative.version}"))

                    if (
                        group.versions.size >
                        1
                    ) {
                        Text(
                            "${group.versions.size} versions",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemFilterDialog(
    editor: NutritionLogEditorUiState,
    onDismiss: () -> Unit,
    onAction: (NutritionAction) -> Unit,
) {
    var proteinText by remember {
        mutableStateOf(
            editor.minimumProteinText
        )
    }

    var ratioText by remember {
        mutableStateOf(
            editor.minimumProteinRatioText
        )
    }

    val valid =
        validNutritionFilterText(proteinText) &&
                validNutritionFilterText(ratioText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Filter foods")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    ),
            ) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionLogEditorTags
                                    .FILTER_PROTEIN
                            ),
                    value = proteinText,
                    singleLine = true,
                    label = {
                        Text(
                            "Minimum protein per 100 g"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),
                    onValueChange = {
                        proteinText = it
                    },
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionLogEditorTags
                                    .FILTER_RATIO
                            ),
                    value = ratioText,
                    singleLine = true,
                    label = {
                        Text(
                            "Minimum protein per 100 kcal"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),
                    onValueChange = {
                        ratioText = it
                    },
                )
            }
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .FILTER_APPLY
                    ),
                enabled = valid,
                onClick = {
                    onAction(
                        NutritionAction
                            .ChangeLogMinimumProtein(
                                proteinText
                            )
                    )

                    onAction(
                        NutritionAction
                            .ChangeLogMinimumProteinRatio(
                                ratioText
                            )
                    )

                    onDismiss()
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .FILTER_RESET
                    ),
                onClick = {
                    onAction(
                        NutritionAction
                            .ResetLogItemFilters
                    )

                    onDismiss()
                },
            ) {
                Text("Reset")
            }
        },
    )
}

@Composable
private fun LogTimePickerDialog(
    time: LocalTime,
    onDismiss: () -> Unit,
    onTimeSelected:
        (LocalTime) -> Unit,
) {
    val context = LocalContext.current

    val state =
        rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour =
                DateFormat.is24HourFormat(
                    context
                ),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .TIME_CONFIRM
                    ),
                onClick = {
                    onTimeSelected(
                        LocalTime.of(
                            state.hour,
                            state.minute,
                        )
                    )
                },
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .TIME_CANCEL
                    ),
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LogVersionDialog(
    versions:
    List<NutritionItemOptionUiState>,
    onAction: (NutritionAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onAction(
                NutritionAction
                    .DismissLogVersions
            )
        },
        title = {
            Text("Choose version")
        },
        text = {
            LazyColumn(
                modifier =
                    Modifier.heightIn(
                        max = 300.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    ),
            ) {
                items(
                    items = versions,
                    key = {
                        it.id
                    },
                ) { version ->
                    OutlinedButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NutritionLogEditorTags
                                        .version(
                                            version.id
                                        )
                                ),
                        onClick = {
                            onAction(
                                NutritionAction
                                    .SelectLogItem(
                                        version.id
                                    )
                            )
                        },
                    ) {
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
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    onAction(
                        NutritionAction
                            .DismissLogVersions
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LogDeleteConfirmation(
    editor: NutritionLogEditorUiState,
    onAction: (NutritionAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!editor.isDeleting) {
                onAction(
                    NutritionAction
                        .DismissDeleteLog
                )
            }
        },
        title = {
            Text("Delete food log?")
        },
        text = {
            Text(
                "This permanently removes this consumption entry."
            )
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .DELETE_CONFIRM
                    ),
                enabled =
                    !editor.isDeleting,
                onClick = {
                    onAction(
                        NutritionAction.DeleteLog
                    )
                },
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        NutritionLogEditorTags
                            .DELETE_CANCEL
                    ),
                enabled =
                    !editor.isDeleting,
                onClick = {
                    onAction(
                        NutritionAction
                            .DismissDeleteLog
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}