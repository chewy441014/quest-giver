@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)

package com.prestonhill.questgiver.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object NutritionTags {
    const val DATE = "nutrition_date"
    const val CALORIE_PROGRESS =
        "nutrition_calorie_progress"
    const val PROTEIN_PROGRESS =
        "nutrition_protein_progress"
    const val MANAGE = "nutrition_manage"
    const val ADD = "nutrition_add"
    const val DATE_CONFIRM =
        "nutrition_date_confirm"
    const val DATE_CANCEL =
        "nutrition_date_cancel"

    fun log(logId: Long) =
        "nutrition_log_$logId"
}

@Composable
fun NutritionScreen(
    state: NutritionScreenUiState,
    onAction: (NutritionAction) -> Unit,
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        return
    }

    if (
        state.destination ==
        NutritionDestination.Manage
    ) {
        NutritionManageScreen(
            state = state.manage,
            onAction = onAction,
        )
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            DateHeader(
                state = state,
                onAction = onAction,
            )

            GoalProgress(
                label = "Calories",
                amount =
                    state.totalCalories,
                goal = state.calorieGoal,
                unit = "kcal",
                progress =
                    state.calorieProgress,
                tag =
                    NutritionTags
                        .CALORIE_PROGRESS,
            )

            GoalProgress(
                label = "Protein",
                amount =
                    state.totalProteinGrams,
                goal =
                    state.proteinGoalGrams,
                unit = "g",
                progress =
                    state.proteinProgress,
                tag =
                    NutritionTags
                        .PROTEIN_PROGRESS,
            )

            if (state.logs.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Text(
                        "No food logged for this day."
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),
                ) {
                    items(
                        items = state.logs,
                        key = {
                            it.logId
                        },
                    ) { log ->
                        NutritionLogRow(
                            log = log,
                            onClick = {
                                onAction(
                                    NutritionAction
                                        .InspectLog(
                                            log.logId
                                        )
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    modifier =
                        Modifier.testTag(
                            NutritionTags.MANAGE
                        ),
                    onClick = {
                        onAction(
                            NutritionAction
                                .OpenManage
                        )
                    },
                ) {
                    Text("Manage")
                }

                Button(
                    modifier =
                        Modifier.testTag(
                            NutritionTags.ADD
                        ),
                    onClick = {
                        onAction(
                            NutritionAction
                                .OpenAddLog
                        )
                    },
                ) {
                    Text("Add")
                }
            }
        }
    }

    if (state.showDatePicker) {
        NutritionDatePicker(
            selectedDate =
                requireNotNull(
                    state.selectedDate
                ),
            currentDate =
                requireNotNull(
                    state.currentDate
                ),
            onAction = onAction,
        )
    }

    state.logEditor?.let { editor ->
        NutritionLogEditorDialog(
            editor = editor,
            onAction = onAction,
        )
    }

    state.itemEditor?.let { editor ->
        NutritionItemEditorDialog(
            editor = editor,
            onAction = onAction,
        )
    }

    state.operationError
        ?.let { message ->
            AlertDialog(
                onDismissRequest = {
                    onAction(
                        NutritionAction
                            .DismissOperationError
                    )
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
                                NutritionAction
                                    .DismissOperationError
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
private fun DateHeader(
    state: NutritionScreenUiState,
    onAction: (NutritionAction) -> Unit,
) {
    val date =
        requireNotNull(state.selectedDate)

    val formatter =
        remember {
            DateTimeFormatter.ofPattern(
                "EEE, MMM d, yyyy",
                Locale.getDefault(),
            )
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column {
            Text(
                if (state.isCurrentDay) {
                    "Today"
                } else {
                    "Nutrition"
                },
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                date.format(formatter),
                style =
                    MaterialTheme
                        .typography.bodyMedium,
            )
        }

        OutlinedButton(
            modifier =
                Modifier.testTag(
                    NutritionTags.DATE
                ),
            onClick = {
                onAction(
                    NutritionAction
                        .OpenDatePicker
                )
            },
        ) {
            Text("Calendar")
        }
    }
}

@Composable
private fun GoalProgress(
    label: String,
    amount: Double,
    goal: Double,
    unit: String,
    progress: Float,
    tag: String,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style =
                    MaterialTheme
                        .typography.titleSmall,
            )

            Text(
                "${amountText(amount)} / " +
                        "${amountText(goal)} $unit minimum"
            )
        }

        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(tag),
        )
    }
}

@Composable
private fun NutritionLogRow(
    log: NutritionLogRowUiState,
    onClick: () -> Unit,
) {
    val timeFormatter =
        remember {
            DateTimeFormatter
                .ofLocalizedTime(
                    FormatStyle.SHORT
                )
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    NutritionTags.log(
                        log.logId
                    )
                )
                .clickable(
                    onClick = onClick
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${log.itemName} · " +
                        (
                                log.versionLabel
                                    ?: "v${log.itemVersion}"
                                ),
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                "${log.consumedTime.format(timeFormatter)}" +
                        " · " +
                        "${amountText(log.weightGrams)} g"
            )

            Text(
                "${amountText(log.calories)} kcal" +
                        " · " +
                        "${amountText(log.proteinGrams)} g protein"
            )

            if (log.isItemArchived) {
                Text(
                    "Archived item",
                    style =
                        MaterialTheme
                            .typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun NutritionDatePicker(
    selectedDate: LocalDate,
    currentDate: LocalDate,
    onAction: (NutritionAction) -> Unit,
) {
    val selectableDates =
        remember(currentDate) {
            object : SelectableDates {
                override fun isSelectableDate(
                    utcTimeMillis: Long,
                ): Boolean =
                    Instant
                        .ofEpochMilli(
                            utcTimeMillis
                        )
                        .atZone(
                            ZoneOffset.UTC
                        )
                        .toLocalDate() <=
                            currentDate

                override fun isSelectableYear(
                    year: Int,
                ): Boolean =
                    year <= currentDate.year
            }
        }

    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                selectedDate
                    .atStartOfDay(
                        ZoneOffset.UTC
                    )
                    .toInstant()
                    .toEpochMilli(),
            selectableDates =
                selectableDates,
        )

    DatePickerDialog(
        onDismissRequest = {
            onAction(
                NutritionAction
                    .DismissDatePicker
            )
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        NutritionTags
                            .DATE_CONFIRM
                    ),
                enabled =
                    pickerState
                        .selectedDateMillis !=
                            null,
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
                        ?.let { date ->
                            onAction(
                                NutritionAction
                                    .SelectDate(
                                        date
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
                        NutritionTags
                            .DATE_CANCEL
                    ),
                onClick = {
                    onAction(
                        NutritionAction
                            .DismissDatePicker
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(
            state = pickerState
        )
    }
}

private fun amountText(
    value: Double,
): String {
    val rounded = round(value)

    return if (
        abs(value - rounded) <
        0.05
    ) {
        rounded.toLong().toString()
    } else {
        String.format(
            Locale.getDefault(),
            "%.1f",
            value,
        )
    }
}