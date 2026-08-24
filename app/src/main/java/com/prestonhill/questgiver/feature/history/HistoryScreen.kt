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

    fun tab(section: HistorySection) =
        "history_tab_${section.name}"
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
            EmptyTaskPage(
                title = "All tasks",
                message =
                    "No tasks to show yet.",
                tag = HistoryTags.ALL_TASKS,
                onBack = {
                    onAction(
                        HistoryAction
                            .BackToDashboard
                    )
                },
            )

        TaskHistoryPage.ALL_LOGS ->
            EmptyTaskPage(
                title = "All task logs",
                message =
                    "No task logs to show yet.",
                tag = HistoryTags.ALL_LOGS,
                onBack = {
                    onAction(
                        HistoryAction
                            .BackToDashboard
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
private fun EmptyTaskPage(
    title: String,
    message: String,
    tag: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag)
            .padding(16.dp),
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