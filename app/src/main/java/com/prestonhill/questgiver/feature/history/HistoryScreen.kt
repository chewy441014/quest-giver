@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)
package com.prestonhill.questgiver.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.remember
import com.prestonhill.questgiver.feature.nutrition.nutritionAmountText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object HistoryTags {
    const val TASK_DASHBOARD =
        "history_task_dashboard"
    const val ALL_TASKS =
        "history_all_tasks"
    const val CATEGORY_GRAPH =
        "history_category_graph"
    const val PINNED_GRAPHS =
        "history_pinned_graphs"

    const val TASK_HISTORY_PLACEHOLDER =
        "history_task_history_placeholder"

    const val ARCHIVED_TOGGLE =
        "history_archived_toggle"

    const val CONFIRM_DELETE =
        "history_confirm_delete"

    const val CANCEL_DELETE =
        "history_cancel_delete"

    const val NUTRITION_DASHBOARD =
        "history_nutrition_dashboard"

    const val NUTRITION_CALORIE_STATS =
        "history_nutrition_calorie_stats"

    const val NUTRITION_PROTEIN_STATS =
        "history_nutrition_protein_stats"

    const val NUTRITION_RANGE_CONFIRM =
        "history_nutrition_range_confirm"

    const val NUTRITION_RANGE_CANCEL =
        "history_nutrition_range_cancel"

    const val NUTRITION_RANGE_LIST =
        "history_nutrition_range_list"

    fun nutritionRange(
        preset: NutritionHistoryRangePreset,
    ) =
        "history_nutrition_range_" +
                preset.name

    fun deleteTask(taskId: Long) =
        "history_delete_task_$taskId"

    fun archiveTask(taskId: Long) =
        "history_archive_task_$taskId"

    fun restoreTask(taskId: Long) =
        "history_restore_task_$taskId"

    fun taskCompletion(taskId: Long) =
        "history_task_completion_$taskId"
    fun tab(section: HistorySection) =
        "history_tab_${section.name}"

    fun task(taskId: Long) =
        "history_task_$taskId"
}

