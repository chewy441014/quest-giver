package com.prestonhill.questgiver.feature.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun HabitScreen(
    uiState: HabitScreenUiState,
    onAction: (HabitAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val inspectedHabit = uiState.categories
        .asSequence()
        .flatMap { it.habits.asSequence() }
        .firstOrNull { it.id == uiState.inspectedHabitId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            uiState.categories.forEach { categoryState ->
                item(key = categoryState.category) {
                    CategoryHeader(
                        categoryState = categoryState,
                        onClick = {
                            onAction(
                                HabitAction.ToggleCategory(
                                    categoryState.category
                                )
                            )
                        },
                        onToggleHidden = {
                            onAction(
                                HabitAction.ToggleHiddenHabits(
                                    categoryState.category
                                )
                            )
                        }
                    )
                }

                if (categoryState.isExpanded) {
                    items(
                        items = categoryState.habits,
                        key = { habit -> habit.id }
                    ) { habit ->
                        HabitRow(
                            habit = habit,
                            onAction = onAction
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    onAction(HabitAction.AddHabit)
                },
            ) {
                Text("Add habit")
            }

            if (uiState.archivedHabits.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        onAction(HabitAction.ShowArchivedHabits)
                    },
                ) {
                    Text("Archived")
                }
            }
        }
    }

    inspectedHabit?.let { habit ->
        HabitDetailsDialog(
            habit = habit,
            onAction = onAction,
            onArchive = {
                onAction(HabitAction.ArchiveHabit(habit.id))
            },

            onDeleteHabit = {
                onAction(HabitAction.RequestDeleteHabit(habit.id))
            },
        )
    }

    uiState.editor?.let { editor ->
        HabitEditorDialog(
            editor = editor,
            onChange = { updatedEditor ->
                onAction(
                    HabitAction.UpdateHabitEditor(
                        updatedEditor
                    )
                )
            },
            onSave = {
                onAction(HabitAction.SaveHabit)
            },
            onDismiss = {
                onAction(HabitAction.DismissHabitEditor)
            }
        )
    }

    if (uiState.showArchivedHabits) {
        ArchivedHabitsDialog(
            habits = uiState.archivedHabits,
            onRestore = {
                onAction(HabitAction.RestoreHabit(it))
            },
            onDelete = {
                onAction(HabitAction.RequestDeleteHabit(it))
            },
            onDismiss = {
                onAction(HabitAction.DismissArchivedHabits)
            },
        )
    }

    uiState.confirmation?.let { confirmation ->
        DeleteConfirmationDialog(
            confirmation = confirmation,
            onConfirm = {
                onAction(HabitAction.ConfirmDelete)
            },
            onDismiss = {
                onAction(HabitAction.DismissConfirmation)
            },
        )
    }
}

@Composable
private fun CategoryHeader(
    categoryState: HabitCategoryUiState,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryState.category.displayName(),
            style = MaterialTheme.typography.titleMedium
        )

        if (categoryState.hasHiddenHabits) {
            Switch(
                checked = categoryState.showHiddenHabits,
                onCheckedChange = {
                    onToggleHidden()
                },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .semantics {
                        contentDescription =
                            "Show hidden " +
                                    categoryState.category
                                        .displayName() +
                                    " habits"
                    }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(if (categoryState.isExpanded) "▾" else "▸")
    }
}

@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onAction: (HabitAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onAction(HabitAction.InspectHabit(habit.id))
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (habit.showsPlusButton) {
            IconButton(
                onClick = {
                    onAction(HabitAction.AddCompletion(habit.id))
                }
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else {
            Checkbox(
                checked = habit.isCompleted,
                onCheckedChange = { checked ->
                    onAction(
                        if (checked) {
                            HabitAction.AddCompletion(habit.id)
                        } else {
                            HabitAction.RemoveCompletion(habit.id)
                        }
                    )
                }
            )
        }

        Text(
            text = habit.name,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.weight(1f))

        if (habit.allowsMultipleCompletions) {
            Text(
                text = "+${habit.completionCountToday}",
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Text(
            text = "S${habit.streakCount}",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    HorizontalDivider()
}

@Composable
private fun HabitDetailsDialog(
    habit: HabitRowUiState,
    onAction: (HabitAction) -> Unit,
    onArchive: () -> Unit,

    onDeleteHabit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            onAction(HabitAction.DismissHabitDetails)
        },
        title = {
            Text(habit.name)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Daily count: ${habit.completionCountToday}")
                Text(
                    "Schedule progress: " +
                            "${habit.scheduleCompletions}/${habit.scheduleTarget}"
                )

                if (habit.allowsMultipleCompletions) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = habit.completionCountToday > 0,
                            onClick = {
                                onAction(
                                    HabitAction.RemoveCompletion(habit.id)
                                )
                            }
                        ) {
                            Text("−")
                        }

                        Text(habit.completionCountToday.toString())

                        IconButton(
                            onClick = {
                                onAction(
                                    HabitAction.AddCompletion(habit.id)
                                )
                            }
                        ) {
                            Text("+")
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Completed")

                        Checkbox(
                            checked = habit.isCompleted,
                            onCheckedChange = { checked ->
                                val action = if (checked) {
                                    HabitAction.AddCompletion(habit.id)
                                } else {
                                    HabitAction.RemoveCompletion(habit.id)
                                }

                                onAction(action)
                            }
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                TextButton(onClick = onArchive) {
                    Text("Archive habit")
                }

                TextButton(onClick = onDeleteHabit) {
                    Text("Delete habit")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAction(HabitAction.EditHabit(habit.id))
                }
            ) {
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onAction(HabitAction.DismissHabitDetails)
                }
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ArchivedHabitsDialog(
    habits: List<ArchivedHabitUiState>,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Archived habits")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = habits,
                    key = { it.id },
                ) { habit ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = habit.name,
                            modifier = Modifier.weight(1f),
                        )

                        TextButton(
                            onClick = {
                                onRestore(habit.id)
                            },
                        ) {
                            Text("Restore")
                        }

                        TextButton(
                            onClick = {
                                onDelete(habit.id)
                            },
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    confirmation: HabitConfirmationUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deletingHistory =
        confirmation is HabitConfirmationUiState.DeleteHabit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (deletingHistory) {
                    "Delete history?"
                } else {
                    "Delete habit?"
                },
            )
        },
        text = {
            Text("Permanently delete \"${confirmation.habitName}\" and all " +
                    "of its completion history? This cannot be undone."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun HabitCategory.displayName(): String =
    when (this) {
        HabitCategory.MORNING -> "Morning"
        HabitCategory.ANYTIME -> "Anytime"
        HabitCategory.BEFORE_BED -> "Before bed"
    }
