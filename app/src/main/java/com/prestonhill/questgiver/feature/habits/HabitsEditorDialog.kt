package com.prestonhill.questgiver.feature.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun HabitEditorDialog(
    editor: HabitEditorUiState,
    onChange: (HabitEditorUiState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    sections: List<HabitDisplaySectionUiState>,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editor.isEditing) {
                    "Edit habit"
                } else {
                    "Add habit"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { name ->
                        onChange(
                            editor.copy(
                                name = name,
                                errorMessage = null
                            )
                        )
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HabitTags.NAME)
                )
                SectionLabel("Display section")

                sections.forEach { section ->
                    ChoiceRow(
                        modifier =
                            Modifier.testTag(
                                HabitTags.editorSection(
                                    section.id
                                )
                            ),
                        selected =
                            editor.displaySectionId ==
                                    section.id,
                        label = section.name,
                        onClick = {
                            onChange(
                                editor.copy(
                                    displaySectionId =
                                        section.id
                                )
                            )
                        },
                    )
                }

                OutlinedTextField(
                    value = editor.historyCategory,
                    onValueChange = { value ->
                        onChange(
                            editor.copy(
                                historyCategory = value,
                                errorMessage = null,
                            )
                        )
                    },
                    label = {
                        Text("Category (optional)")
                    },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                HabitTags.HISTORY_CATEGORY
                            ),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked =
                            editor.allowsMultipleCompletions,
                        enabled = !editor.isEditing,
                        onCheckedChange = { checked ->
                            onChange(
                                editor.copy(
                                    allowsMultipleCompletions =
                                        checked
                                )
                            )
                        }
                    )

                    Text("Allow multiple completions")
                }

                SectionLabel("Schedule")

                HabitScheduleType.entries.forEach { type ->
                    ChoiceRow(
                        selected =
                            editor.scheduleType == type,
                        label = type.displayName(),
                        onClick = {
                            onChange(
                                editor.copy(
                                    scheduleType = type,
                                    scheduleTarget =
                                        if (
                                            type ==
                                            HabitScheduleType.INTERVAL
                                        ) {
                                            "1"
                                        } else {
                                            editor.scheduleTarget
                                        }
                                )
                            )
                        }
                    )
                }

                if (
                    editor.scheduleType !=
                    HabitScheduleType.INTERVAL
                ) {
                    OutlinedTextField(
                        value = editor.scheduleTarget,
                        onValueChange = { value ->
                            if (
                                value.isEmpty() ||
                                value.all(Char::isDigit)
                            ) {
                                onChange(
                                    editor.copy(
                                        scheduleTarget = value,
                                        errorMessage = null
                                    )
                                )
                            }
                        },
                        label = { Text("Target count") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (
                    editor.scheduleType ==
                    HabitScheduleType.INTERVAL
                ) {
                    OutlinedTextField(
                        value = editor.intervalDays,
                        onValueChange = { value ->
                            if (
                                value.isEmpty() ||
                                value.all(Char::isDigit)
                            ) {
                                onChange(
                                    editor.copy(
                                        intervalDays = value,
                                        errorMessage = null
                                    )
                                )
                            }
                        },
                        label = { Text("Interval days") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SectionLabel("Interval basis")

                    HabitIntervalBasis.entries.forEach { basis ->
                        ChoiceRow(
                            selected =
                                editor.intervalBasis == basis,
                            label = basis.displayName(),
                            onClick = {
                                onChange(
                                    editor.copy(
                                        intervalBasis = basis
                                    )
                                )
                            }
                        )
                    }

                    if (
                        editor.intervalBasis ==
                        HabitIntervalBasis.FROM_COMPLETION
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked =
                                    editor
                                        .extraCompletionsMoveNextDueDate,
                                onCheckedChange = { checked ->
                                    onChange(
                                        editor.copy(
                                            extraCompletionsMoveNextDueDate =
                                                checked
                                        )
                                    )
                                }
                            )

                            Text(
                                "Extra completions move next due date"
                            )
                        }
                    }
                }

                SectionLabel("Habit-screen visibility")

                HabitScheduleVisibility.entries.forEach {
                        visibility ->
                    ChoiceRow(
                        modifier = Modifier.testTag(
                            HabitTags.visibility(visibility)
                        ),
                        selected =
                            editor.scheduleVisibility ==
                                    visibility,
                        label = visibility.displayName(),
                        onClick = {
                            onChange(
                                editor.copy(
                                    scheduleVisibility =
                                        visibility
                                )
                            )
                        }
                    )
                }

                editor.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag(HabitTags.SAVE),
                enabled = editor.canSave,
                onClick = onSave,
            ) {
                Text(
                    if (editor.isSaving) {
                        "Saving…"
                    } else {
                        "Save"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Text(label)
    }
}

private fun HabitScheduleType.displayName(): String =
    when (this) {
        HabitScheduleType.DAILY -> "Daily"
        HabitScheduleType.WEEKLY_TARGET -> "Weekly target"
        HabitScheduleType.INTERVAL -> "Every N days"
    }

private fun HabitIntervalBasis.displayName(): String =
    when (this) {
        HabitIntervalBasis.FIXED_SCHEDULE ->
            "Fixed schedule"

        HabitIntervalBasis.FROM_COMPLETION ->
            "From completion"
    }

private fun HabitScheduleVisibility.displayName(): String =
    when (this) {
        HabitScheduleVisibility.ALWAYS ->
            "Always visible"

        HabitScheduleVisibility.WHEN_DUE ->
            "Only when due"

        HabitScheduleVisibility.HIDE_AFTER_TARGET ->
            "Hide after target"
    }