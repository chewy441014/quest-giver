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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun HabitHistoryDashboard(
    state: HabitHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val dateFormatter =
        remember {
            DateTimeFormatter
                .ofLocalizedDate(
                    FormatStyle.MEDIUM
                )
                .withLocale(
                    Locale.getDefault()
                )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    HistoryTags
                        .HABIT_DASHBOARD
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
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Habit history",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                    )

                    Text(
                        text =
                            if (
                                state
                                    .showArchivedHabits
                            ) {
                                "Archived habits"
                            } else {
                                "Active habits"
                            },
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )

                Switch(
                    modifier =
                        Modifier
                            .testTag(
                                HistoryTags
                                    .HABIT_ARCHIVED_TOGGLE
                            )
                            .semantics {
                                contentDescription =
                                    "Show archived habit history"
                            },
                    checked =
                        state.showArchivedHabits,
                    onCheckedChange = { show ->
                        onAction(
                            HistoryAction
                                .ShowArchivedHabits(
                                    show
                                )
                        )
                    },
                )
            }
        }

        item {
            Text(
                text = "Schedule performance",
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )
        }

        item {
            LazyRow(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .HABIT_RANGE_LIST
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items =
                        HabitHistoryRangePreset
                            .entries,
                    key = {
                        it.name
                    },
                ) { preset ->
                    FilterChip(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .habitRange(
                                        preset
                                    )
                            ),
                        selected =
                            state.rangePreset ==
                                    preset,
                        onClick = {
                            if (
                                preset ==
                                HabitHistoryRangePreset
                                    .CUSTOM
                            ) {
                                onAction(
                                    HistoryAction
                                        .OpenHabitCustomRange
                                )
                            } else {
                                onAction(
                                    HistoryAction
                                        .SelectHabitRange(
                                            preset
                                        )
                                )
                            }
                        },
                        label = {
                            Text(preset.label)
                        },
                    )
                }
            }
        }

        state.selectedRange?.let {
                selectedRange ->
            item {
                Text(
                    text =
                        selectedRange.startDate
                            .format(
                                dateFormatter
                            ) +
                                " – " +
                                selectedRange.endDate
                                    .format(
                                        dateFormatter
                                    ),
                    style =
                        MaterialTheme
                            .typography.bodyMedium,
                )
            }
        }

        if (state.selectedRange == null) {
            item {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.performance.isEmpty()) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            if (
                                state
                                    .showArchivedHabits
                            ) {
                                "No archived habit " +
                                        "performance to show."
                            } else {
                                "No active habits are " +
                                        "included in history."
                            },
                        modifier =
                            Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(
                items = state.performance,
                key = {
                    it.habitId
                },
            ) { performance ->
                HabitPerformanceCard(
                    state = performance
                )
            }
        }

        item {
            HabitCompletionChartCard(
                state = state.completionChart,
                onToggleHabit = { habitId ->
                    onAction(
                        HistoryAction
                            .ToggleHabitCompletionSeries(
                                habitId
                            )
                    )
                },
                onSelectAll = {
                    onAction(
                        HistoryAction
                            .SelectAllHabitCompletionSeries
                    )
                },
            )
        }

        item {
            HistoryStampCalendarCard(
                state = state.stampCalendar,
                tagPrefix =
                    HistoryTags
                        .HABIT_STAMP_PREFIX,
                title =
                    if (
                        state.showArchivedHabits
                    ) {
                        "Archived habit calendar"
                    } else {
                        "Habit completion calendar"
                    },
                emptyMessage =
                    if (
                        state.showArchivedHabits
                    ) {
                        "No archived habit history " +
                                "to show."
                    } else {
                        "No active habits are " +
                                "included in history."
                    },
                onPreviousMonth = {
                    onAction(
                        HistoryAction
                            .PreviousHabitCalendarMonth
                    )
                },
                onNextMonth = {
                    onAction(
                        HistoryAction
                            .NextHabitCalendarMonth
                    )
                },
                onToggleFilter = { key ->
                    onAction(
                        HistoryAction
                            .ToggleHabitStampFilter(
                                key
                            )
                    )
                },
                onSelectAll = {
                    onAction(
                        HistoryAction
                            .SelectAllHabitStamps
                    )
                },
                onOpenDay = { date ->
                    onAction(
                        HistoryAction
                            .OpenHabitCalendarDay(
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
                            .SetHabitStampGroupSelected(
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
            HistoryTags.HABIT_STAMP_PREFIX,
        onDismiss = {
            onAction(
                HistoryAction
                    .DismissHabitCalendarDay
            )
        },
    )

    if (state.showCustomRangePicker) {
        HabitCustomRangeDialog(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun HabitPerformanceCard(
    state: HabitHistoryPerformanceUiState,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    HistoryTags.habitPerformance(
                        state.habitId
                    )
                )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text = state.name,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )

                    Text(
                        text = state.schedule,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )
                }

                if (state.totalPeriods > 0) {
                    Text(
                        text =
                            (
                                    state.completionRate *
                                            100f
                                    )
                                .roundToInt()
                                .toString() +
                                    "%",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )
                }
            }

            if (state.totalPeriods == 0) {
                Text(
                    "No finished periods in " +
                            "this range."
                )
            } else {
                Text(
                    "${state.completedPeriods}/" +
                            "${state.totalPeriods} " +
                            "periods completed"
                )

                LinearProgressIndicator(
                    progress = {
                        state.completionRate
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HabitCustomRangeDialog(
    state: HabitHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val currentDate =
        requireNotNull(
            state.stampCalendar.currentDate
        )

    val customRange =
        requireNotNull(
            state.customRange
        )

    val selectableDates =
        remember(currentDate) {
            object : SelectableDates {
                override fun isSelectableDate(
                    utcTimeMillis: Long,
                ): Boolean =
                    utcTimeMillis.utcDate() <=
                            currentDate

                override fun isSelectableYear(
                    year: Int,
                ): Boolean =
                    year <= currentDate.year
            }
        }

    val pickerState =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis =
                customRange.startDate
                    .utcMillis(),
            initialSelectedEndDateMillis =
                customRange.endDate
                    .utcMillis(),
            selectableDates =
                selectableDates,
        )

    DatePickerDialog(
        onDismissRequest = {
            onAction(
                HistoryAction
                    .DismissHabitCustomRange
            )
        },
        confirmButton = {
            val start =
                pickerState
                    .selectedStartDateMillis

            val end =
                pickerState
                    .selectedEndDateMillis

            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .HABIT_RANGE_CONFIRM
                    ),
                enabled =
                    start != null &&
                            end != null,
                onClick = {
                    if (
                        start != null &&
                        end != null
                    ) {
                        onAction(
                            HistoryAction
                                .SetHabitCustomRange(
                                    HabitHistoryDateRange(
                                        startDate =
                                            start.utcDate(),
                                        endDate =
                                            end.utcDate(),
                                    )
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
                        HistoryTags
                            .HABIT_RANGE_CANCEL
                    ),
                onClick = {
                    onAction(
                        HistoryAction
                            .DismissHabitCustomRange
                    )
                },
            ) {
                Text("Cancel")
            }
        },
    ) {
        DateRangePicker(
            state = pickerState,
        )
    }
}