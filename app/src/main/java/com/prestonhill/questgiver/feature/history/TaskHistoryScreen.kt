@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)
package com.prestonhill.questgiver.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryDeleteTaskDialog(
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
internal fun HistoryTaskDialog(
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

internal fun TaskHistoryUiState.findTask(
    taskId: Long,
): HistoryTaskUiState? =
    allTasks.firstOrNull {
        it.id == taskId
    }

@Composable
internal fun TaskHistory(
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
            HistoryStampCalendarCard(
                state = state.stampCalendar,
                tagPrefix =
                    HistoryTags
                        .TASK_STAMP_PREFIX,
                title =
                    "Task completion calendar",
                emptyMessage =
                    "Complete a recurring or " +
                            "categorized task to " +
                            "create calendar stamps.",
                onPreviousMonth = {
                    onAction(
                        HistoryAction
                            .PreviousTaskCalendarMonth
                    )
                },
                onNextMonth = {
                    onAction(
                        HistoryAction
                            .NextTaskCalendarMonth
                    )
                },
                onToggleFilter = { key ->
                    onAction(
                        HistoryAction
                            .ToggleTaskStampFilter(
                                key
                            )
                    )
                },
                onSelectAll = {
                    onAction(
                        HistoryAction
                            .SelectAllTaskStamps
                    )
                },
                onOpenDay = { date ->
                    onAction(
                        HistoryAction
                            .OpenTaskCalendarDay(
                                date
                            )
                    )
                },
                onSetGroupSelected = {
                        group,
                        selected,
                    ->
                    onAction(
                        HistoryAction
                            .SetTaskStampGroupSelected(
                                groupLabel = group,
                                selected = selected,
                            )
                    )
                },
            )
        }
    }

    HistoryStampCalendarDayDialog(
        state = state.stampCalendar,
        tagPrefix =
            HistoryTags.TASK_STAMP_PREFIX,
        onDismiss = {
            onAction(
                HistoryAction
                    .DismissTaskCalendarDay
            )
        },
    )

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