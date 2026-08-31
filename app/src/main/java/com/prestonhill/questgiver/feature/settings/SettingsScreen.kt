@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.prestonhill.questgiver.feature.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object SettingsTags {
    const val BACK = "settings_back"
    const val DAY_BOUNDARY = "settings_day_boundary"
    const val WEEK_START = "settings_week_start"
    const val CONFIRM_TIME = "settings_confirm_time"
    const val DAYLIGHT_SAVING = "settings_daylight_saving"
    const val NUTRITION_GOALS = "settings_nutrition_goals"

    const val CALORIE_GOAL = "settings_calorie_goal"

    const val PROTEIN_GOAL = "settings_protein_goal"

    const val SAVE_NUTRITION_GOALS = "settings_save_nutrition_goals"

    const val CANCEL_NUTRITION_GOALS = "settings_cancel_nutrition_goals"

    fun weekDay(day: DayOfWeek) =
        "settings_week_${day.name}"
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings")
                },
                navigationIcon = {
                    TextButton(
                        modifier =
                            Modifier.testTag(SettingsTags.BACK),
                        onClick = onBack,
                    ) {
                        Text("Back")
                    }
                },
            )
        },
    ) { contentPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SettingsContent(
                state = state,
                onAction = onAction,
                modifier =
                    Modifier.padding(contentPadding),
            )
        }
    }

    state.nutritionGoalsEditor
        ?.let { editor ->
            NutritionGoalsDialog(
                editor = editor,
                isSaving = state.isSaving,
                onAction = onAction,
            )
        }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {
                onAction(SettingsAction.DismissError)
            },
            title = {
                Text("Something went wrong")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(
                            SettingsAction.DismissError
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
private fun NutritionGoalsSetting(
    calorieGoal: Double,
    proteinGoalGrams: Double,
    enabled: Boolean,
    onEdit: () -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Text("Nutrition goals")

        OutlinedButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        SettingsTags
                            .NUTRITION_GOALS
                    ),
            enabled = enabled,
            onClick = onEdit,
        ) {
            Text(
                "${goalText(calorieGoal)} kcal minimum" +
                        " • " +
                        "${goalText(proteinGoalGrams)} g protein minimum"
            )
        }
    }
}

@Composable
private fun NutritionGoalsDialog(
    editor:
    NutritionGoalsEditorUiState,
    isSaving: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onAction(
                    SettingsAction
                        .DismissNutritionGoals
                )
            }
        },
        title = {
            Text("Nutrition goals")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                SettingsTags
                                    .CALORIE_GOAL
                            ),
                    value =
                        editor.calorieGoalText,
                    enabled = !isSaving,
                    singleLine = true,
                    label = {
                        Text(
                            "Minimum calories (kcal)"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number
                        ),
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            onAction(
                                SettingsAction
                                    .ChangeCalorieGoal(
                                        value
                                    )
                            )
                        }
                    },
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                SettingsTags
                                    .PROTEIN_GOAL
                            ),
                    value =
                        editor.proteinGoalText,
                    enabled = !isSaving,
                    singleLine = true,
                    label = {
                        Text(
                            "Minimum protein (g)"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number
                        ),
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            onAction(
                                SettingsAction
                                    .ChangeProteinGoal(
                                        value
                                    )
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                modifier =
                    Modifier.testTag(
                        SettingsTags
                            .SAVE_NUTRITION_GOALS
                    ),
                enabled =
                    editor.canSave &&
                            !isSaving,
                onClick = {
                    onAction(
                        SettingsAction
                            .SaveNutritionGoals
                    )
                },
            ) {
                Text(
                    if (isSaving) {
                        "Saving..."
                    } else {
                        "Save"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        SettingsTags
                            .CANCEL_NUTRITION_GOALS
                    ),
                enabled = !isSaving,
                onClick = {
                    onAction(
                        SettingsAction
                            .DismissNutritionGoals
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement =
            Arrangement.spacedBy(24.dp),
    ) {
        if (state.isSaving) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        DayBoundarySetting(
            time = state.settings.dayBoundary,
            enabled = !state.isSaving,
            onTimeSelected = { time ->
                onAction(
                    SettingsAction.SetDayBoundary(time)
                )
            },
        )

        WeekStartSetting(
            day = state.settings.weekStart,
            enabled = !state.isSaving,
            onDaySelected = { day ->
                onAction(
                    SettingsAction.SetWeekStart(day)
                )
            },
        )

        DaylightSavingSetting(
            checked =
                state.settings.daylightSavingEnabled,
            enabled = !state.isSaving,
            onCheckedChange = { checked ->
                onAction(
                    SettingsAction.SetDaylightSaving(
                        checked
                    )
                )
            },
        )

        NutritionGoalsSetting(
            calorieGoal =
                state.settings.calorieGoal,
            proteinGoalGrams =
                state.settings
                    .proteinGoalGrams,
            enabled = !state.isSaving,
            onEdit = {
                onAction(
                    SettingsAction
                        .EditNutritionGoals
                )
            },
        )
    }
}

@Composable
private fun DaylightSavingSetting(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Use daylight saving time")

        Switch(
            modifier =
                Modifier.testTag(
                    SettingsTags.DAYLIGHT_SAVING
                ),
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DayBoundarySetting(
    time: LocalTime,
    enabled: Boolean,
    onTimeSelected: (LocalTime) -> Unit,
) {
    var showPicker by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val use24HourTime =
        DateFormat.is24HourFormat(context)

    val formatter =
        remember(use24HourTime) {
            DateTimeFormatter.ofPattern(
                if (use24HourTime) {
                    "HH:mm"
                } else {
                    "h:mm a"
                },
                Locale.getDefault(),
            )
        }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Text("Day begins at")

        OutlinedButton(
            modifier =
                Modifier.testTag(
                    SettingsTags.DAY_BOUNDARY
                ),
            enabled = enabled,
            onClick = {
                showPicker = true
            },
        ) {
            Text(time.format(formatter))
        }
    }

    if (showPicker) {
        val pickerState =
            rememberTimePickerState(
                initialHour = time.hour,
                initialMinute = time.minute,
                is24Hour = use24HourTime,
            )

        AlertDialog(
            onDismissRequest = {
                showPicker = false
            },
            text = {
                TimePicker(state = pickerState)
            },
            confirmButton = {
                Button(
                    modifier =
                        Modifier.testTag(
                            SettingsTags.CONFIRM_TIME
                        ),
                    onClick = {
                        onTimeSelected(
                            LocalTime.of(
                                pickerState.hour,
                                pickerState.minute,
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
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun WeekStartSetting(
    day: DayOfWeek,
    enabled: Boolean,
    onDaySelected: (DayOfWeek) -> Unit,
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text("Week starts on")

            OutlinedButton(
                modifier =
                    Modifier.testTag(
                        SettingsTags.WEEK_START
                    ),
                enabled = enabled,
                onClick = {
                    expanded = true
                },
            ) {
                Text(day.displayName())
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            DayOfWeek.entries.forEach { option ->
                DropdownMenuItem(
                    modifier =
                        Modifier.testTag(
                            SettingsTags.weekDay(option)
                        ),
                    text = {
                        Text(option.displayName())
                    },
                    onClick = {
                        expanded = false
                        onDaySelected(option)
                    },
                )
            }
        }
    }
}

private fun goalText(
    value: Double,
): String =
    value.toString()
        .removeSuffix(".0")

private fun DayOfWeek.displayName(): String =
    getDisplayName(
        TextStyle.FULL,
        Locale.getDefault(),
    )