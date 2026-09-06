@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)
package com.prestonhill.questgiver.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

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
                HabitHistoryDashboard(
                    state = state.habits,
                    onAction = onAction,
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
