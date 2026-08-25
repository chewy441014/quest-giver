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
                EmptyHistory(
                    title = "Nutrition history",
                    message =
                        "No nutrition history to show yet.",
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
                                                    taskId =
                                                        task.id,
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

                if (task.isArchived) {
                    OutlinedButton(
                        modifier =
                            Modifier.testTag(
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