@Composable
fun HistoryScreen(
    state: HistoryScreenUiState,
    onAction: (HistoryAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TabRow(
            selectedTabIndex =
                state.section.ordinal,
        ) {
            HistorySection.entries.forEach {
                    section ->
                Tab(
                    modifier =
                        Modifier.testTag(
                            HistoryTags.tab(section)
                        ),
                    selected =
                        state.section == section,
                    onClick = {
                        onAction(
                            HistoryAction
                                .SelectSection(section)
                        )
                    },
                    text = {
                        Text(section.label)
                    },
                )
            }
        }

        when (state.section) {
            HistorySection.HABITS ->
                EmptyHistory(
                    title = "Habit history",
                    message =
                        "No habit history to show yet.",
                )

            HistorySection.TASKS ->
                TaskHistory(
                    state = state.tasks,
                    onAction = onAction,
                )

            HistorySection.NUTRITION ->
                NutritionHistoryDashboard(
                    state = state.nutrition,
                    onAction = onAction,
                )
        }
    }

    state.tasks.inspectedTaskId
        ?.let(state.tasks::findTask)
        ?.let { task ->
            HistoryTaskDialog(
                task = task,
                onAction = onAction,
            )
        }

    state.tasks.deleteConfirmation
        ?.let { confirmation ->
            HistoryDeleteTaskDialog(
                confirmation = confirmation,
                onConfirm = {
                    onAction(
                        HistoryAction.ConfirmDelete
                    )
                },
                onDismiss = {
                    onAction(
                        HistoryAction.DismissDelete
                    )
                },
            )
        }

    state.tasks.operationError?.let {
            message ->
        AlertDialog(
            onDismissRequest = {
                onAction(
                    HistoryAction.DismissError
                )
            },
            title = {
                Text("Something went wrong")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(
                            HistoryAction
                                .DismissError
                        )
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun NutritionHistoryDashboard(
    state: NutritionHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val selectedRange =
        state.selectedRange

    if (selectedRange == null) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        return
    }

    val dateFormatter =
        remember {
            DateTimeFormatter
                .ofLocalizedDate(
                    FormatStyle.MEDIUM
                )
                .withLocale(
                    Locale.getDefault()
                )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    HistoryTags
                        .NUTRITION_DASHBOARD
                ),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Nutrition history",
                style =
                    MaterialTheme
                        .typography.headlineSmall,
            )
        }

        item {
            LazyRow(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .NUTRITION_RANGE_LIST
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items =
                        NutritionHistoryRangePreset
                            .entries,
                    key = {
                        it.name
                    },
                ) { preset ->
                    FilterChip(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .nutritionRange(
                                        preset
                                    )
                            ),
                        selected =
                            state.rangePreset ==
                                    preset,
                        onClick = {
                            if (
                                preset ==
                                NutritionHistoryRangePreset
                                    .CUSTOM
                            ) {
                                onAction(
                                    HistoryAction
                                        .OpenNutritionCustomRange
                                )
                            } else {
                                onAction(
                                    HistoryAction
                                        .SelectNutritionRange(
                                            preset
                                        )
                                )
                            }
                        },
                        label = {
                            Text(preset.label)
                        },
                    )
                }
            }
        }

        item {
            Text(
                selectedRange.startDate
                    .format(dateFormatter) +
                        " – " +
                        selectedRange.endDate
                            .format(dateFormatter),
                style =
                    MaterialTheme
                        .typography.bodyMedium,
            )
        }

        if (
            state.calorieStatistics
                .loggedDays == 0
        ) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            "No nutrition was logged " +
                                    "during this range.",
                        modifier =
                            Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                NutritionMetricCard(
                    title = "Calories",
                    statistics =
                        state
                            .calorieStatistics,
                    unit = "kcal",
                    tag =
                        HistoryTags
                            .NUTRITION_CALORIE_STATS,
                )
            }

            item {
                NutritionMetricCard(
                    title = "Protein",
                    statistics =
                        state
                            .proteinStatistics,
                    unit = "g",
                    tag =
                        HistoryTags
                            .NUTRITION_PROTEIN_STATS,
                )
            }
        }
    }

    if (state.showCustomRangePicker) {
        NutritionCustomRangeDialog(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun NutritionMetricCard(
    title: String,
    statistics:
    NutritionHistoryMetricUiState,
    unit: String,
    tag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                "Logged days: " +
                        statistics.loggedDays
            )

            MetricRow(
                label =
                    "Daily average",
                value =
                    metricText(
                        statistics.average,
                        unit,
                    ),
            )

            MetricRow(
                label =
                    "Minimum (nonzero)",
                value =
                    metricText(
                        statistics
                            .minimumNonZero,
                        unit,
                    ),
            )

            MetricRow(
                label = "Maximum",
                value =
                    metricText(
                        statistics.maximum,
                        unit,
                    ),
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun NutritionCustomRangeDialog(
    state: NutritionHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val currentDate =
        requireNotNull(
            state.currentDate
        )

    val customRange =
        requireNotNull(
            state.customRange
        )

    val selectableDates =
        remember(currentDate) {
            object : SelectableDates {
                override fun isSelectableDate(
                    utcTimeMillis: Long,
                ): Boolean =
                    utcTimeMillis
                        .utcDate() <=
                            currentDate

                override fun isSelectableYear(
                    year: Int,
                ): Boolean =
                    year <= currentDate.year
            }
        }

    val pickerState =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis =
                customRange.startDate
                    .utcMillis(),
            initialSelectedEndDateMillis =
                customRange.endDate
                    .utcMillis(),
            selectableDates =
                selectableDates,
        )

    DatePickerDialog(
        onDismissRequest = {
            onAction(
                HistoryAction
                    .DismissNutritionCustomRange
            )
        },
        confirmButton = {
            val start =
                pickerState
                    .selectedStartDateMillis

            val end =
                pickerState
                    .selectedEndDateMillis

            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .NUTRITION_RANGE_CONFIRM
                    ),
                enabled =
                    start != null &&
                            end != null,
                onClick = {
                    if (
                        start != null &&
                        end != null
                    ) {
                        onAction(
                            HistoryAction
                                .SetNutritionCustomRange(
                                    NutritionHistoryDateRange(
                                        startDate =
                                            start.utcDate(),
                                        endDate =
                                            end.utcDate(),
                                    )
                                )
                        )
                    }
                },
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .NUTRITION_RANGE_CANCEL
                    ),
                onClick = {
                    onAction(
                        HistoryAction
                            .DismissNutritionCustomRange
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    ) {
        DateRangePicker(
            state = pickerState,
        )
    }
}

@Composable
private fun HistoryDeleteTaskDialog(
    confirmation: HistoryDeleteUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!confirmation.isDeleting) {
                onDismiss()
            }
        },
        title = {
            Text("Delete task?")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Permanently delete " +
                            "\"${confirmation.taskName}\" " +
                            "and all of its history? " +
                            "This cannot be undone."
                )

                confirmation.errorMessage
                    ?.let { message ->
                        Text(
                            text = message,
                            color =
                                MaterialTheme
                                    .colorScheme.error,
                        )
                    }
            }
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        HistoryTags.CONFIRM_DELETE
                    ),
                enabled =
                    !confirmation.isDeleting,
                onClick = onConfirm,
            ) {
                Text(
                    if (
                        confirmation.isDeleting
                    ) {
                        "Deleting..."
                    } else {
                        "Delete"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags.CANCEL_DELETE
                    ),
                enabled =
                    !confirmation.isDeleting,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun HistoryTaskDialog(
    task: HistoryTaskUiState,
    onAction: (HistoryAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onAction(
                HistoryAction.DismissTask
            )
        },
        title = {
            Text(task.name)
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    task.category
                        ?: "Uncategorized"
                )

                Text(task.schedule)

                if (!task.isArchived) {
                    task.completionEpochDay
                        ?.let { completionDay ->
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    modifier =
                                        Modifier
                                            .testTag(
                                                HistoryTags
                                                    .taskCompletion(
                                                        task.id
                                                    )
                                            )
                                            .semantics {
                                                contentDescription =
                                                    "Task completed"
                                            },
                                    checked =
                                        task.isCompleted,
                                    enabled =
                                        task.canChangeCompletion &&
                                                !task.isChanging,
                                    onCheckedChange = {
                                            completed ->
                                        onAction(
                                            HistoryAction
                                                .SetTaskCompletion(
                                                    taskId = task.id,
                                                    scheduledEpochDay =
                                                        completionDay,
                                                    completed =
                                                        completed,
                                                )
                                        )
                                    },
                                )

                                Text("Task completed")
                            }
                        }
                }

                if (task.isArchived) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag(
                                        HistoryTags.restoreTask(
                                            task.id
                                        )
                                    ),
                            enabled = !task.isChanging,
                            onClick = {
                                onAction(
                                    HistoryAction.RestoreTask(
                                        task.id
                                    )
                                )
                            },
                        ) {
                            Text(
                                if (task.isChanging) {
                                    "Restoring..."
                                } else {
                                    "Restore task"
                                }
                            )
                        }

                        TextButton(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag(
                                        HistoryTags.deleteTask(
                                            task.id
                                        )
                                    ),
                            enabled = !task.isChanging,
                            onClick = {
                                onAction(
                                    HistoryAction
                                        .RequestDeleteTask(
                                            task.id
                                        )
                                )
                            },
                        ) {
                            Text("Delete")
                        }
                    }
                } else {
                    OutlinedButton(
                        modifier =
                            Modifier.testTag(
                                HistoryTags.archiveTask(
                                    task.id
                                )
                            ),
                        enabled = !task.isChanging,
                        onClick = {
                            onAction(
                                HistoryAction.ArchiveTask(
                                    task.id
                                )
                            )
                        },
                    ) {
                        Text(
                            if (task.isChanging) {
                                "Archiving..."
                            } else {
                                "Archive task"
                            }
                        )
                    }
                }

                OutlinedButton(
                    modifier =
                        Modifier.testTag(
                            HistoryTags
                                .TASK_HISTORY_PLACEHOLDER
                        ),
                    enabled = false,
                    onClick = {},
                ) {
                    Text("View task history")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        HistoryAction.DismissTask
                    )
                },
            ) {
                Text("Close")
            }
        },
    )
}

