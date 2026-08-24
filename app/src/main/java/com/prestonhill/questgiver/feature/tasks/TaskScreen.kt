@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.prestonhill.questgiver.feature.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle

object TaskTags {
    const val HIDDEN_TOGGLE =
        "task_hidden_toggle"

    const val TODAY_LIST =
        "task_today_list"

    const val UPCOMING_LIST =
        "task_upcoming_list"

    const val DELETE_TASK =
        "task_delete"

    const val DELETE_HISTORY =
        "task_delete_history"

    const val ADD = "task_add"
    const val EDIT = "task_edit"
    const val EDITOR_NAME = "task_editor_name"
    const val EDITOR_CATEGORY = "task_editor_category"
    const val EDITOR_SCHEDULE = "task_editor_schedule"
    const val EDITOR_SAVE = "task_editor_save"
    const val EDITOR_DATE = "task_editor_date"
    const val EDITOR_START = "task_editor_start"
    const val EDITOR_DUE_TIME = "task_editor_due_time"

    fun weekday(day: DayOfWeek) =
        "task_weekday_${day.name}"

    fun row(taskId: Long) =
        "task_row_$taskId"

    fun check(taskId: Long) =
        "task_check_$taskId"

    fun upcoming(taskId: Long) =
        "task_upcoming_$taskId"
}

