package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round

object NutritionManageTags {
    const val SCREEN =
        "nutrition_manage_screen"
    const val BACK =
        "nutrition_manage_back"
    const val SEARCH =
        "nutrition_manage_search"
    const val FILTER =
        "nutrition_manage_filter"
    const val SORT =
        "nutrition_manage_sort"
    const val FILTER_PROTEIN =
        "nutrition_manage_filter_protein"
    const val FILTER_RATIO =
        "nutrition_manage_filter_ratio"
    const val FILTER_APPLY =
        "nutrition_manage_filter_apply"
    const val FILTER_RESET =
        "nutrition_manage_filter_reset"

    const val LIST =
        "nutrition_manage_list"

    const val ADD_ITEM =
        "nutrition_manage_add_item"

    fun version(itemId: Long) =
        "nutrition_manage_version_$itemId"

    fun group(nameKey: String) =
        "nutrition_manage_group_$nameKey"

    fun sort(sort: NutritionItemSort) =
        "nutrition_manage_sort_${sort.name}"

    fun archiveFilter(
        filter: NutritionArchiveFilter,
    ) =
        "nutrition_manage_archive_${filter.name}"
}

@Composable
fun NutritionManageScreen(
    state: NutritionManageUiState,
    onAction: (NutritionAction) -> Unit,
) {
    var showFilters by remember {
        mutableStateOf(false)
    }

    var sortExpanded by remember {
        mutableStateOf(false)
    }

    var selectedVersionGroup by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    NutritionManageTags.SCREEN
                )
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            TextButton(
                modifier =
                    Modifier.testTag(
                        NutritionManageTags.BACK
                    ),
                onClick = {
                    onAction(
                        NutritionAction
                            .DismissDestination
                    )
                },
            ) {
                Text("Back")
            }

            Text(
                "Manage foods",
                style =
                    MaterialTheme
                        .typography.titleLarge,
            )
        }

        OutlinedTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        NutritionManageTags.SEARCH
                    ),
            value = state.itemSearch,
            onValueChange = {
                onAction(
                    NutritionAction
                        .ChangeManageSearch(it)
                )
            },
            label = {
                Text("Search foods")
            },
            singleLine = true,
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier =
                    Modifier.testTag(
                        NutritionManageTags.FILTER
                    ),
                onClick = {
                    showFilters = true
                },
            ) {
                Text(
                    if (
                        state.minimumProteinText
                            .isNotBlank() ||
                        state.minimumProteinRatioText
                            .isNotBlank() ||
                        state.archiveFilter !=
                        NutritionArchiveFilter
                            .ACTIVE
                    ) {
                        "Filter active"
                    } else {
                        "Filter"
                    }
                )
            }

            Column {
                OutlinedButton(
                    modifier =
                        Modifier.testTag(
                            NutritionManageTags.SORT
                        ),
                    onClick = {
                        sortExpanded = true
                    },
                ) {
                    Text(
                        state.itemSort.displayName()
                    )
                }

                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = {
                        sortExpanded = false
                    },
                ) {
                    NutritionItemSort.entries
                        .forEach { sort ->
                            DropdownMenuItem(
                                modifier =
                                    Modifier.testTag(
                                        NutritionManageTags
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
                                            .ChangeManageSort(
                                                sort
                                            )
                                    )
                                },
                            )
                        }
                }
            }
        }

        if (!state.filtersValid) {
            Text(
                "Enter valid non-negative filter values.",
                color =
                    MaterialTheme
                        .colorScheme.error,
            )
        }

        if (state.visibleFoodGroups.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                verticalArrangement =
                    Arrangement.Center,
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        !state.filtersValid ->
                            "Correct the filters to view foods."

                        state.itemSearch.isNotBlank() ->
                            "No matching foods."

                        state.archiveFilter ==
                                NutritionArchiveFilter
                                    .ARCHIVED ->
                            "No archived foods."

                        else ->
                            "No active foods."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(
                            NutritionManageTags.LIST
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items =
                        state.visibleFoodGroups,
                    key = {
                        it.nameKey
                    },
                ) { group ->
                    NutritionManageGroupRow(
                        group = group,
                        onClick = {
                            val versions =
                                state.itemOptions
                                    .filter {
                                        it.nameKey ==
                                                group.nameKey
                                    }
                                    .filter { option ->
                                        when (
                                            state.archiveFilter
                                        ) {
                                            NutritionArchiveFilter
                                                .ACTIVE ->
                                                !option.isArchived

                                            NutritionArchiveFilter
                                                .ARCHIVED ->
                                                option.isArchived

                                            NutritionArchiveFilter.ALL ->
                                                true
                                        }
                                    }

                            if (versions.size == 1) {
                                onAction(
                                    NutritionAction.InspectItem(
                                        versions.single().id
                                    )
                                )
                            } else if (versions.isNotEmpty()) {
                                selectedVersionGroup =
                                    group.nameKey
                            }
                        },
                    )
                }
            }
        }
        Button(
            modifier =
                Modifier
                    .align(Alignment.End)
                    .testTag(
                        NutritionManageTags
                            .ADD_ITEM
                    ),
            onClick = {
                onAction(
                    NutritionAction.OpenAddItem
                )
            },
        ) {
            Text("Add food")
        }
    }

    if (showFilters) {
        NutritionManageFilterDialog(
            state = state,
            onDismiss = {
                showFilters = false
            },
            onAction = onAction,
        )
    }

    selectedVersionGroup
        ?.let { nameKey ->
            val versions =
                state.itemOptions
                    .filter {
                        it.nameKey == nameKey
                    }
                    .filter { option ->
                        when (
                            state.archiveFilter
                        ) {
                            NutritionArchiveFilter
                                .ACTIVE ->
                                !option.isArchived

                            NutritionArchiveFilter
                                .ARCHIVED ->
                                option.isArchived

                            NutritionArchiveFilter.ALL ->
                                true
                        }
                    }
                    .sortedBy {
                        it.version
                    }

            AlertDialog(
                onDismissRequest = {
                    selectedVersionGroup =
                        null
                },
                title = {
                    Text("Choose version")
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
                                            NutritionManageTags
                                                .version(
                                                    version.id
                                                )
                                        ),
                                onClick = {
                                    selectedVersionGroup =
                                        null

                                    onAction(
                                        NutritionAction
                                            .InspectItem(
                                                version.id
                                            )
                                    )
                                },
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                ) {
                                    Text(
                                        version.displayName
                                    )

                                    Text(
                                        "${amountText(version.caloriesPer100g)} kcal" +
                                                " · " +
                                                "${amountText(version.proteinPer100g)} g protein" +
                                                " per 100 g"
                                    )

                                    if (
                                        version.isArchived
                                    ) {
                                        Text("Archived")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedVersionGroup =
                                null
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
}

@Composable
private fun NutritionManageGroupRow(
    group: NutritionFoodGroupUiState,
    onClick: () -> Unit,
) {
    val archivedCount =
        group.versions.count {
            it.isArchived
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(
                    NutritionManageTags.group(
                        group.nameKey
                    )
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp),
        ) {
            val representative =
                group.representativeVersion

            Text(
                group.name,
                style =
                    MaterialTheme.typography
                        .titleMedium,
            )

            Text(
                representative.versionLabel
                    ?: "v${representative.version}"
            )

            Text(
                "${amountText(representative.caloriesPer100g)} kcal" +
                        " · " +
                        "${amountText(representative.proteinPer100g)} g protein" +
                        " per 100 g"
            )

            if (group.versions.size > 1) {
                Text(
                    "${group.versions.size} versions",
                    style =
                        MaterialTheme.typography
                            .labelMedium,
                )
            }

            when {
                archivedCount ==
                        group.versions.size ->
                    Text(
                        if (
                            group.versions.size == 1
                        ) {
                            "Archived"
                        } else {
                            "All versions archived"
                        }
                    )

                archivedCount > 0 ->
                    Text(
                        "$archivedCount archived"
                    )
            }
        }
    }
}

@Composable
private fun NutritionManageFilterDialog(
    state: NutritionManageUiState,
    onDismiss: () -> Unit,
    onAction: (NutritionAction) -> Unit,
) {
    var proteinText by remember(
        state.minimumProteinText
    ) {
        mutableStateOf(
            state.minimumProteinText
        )
    }

    var ratioText by remember(
        state.minimumProteinRatioText
    ) {
        mutableStateOf(
            state.minimumProteinRatioText
        )
    }

    var archiveFilter by remember(
        state.archiveFilter
    ) {
        mutableStateOf(
            state.archiveFilter
        )
    }

    val valid =
        validFilterText(proteinText) &&
                validFilterText(ratioText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Filter foods")
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
                                NutritionManageTags
                                    .FILTER_PROTEIN
                            ),
                    value = proteinText,
                    onValueChange = {
                        proteinText = it
                    },
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
                    singleLine = true,
                    isError =
                        !validFilterText(
                            proteinText
                        ),
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NutritionManageTags
                                    .FILTER_RATIO
                            ),
                    value = ratioText,
                    onValueChange = {
                        ratioText = it
                    },
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
                    singleLine = true,
                    isError =
                        !validFilterText(
                            ratioText
                        ),
                )

                Text(
                    "Archive status",
                    style =
                        MaterialTheme
                            .typography.titleSmall,
                )

                NutritionArchiveFilter.entries
                    .forEach { filter ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                modifier =
                                    Modifier.testTag(
                                        NutritionManageTags
                                            .archiveFilter(
                                                filter
                                            )
                                    ),
                                selected =
                                    archiveFilter ==
                                            filter,
                                onClick = {
                                    archiveFilter =
                                        filter
                                },
                            )

                            Text(
                                filter.displayName()
                            )
                        }
                    }
            }
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        NutritionManageTags
                            .FILTER_APPLY
                    ),
                enabled = valid,
                onClick = {
                    onAction(
                        NutritionAction
                            .ChangeManageMinimumProtein(
                                proteinText
                            )
                    )

                    onAction(
                        NutritionAction
                            .ChangeManageMinimumProteinRatio(
                                ratioText
                            )
                    )

                    onAction(
                        NutritionAction
                            .ChangeManageArchiveFilter(
                                archiveFilter
                            )
                    )

                    onDismiss()
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    modifier =
                        Modifier.testTag(
                            NutritionManageTags
                                .FILTER_RESET
                        ),
                    onClick = {
                        onAction(
                            NutritionAction
                                .ResetManageFilters
                        )

                        onDismiss()
                    },
                ) {
                    Text("Reset")
                }

                TextButton(
                    onClick = onDismiss,
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

private fun NutritionItemSort.displayName():
        String =
    when (this) {
        NutritionItemSort.RECENT ->
            "Recent"

        NutritionItemSort.NEWEST_ADDED ->
            "Newest"

        NutritionItemSort.NAME ->
            "Name"

        NutritionItemSort.CALORIES ->
            "Calories"

        NutritionItemSort.PROTEIN ->
            "Protein"

        NutritionItemSort.PROTEIN_RATIO ->
            "Protein ratio"
    }

private fun NutritionArchiveFilter.displayName():
        String =
    when (this) {
        NutritionArchiveFilter.ACTIVE ->
            "Active"

        NutritionArchiveFilter.ARCHIVED ->
            "Archived"

        NutritionArchiveFilter.ALL ->
            "All"
    }

private fun validFilterText(
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

private fun amountText(
    value: Double,
): String {
    val rounded =
        round(value * 10.0) / 10.0

    return if (
        abs(rounded - rounded.toLong()) <
        0.000_001
    ) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}