private fun TaskHistoryUiState.findTask(
    taskId: Long,
): HistoryTaskUiState? =
    allTasks.firstOrNull {
        it.id == taskId
    }

@Composable
private fun TaskHistory(
    state: TaskHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    when (state.page) {
        TaskHistoryPage.DASHBOARD ->
            TaskDashboard(
                state = state,
                onAction = onAction,
            )

        TaskHistoryPage.ALL_TASKS ->
            AllTasksPage(
                state = state,
                onAction = onAction,
                onBack = {
                    onAction(
                        HistoryAction.BackToDashboard
                    )
                },
            )
    }
}

@Composable
private fun TaskDashboard(
    state: TaskHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(
                HistoryTags.TASK_DASHBOARD
            ),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier =
                        Modifier.weight(1f),
                    onClick = {
                        onAction(
                            HistoryAction.OpenTaskPage(
                                TaskHistoryPage
                                    .ALL_TASKS
                            )
                        )
                    },
                ) {
                    Text("View all tasks")
                }
            }
        }

        item {
            GraphPlaceholder(
                graph = state.categoryGraph,
                modifier =
                    Modifier.testTag(
                        HistoryTags.CATEGORY_GRAPH
                    ),
            )
        }

        item {
            Text(
                text = "Pinned graphs",
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )
        }

        items(
            count = state.pinnedGraphs.size,
            key = { index ->
                state.pinnedGraphs[index].id
            },
        ) { index ->
            GraphPlaceholder(
                graph =
                    state.pinnedGraphs[index],
                modifier =
                    Modifier.testTag(
                        HistoryTags.PINNED_GRAPHS
                    ),
            )
        }
    }
}