@Composable
fun TaskScreen(
    state: TaskScreenUiState,
    onAction: (TaskAction) -> Unit,
) {
    val timeFormatter = rememberTimeFormatter()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text("Today")

            Spacer(Modifier.weight(1f))

            TextButton(
                modifier =
                    Modifier.testTag(TaskTags.ADD),
                onClick = {
                    onAction(TaskAction.Add)
                },
            ) {
                Text("Add")
            }

            if (state.hasHiddenToday) {
                Switch(
                    modifier =
                        Modifier.testTag(
                            TaskTags.HIDDEN_TOGGLE
                        ),
                    checked =
                        state.showHiddenToday,
                    onCheckedChange = {
                        onAction(
                            TaskAction.ToggleHidden
                        )
                    },
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(TaskTags.TODAY_LIST),
            verticalArrangement =
                Arrangement.spacedBy(4.dp),
        ) {
            if (state.today.isEmpty()) {
                item {
                    Text(
                        text = "No tasks today",
                        modifier =
                            Modifier.padding(
                                vertical = 12.dp
                            ),
                    )
                }
            }

            items(
                items = state.today,
                key = TaskRowUiState::id,
            ) { task ->
                TodayTaskRow(
                    task = task,
                    timeFormatter =
                        timeFormatter,
                    onAction = onAction,
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Upcoming",
            modifier =
                Modifier.padding(vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(TaskTags.UPCOMING_LIST),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            if (state.upcoming.isEmpty()) {
                item {
                    Text(
                        text = "Nothing upcoming",
                        modifier =
                            Modifier.padding(
                                vertical = 12.dp
                            ),
                    )
                }
            }

            items(
                items = state.upcoming,
                key = {
                    it.date.toEpochDay()
                },
            ) { day ->
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        day.date.format(
                            DateTimeFormatter.ofPattern(
                                "EEE, MMM d",
                                LocalLocale.current.platformLocale,
                            )
                        )
                    )

                    day.tasks.forEach { task ->
                        UpcomingTaskCard(
                            task = task,
                            timeFormatter =
                                timeFormatter,
                            onInspect = {
                                onAction(
                                    TaskAction.Inspect(
                                        task.id
                                    )
                                )
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    state.editor?.let { editor ->
        TaskEditorDialog(
            editor = editor,
            timeFormatter = timeFormatter,
            onAction = onAction,
        )
    }

    state.inspectedTaskId
        ?.let(state::findTask)
        ?.let { task ->
            TaskDetailsDialog(
                task = task,
                timeFormatter = timeFormatter,
                onAction = onAction,
            )
        }

    state.confirmation?.let { confirmation ->
        DeleteTaskDialog(
            confirmation = confirmation,
            onAction = onAction,
        )
    }

    state.operationError?.let { message ->
        AlertDialog(
            onDismissRequest = {
                onAction(TaskAction.DismissError)
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
                            TaskAction.DismissError
                        )
                    }
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun TodayTaskRow(
    task: TaskRowUiState,
    timeFormatter: DateTimeFormatter,
    onAction: (TaskAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TaskTags.row(task.id))
            .clickable {
                onAction(
                    TaskAction.Inspect(task.id)
                )
            }
            .padding(vertical = 4.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Checkbox(
            modifier =
                Modifier.testTag(
                    TaskTags.check(task.id)
                ),
            checked = task.isCompleted,
            enabled = task.canComplete,
            onCheckedChange = { checked ->
                if (checked && task.canComplete) {
                    onAction(
                        TaskAction.Complete(
                            taskId = task.id,
                            completionEpochDay =
                                task.completionEpochDay,
                        )
                    )
                }
            },
        )

        Text(task.name)

        Spacer(Modifier.weight(1f))

        task.dueTime?.let { time ->
            Text(time.format(timeFormatter))
        }
    }
}

@Composable
private fun UpcomingTaskCard(
    task: TaskRowUiState,
    timeFormatter: DateTimeFormatter,
    onInspect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                TaskTags.upcoming(task.id)
            ),
        onClick = onInspect,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(task.name)

            Spacer(Modifier.weight(1f))

            task.dueTime?.let { time ->
                Text(time.format(timeFormatter))
            }
        }
    }
}

@Composable
private fun TaskDetailsDialog(
    task: TaskRowUiState,
    timeFormatter: DateTimeFormatter,
    onAction: (TaskAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onAction(TaskAction.DismissDetails)
        },
        title = {
            Text(task.name)
        },
        text = {
            Column {
                task.dueTime?.let { time ->
                    Text(
                        "Due ${time.format(timeFormatter)}"
                    )
                }

                TextButton(
                    modifier =
                        Modifier.testTag(TaskTags.EDIT),
                    onClick = {
                        onAction(
                            TaskAction.Edit(task.id)
                        )
                    },
                ) {
                    Text("Edit")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        TaskAction.RequestDelete(
                            task.id
                        )
                    )
                },
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onAction(
                        TaskAction.DismissDetails
                    )
                },
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun DeleteTaskDialog(
    confirmation: TaskDeleteUiState,
    onAction: (TaskAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!confirmation.isDeleting) {
                onAction(
                    TaskAction.DismissDelete
                )
            }
        },
        title = {
            Text("Delete ${confirmation.taskName}?")
        },
        text = {
            Column {
                Text(
                    confirmation.errorMessage
                        ?: "Choose what should be deleted."
                )

                TextButton(
                    modifier =
                        Modifier.testTag(
                            TaskTags.DELETE_HISTORY
                        ),
                    enabled =
                        !confirmation.isDeleting,
                    onClick = {
                        onAction(
                            TaskAction
                                .DeleteTaskAndHistory
                        )
                    },
                ) {
                    Text("Delete task and history")
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        TaskTags.DELETE_TASK
                    ),
                enabled =
                    !confirmation.isDeleting,
                onClick = {
                    onAction(
                        TaskAction.DeleteTask
                    )
                },
            ) {
                Text("Delete task only")
            }
        },
        dismissButton = {
            TextButton(
                enabled =
                    !confirmation.isDeleting,
                onClick = {
                    onAction(
                        TaskAction.DismissDelete
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TaskEditorDialog(
    editor: TaskEditorUiState,
    timeFormatter: DateTimeFormatter,
    onAction: (TaskAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) {
                onAction(
                    TaskAction.DismissEditor
                )
            }
        },
        title = {
            Text(
                if (editor.isEditing) {
                    "Edit task"
                } else {
                    "Add task"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            TaskTags.EDITOR_NAME
                        ),
                    value = editor.name,
                    enabled = !editor.isSaving,
                    label = {
                        Text("Name")
                    },
                    singleLine = true,
                    onValueChange = { name ->
                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    name = name
                                )
                            )
                        )
                    },
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            TaskTags.EDITOR_CATEGORY
                        ),
                    value = editor.category,
                    enabled = !editor.isSaving,
                    label = {
                        Text("Category")
                    },
                    singleLine = true,
                    onValueChange = { category ->
                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    category = category
                                )
                            )
                        )
                    },
                )

                ScheduleSetting(
                    editor = editor,
                    onAction = onAction,
                )

                when (editor.scheduleType) {
                    TaskScheduleType.ONE_TIME -> {
                        DateSetting(
                            label = "Scheduled date",
                            date =
                                editor.scheduledDate,
                            allowNoDate = true,
                            enabled = !editor.isSaving,
                            tag =
                                TaskTags.EDITOR_DATE,
                            onDateSelected = { date ->
                                onAction(
                                    TaskAction.UpdateEditor(
                                        editor.copy(
                                            scheduledDate =
                                                date,
                                            dueTime =
                                                if (date == null) {
                                                    null
                                                } else {
                                                    editor.dueTime
                                                },
                                        )
                                    )
                                )
                            },
                        )
                    }

                    TaskScheduleType.DAILY -> {
                        StartDateSetting(
                            editor = editor,
                            onAction = onAction,
                        )
                    }

                    TaskScheduleType.WEEKLY_DAYS -> {
                        StartDateSetting(
                            editor = editor,
                            onAction = onAction,
                        )

                        WeekdaySetting(
                            editor = editor,
                            onAction = onAction,
                        )
                    }

                    TaskScheduleType.INTERVAL -> {
                        StartDateSetting(
                            editor = editor,
                            onAction = onAction,
                        )

                        OutlinedTextField(
                            modifier =
                                Modifier.fillMaxWidth(),
                            value =
                                editor.intervalDays,
                            enabled =
                                !editor.isSaving,
                            label = {
                                Text("Interval days")
                            },
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Number
                                ),
                            onValueChange = { value ->
                                onAction(
                                    TaskAction.UpdateEditor(
                                        editor.copy(
                                            intervalDays =
                                                value
                                        )
                                    )
                                )
                            },
                        )

                        IntervalBasisSetting(
                            editor = editor,
                            onAction = onAction,
                        )
                    }
                }

                DueTimeSetting(
                    editor = editor,
                    timeFormatter = timeFormatter,
                    onAction = onAction,
                )

                if (
                    editor.dueTime != null ||
                    (
                            editor.scheduleType ==
                                    TaskScheduleType
                                        .ONE_TIME &&
                                    editor
                                        .scheduledDate !=
                                    null
                            )
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            "Keep visible after due",
                            modifier =
                                Modifier.weight(1f),
                        )

                        Switch(
                            checked =
                                editor
                                    .remainsVisibleAfterDue,
                            enabled =
                                !editor.isSaving,
                            onCheckedChange = { checked ->
                                onAction(
                                    TaskAction.UpdateEditor(
                                        editor.copy(
                                            remainsVisibleAfterDue =
                                                checked
                                        )
                                    )
                                )
                            },
                        )
                    }
                }

                editor.errorMessage?.let { message ->
                    Text(message)
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        TaskTags.EDITOR_SAVE
                    ),
                enabled = editor.canSave,
                onClick = {
                    onAction(TaskAction.Save)
                },
            ) {
                Text(
                    if (editor.isSaving) {
                        "Saving"
                    } else {
                        "Save"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !editor.isSaving,
                onClick = {
                    onAction(
                        TaskAction.DismissEditor
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ScheduleSetting(
    editor: TaskEditorUiState,
    onAction: (TaskAction) -> Unit,
) {
    var expanded by remember {
        androidx.compose.runtime.mutableStateOf(
            false
        )
    }

    Box {
        Column {
            Text("Schedule")

            OutlinedButton(
                modifier =
                    Modifier.testTag(
                        TaskTags.EDITOR_SCHEDULE
                    ),
                enabled = !editor.isSaving,
                onClick = {
                    expanded = true
                },
            ) {
                Text(
                    editor.scheduleType.displayName()
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            TaskScheduleType.entries.forEach {
                    schedule ->
                DropdownMenuItem(
                    text = {
                        Text(
                            schedule.displayName()
                        )
                    },
                    onClick = {
                        expanded = false

                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    scheduleType =
                                        schedule
                                )
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StartDateSetting(
    editor: TaskEditorUiState,
    onAction: (TaskAction) -> Unit,
) {
    DateSetting(
        label = "Starts",
        date =
            editor.recurrenceStartDate,
        allowNoDate = false,
        enabled = !editor.isSaving,
        tag = TaskTags.EDITOR_START,
        onDateSelected = { date ->
            onAction(
                TaskAction.UpdateEditor(
                    editor.copy(
                        recurrenceStartDate = date
                    )
                )
            )
        },
    )
}

@Composable
private fun WeekdaySetting(
    editor: TaskEditorUiState,
    onAction: (TaskAction) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Text("Days")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    modifier =
                        Modifier.testTag(
                            TaskTags.weekday(day)
                        ),
                    selected =
                        day in
                                editor
                                    .selectedWeekdays,
                    enabled = !editor.isSaving,
                    onClick = {
                        val selected =
                            if (
                                day in
                                editor.selectedWeekdays
                            ) {
                                editor
                                    .selectedWeekdays -
                                        day
                            } else {
                                editor
                                    .selectedWeekdays +
                                        day
                            }

                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    selectedWeekdays =
                                        selected
                                )
                            )
                        )
                    },
                    label = {
                        Text(
                            day.getDisplayName(
                                TextStyle.SHORT,
                                LocalLocale.current.platformLocale,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun IntervalBasisSetting(
    editor: TaskEditorUiState,
    onAction: (TaskAction) -> Unit,
) {
    var expanded by remember {
        androidx.compose.runtime.mutableStateOf(
            false
        )
    }

    Box {
        Column {
            Text("Interval starts from")

            OutlinedButton(
                enabled = !editor.isSaving,
                onClick = {
                    expanded = true
                },
            ) {
                Text(
                    editor.intervalBasis
                        .displayName()
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            TaskIntervalBasis.entries.forEach {
                    basis ->
                DropdownMenuItem(
                    text = {
                        Text(
                            basis.displayName()
                        )
                    },
                    onClick = {
                        expanded = false

                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    intervalBasis =
                                        basis
                                )
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DateSetting(
    label: String,
    date: LocalDate?,
    allowNoDate: Boolean,
    enabled: Boolean,
    tag: String,
    onDateSelected: (LocalDate?) -> Unit,
) {
    var showPicker by remember {
        androidx.compose.runtime.mutableStateOf(
            false
        )
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Text(label)

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier =
                    Modifier.testTag(tag),
                enabled = enabled,
                onClick = {
                    showPicker = true
                },
            ) {
                Text(
                    date?.format(
                        DateTimeFormatter.ofPattern(
                            "MMM d, yyyy",
                            Locale.getDefault(),
                        )
                    ) ?: "Choose date"
                )
            }

            if (
                allowNoDate &&
                date != null
            ) {
                TextButton(
                    enabled = enabled,
                    onClick = {
                        onDateSelected(null)
                    },
                ) {
                    Text("No date")
                }
            }
        }
    }

    if (showPicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    date?.atStartOfDay(
                        ZoneOffset.UTC
                    )
                        ?.toInstant()
                        ?.toEpochMilli(),
            )

        DatePickerDialog(
            onDismissRequest = {
                showPicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState
                            .selectedDateMillis
                            ?.let { milliseconds ->
                                Instant
                                    .ofEpochMilli(
                                        milliseconds
                                    )
                                    .atZone(
                                        ZoneOffset.UTC
                                    )
                                    .toLocalDate()
                            }
                            ?.let(onDateSelected)

                        showPicker = false
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DueTimeSetting(
    editor: TaskEditorUiState,
    timeFormatter: DateTimeFormatter,
    onAction: (TaskAction) -> Unit,
) {
    var showPicker by remember {
        androidx.compose.runtime.mutableStateOf(
            false
        )
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Text("Due time")

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier =
                    Modifier.testTag(
                        TaskTags.EDITOR_DUE_TIME
                    ),
                enabled =
                    !editor.isSaving &&
                            (
                                    editor.scheduleType !=
                                            TaskScheduleType
                                                .ONE_TIME ||
                                            editor
                                                .scheduledDate !=
                                            null
                                    ),
                onClick = {
                    showPicker = true
                },
            ) {
                Text(
                    editor.dueTime
                        ?.format(timeFormatter)
                        ?: "No time"
                )
            }

            if (editor.dueTime != null) {
                TextButton(
                    enabled = !editor.isSaving,
                    onClick = {
                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    dueTime = null,
                                    remainsVisibleAfterDue =
                                        false,
                                )
                            )
                        )
                    },
                ) {
                    Text("Clear")
                }
            }
        }
    }

    if (showPicker) {
        val pickerState =
            rememberTimePickerState(
                initialHour =
                    editor.dueTime?.hour ?: 9,
                initialMinute =
                    editor.dueTime?.minute ?: 0,
                is24Hour = true,
            )

        AlertDialog(
            onDismissRequest = {
                showPicker = false
            },
            text = {
                TimePicker(state = pickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(
                            TaskAction.UpdateEditor(
                                editor.copy(
                                    dueTime =
                                        LocalTime.of(
                                            pickerState.hour,
                                            pickerState.minute,
                                        )
                                )
                            )
                        )

                        showPicker = false
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun TaskScheduleType.displayName(): String =
    when (this) {
        TaskScheduleType.ONE_TIME ->
            "One time"

        TaskScheduleType.DAILY ->
            "Daily"

        TaskScheduleType.WEEKLY_DAYS ->
            "Selected weekdays"

        TaskScheduleType.INTERVAL ->
            "Every N days"
    }

private fun TaskIntervalBasis.displayName(): String =
    when (this) {
        TaskIntervalBasis.FIXED_SCHEDULE ->
            "Fixed schedule"

        TaskIntervalBasis.FROM_COMPLETION ->
            "Last completion"
    }

@Composable
private fun rememberTimeFormatter():
        DateTimeFormatter {
    val context = LocalContext.current

    val use24Hour =
        DateFormat.is24HourFormat(context)

    return remember(use24Hour) {
        DateTimeFormatter.ofPattern(
            if (use24Hour) {
                "HH:mm"
            } else {
                "h:mm a"
            },
            Locale.getDefault(),
        )
    }
}

private fun TaskScreenUiState.findTask(
    taskId: Long,
): TaskRowUiState? =
    today.firstOrNull {
        it.id == taskId
    }
        ?: upcoming
            .asSequence()
            .flatMap { day ->
                day.tasks.asSequence()
            }
            .firstOrNull {
                it.id == taskId
            }