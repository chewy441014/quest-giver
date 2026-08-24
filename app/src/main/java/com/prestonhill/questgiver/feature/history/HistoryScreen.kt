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
import androidx.compose.runtime.remember
import java.time.format.DateTimeFormatter
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

object HistoryTags {
    const val TASK_DASHBOARD =
        "history_task_dashboard"

    const val ALL_TASKS =
        "history_all_tasks"

    const val ALL_LOGS =
        "history_all_logs"

    const val CATEGORY_GRAPH =
        "history_category_graph"

    const val PINNED_GRAPHS =
        "history_pinned_graphs"

    const val DELETE_LOG =
        "history_delete_log"

    const val CONFIRM_DELETE_LOG =
        "history_confirm_delete_log"

    fun tab(section: HistorySection) =
        "history_tab_${section.name}"

    fun task(taskId: Long) =
        "history_task_$taskId"

    fun log(logId: Long) =
        "history_log_$logId"
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
    state.tasks.inspectedLogId
        ?.let(state.tasks::findLog)
        ?.let { log ->
            LogDetailsDialog(
                log = log,
                onAction = onAction,
            )
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
    state.tasks.confirmation?.let {
            confirmation ->
        DeleteLogDialog(
            confirmation = confirmation,
            onAction = onAction,
        )
    }
}

@Composable
private fun DeleteLogDialog(
    confirmation: HistoryDeleteUiState,
    onAction: (HistoryAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!confirmation.isDeleting) {
                onAction(
                    HistoryAction
                        .DismissDeleteLog
                )
            }
        },
        title = {
            Text("Delete history?")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Text(confirmation.taskName)

                Text(
                    "This permanently deletes this history entry."
                )

                confirmation.errorMessage
                    ?.let { message ->
                        Text(message)
                    }
            }
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .CONFIRM_DELETE_LOG
                    ),
                enabled =
                    !confirmation.isDeleting,
                onClick = {
                    onAction(
                        HistoryAction
                            .ConfirmDeleteLog
                    )
                },
            ) {
                Text(
                    if (
                        confirmation.isDeleting
                    ) {
                        "Deleting..."
                    } else {
                        "Delete history"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled =
                    !confirmation.isDeleting,
                onClick = {
                    onAction(
                        HistoryAction
                            .DismissDeleteLog
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}
@Composable
private fun LogDetailsDialog(
    log: HistoryTaskLogUiState,
    onAction: (HistoryAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onAction(
                HistoryAction.DismissLog
            )
        },
        title = {
            Text(log.taskName)
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    log.category
                        ?: "Uncategorized"
                )

                Text(log.date.toString())

                when {
                    log.isCorrected ->
                        Text("Corrected")

                    log.taskId == null ->
                        Text("Associated task deleted")
                }
                if (log.canDelete) {
                    TextButton(
                        modifier =
                            Modifier.testTag(
                                HistoryTags.DELETE_LOG
                            ),
                        onClick = {
                            onAction(
                                HistoryAction.RequestDeleteLog(
                                    log.id
                                )
                            )
                        },
                    ) {
                        Text("Delete history")
                    }
                }
            }
        },
        confirmButton = {
            if (log.canOpenTask) {
                TextButton(
                    onClick = {
                        onAction(
                            HistoryAction.InspectTask(
                                requireNotNull(
                                    log.taskId
                                )
                            )
                        )
                    },
                ) {
                    Text("View task")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onAction(
                        HistoryAction.DismissLog
                    )
                },
            ) {
                Text("Close")
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

private fun TaskHistoryUiState.findLog(
    logId: Long,
): HistoryTaskLogUiState? =
    logDays.asSequence()
        .flatMap { it.logs.asSequence() }
        .firstOrNull {
            it.id == logId
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
                tasks = state.allTasks,
                onAction = onAction,
                onBack = {
                    onAction(
                        HistoryAction.BackToDashboard
                    )
                },
            )

        TaskHistoryPage.ALL_LOGS ->
            AllLogsPage(
                days = state.logDays,
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

                OutlinedButton(
                    modifier =
                        Modifier.weight(1f),
                    onClick = {
                        onAction(
                            HistoryAction.OpenTaskPage(
                                TaskHistoryPage
                                    .ALL_LOGS
                            )
                        )
                    },
                ) {
                    Text("View all logs")
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
    tasks: List<HistoryTaskUiState>,
    onBack: () -> Unit,
    onAction: (HistoryAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HistoryTags.ALL_TASKS),
    ) {
        HistoryHeader(
            title = "All tasks",
            onBack = onBack,
        )

        if (tasks.isEmpty()) {
            EmptyList("No tasks to show yet.")
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
private fun AllLogsPage(
    days: List<HistoryTaskDayUiState>,
    onBack: () -> Unit,
    onAction: (HistoryAction) -> Unit,
) {
    val dateFormatter =
        remember {
            DateTimeFormatter.ofPattern(
                "EEE, MMM d"
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HistoryTags.ALL_LOGS),
    ) {
        HistoryHeader(
            title = "All task logs",
            onBack = onBack,
        )

        if (days.isEmpty()) {
            EmptyList(
                "No task logs to show yet."
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
                days.forEach { day ->
                    item(
                        key =
                            "day_${day.date.toEpochDay()}"
                    ) {
                        Text(
                            text =
                                day.date.format(
                                    dateFormatter
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            modifier =
                                Modifier.padding(
                                    top = 8.dp
                                ),
                        )
                    }

                    items(
                        items = day.logs,
                        key =
                            HistoryTaskLogUiState::id,
                    ) { log ->
                        TaskLogCard(
                            log = log,
                            onClick = {
                                onAction(
                                    HistoryAction.InspectLog(
                                        log.id
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLogCard(
    log: HistoryTaskLogUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                HistoryTags.log(log.id)
            ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = log.taskName,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                log.category
                    ?: "Uncategorized"
            )

            when {
                log.isCorrected ->
                    Text("Corrected")

                log.taskId == null ->
                    Text("Task deleted")
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    title: String,
    onBack: () -> Unit,
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