@Composable
private fun GraphPlaceholder(
    graph: HistoryGraphUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = graph.title,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(graph.message)
            }
        }
    }
}

@Composable
private fun AllTasksPage(
    state: TaskHistoryUiState,
    onBack: () -> Unit,
    onAction: (HistoryAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HistoryTags.ALL_TASKS),
    ) {
        HistoryHeader(
            title =
                if (state.showArchivedTasks) {
                    "Archived tasks"
                } else {
                    "All tasks"
                },
            onBack = onBack,
            trailingContent = {
                Switch(
                    modifier =
                        Modifier
                            .testTag(
                                HistoryTags
                                    .ARCHIVED_TOGGLE
                            )
                            .semantics {
                                contentDescription =
                                    "Show archived tasks"
                            },
                    checked =
                        state.showArchivedTasks,
                    onCheckedChange = { show ->
                        onAction(
                            HistoryAction
                                .ShowArchivedTasks(
                                    show
                                )
                        )
                    },
                )
            },
        )

        val tasks = state.visibleTasks

        if (tasks.isEmpty()) {
            EmptyList(
                if (state.showArchivedTasks) {
                    "No archived tasks."
                } else {
                    "No active tasks."
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = tasks,
                    key = HistoryTaskUiState::id,
                ) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                HistoryTags.task(task.id)
                            ),
                        onClick = {
                            onAction(
                                HistoryAction.InspectTask(
                                    task.id
                                )
                            )
                        },
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    4.dp
                                ),
                        ) {
                            Text(
                                text = task.name,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                            )

                            Text(
                                task.category
                                    ?: "Uncategorized"
                            )

                            Text(task.schedule)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    title: String,
    onBack: () -> Unit,
    trailingContent:
    (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = title,
            style =
                MaterialTheme
                    .typography.headlineSmall,
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        trailingContent?.invoke()
    }
}

@Composable
private fun EmptyList(
    message: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(message)
    }
}

@Composable
private fun EmptyHistory(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme
                    .typography.headlineSmall,
        )

        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center,
        ) {
            Text(message)
        }
    }
}

private fun LocalDate.utcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun Long.utcDate(): LocalDate =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

private fun metricText(
    value: Double?,
    unit: String,
): String =
    value?.let {
        "${nutritionAmountText(it)} $unit"
    } ?: "—"