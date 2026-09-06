@file:OptIn(
    androidx.compose.material3
        .ExperimentalMaterial3Api::class
)
package com.prestonhill.questgiver.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prestonhill.questgiver.feature.nutrition.nutritionAmountText
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
internal fun NutritionHistoryDashboard(
    state: NutritionHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val selectedRange =
        state.selectedRange

    if (selectedRange == null) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        return
    }

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
                        .NUTRITION_DASHBOARD
                ),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Nutrition history",
                style =
                    MaterialTheme
                        .typography.headlineSmall,
            )
        }

        item {
            NutritionGoalProgressCard(state)
        }

        item {
            NutritionGoalCalendar(
                state = state,
                onAction = onAction,
            )
        }

        item {
            LazyRow(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .NUTRITION_RANGE_LIST
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items =
                        NutritionHistoryRangePreset
                            .entries,
                    key = {
                        it.name
                    },
                ) { preset ->
                    FilterChip(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .nutritionRange(
                                        preset
                                    )
                            ),
                        selected =
                            state.rangePreset ==
                                    preset,
                        onClick = {
                            if (
                                preset ==
                                NutritionHistoryRangePreset
                                    .CUSTOM
                            ) {
                                onAction(
                                    HistoryAction
                                        .OpenNutritionCustomRange
                                )
                            } else {
                                onAction(
                                    HistoryAction
                                        .SelectNutritionRange(
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

            state.selectedCalendarDate
                ?.let { selectedDate ->
                    state.calendarDays
                        .firstOrNull {
                            it.date == selectedDate
                        }
                }
                ?.let { day ->
                    NutritionCalendarDayDialog(
                        day = day,
                        selectedTypes =
                            state.selectedStampTypes,
                        onDismiss = {
                            onAction(
                                HistoryAction
                                    .DismissNutritionCalendarDay
                            )
                        },
                    )
                }
        }

        item {
            Text(
                selectedRange.startDate
                    .format(dateFormatter) +
                        " – " +
                        selectedRange.endDate
                            .format(dateFormatter),
                style =
                    MaterialTheme
                        .typography.bodyMedium,
            )
        }

        if (
            state.calorieStatistics
                .loggedDays == 0
        ) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            "No nutrition was logged " +
                                    "during this range.",
                        modifier =
                            Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                NutritionMetricCard(
                    title = "Calories",
                    statistics =
                        state
                            .calorieStatistics,
                    unit = "kcal",
                    tag =
                        HistoryTags
                            .NUTRITION_CALORIE_STATS,
                )
            }

            item {
                NutritionMetricCard(
                    title = "Protein",
                    statistics =
                        state
                            .proteinStatistics,
                    unit = "g",
                    tag =
                        HistoryTags
                            .NUTRITION_PROTEIN_STATS,
                )
            }

            item {
                NutritionHistoryChart(
                    title = "Daily calories",
                    days = state.selectedDays,
                    value = {
                        it.calories
                    },
                    minimumGoal =
                        state.calorieGoal,
                    maximumGoal =
                        state.maximumCalorieGoal,
                    unit = "kcal",
                    tag =
                        HistoryTags
                            .NUTRITION_CALORIE_CHART,
                )
            }

            item {
                NutritionHistoryChart(
                    title = "Daily protein",
                    days = state.selectedDays,
                    value = {
                        it.proteinGrams
                    },
                    minimumGoal =
                        state.proteinGoalGrams,
                    maximumGoal =
                        state.maximumProteinGoalGrams,
                    unit = "g",
                    tag =
                        HistoryTags
                            .NUTRITION_PROTEIN_CHART,
                )
            }
        }
    }

    if (state.showCustomRangePicker) {
        NutritionCustomRangeDialog(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun NutritionGoalProgressCard(
    state: NutritionHistoryUiState,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    HistoryTags
                        .NUTRITION_GOAL_PROGRESS
                )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Goal completion",
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            NutritionGoalProgress(
                label = "Calories · current month",
                completion =
                    state.currentMonthCalories,
                color = CALORIE_STAMP_COLOR,
            )

            NutritionGoalProgress(
                label = "Calories · custom range",
                completion =
                    state.customRangeCalories,
                color = CALORIE_STAMP_COLOR,
            )

            NutritionGoalProgress(
                label = "Protein · current month",
                completion =
                    state.currentMonthProtein,
                color = PROTEIN_STAMP_COLOR,
            )

            NutritionGoalProgress(
                label = "Protein · custom range",
                completion =
                    state.customRangeProtein,
                color = PROTEIN_STAMP_COLOR,
            )
        }
    }
}

@Composable
private fun NutritionGoalProgress(
    label: String,
    completion:
    NutritionGoalCompletionUiState,
    color: Color,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme
                        .typography.labelLarge,
            )

            Text(
                "${completion.metDays}/" +
                        "${completion.totalDays} · " +
                        "${(completion.progress * 100f).roundToInt()}%"
            )
        }

        LinearProgressIndicator(
            progress = {
                completion.progress
            },
            modifier =
                Modifier.fillMaxWidth(),
            color = color,
        )
    }
}

@Composable
private fun NutritionGoalCalendar(
    state: NutritionHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val month =
        state.calendarMonth ?: return

    val currentDate =
        state.currentDate ?: return

    val firstDay =
        month.atDay(1)

    val leadingEmptyDays =
        (
                firstDay.dayOfWeek.value -
                        state.calendarWeekStart.value +
                        7
                ) % 7

    val cells =
        buildList<
                NutritionHistoryDayUiState?
                > {
            repeat(leadingEmptyDays) {
                add(null)
            }

            addAll(
                state.calendarDays
                    .sortedBy {
                        it.date
                    }
            )

            while (size % 7 != 0) {
                add(null)
            }
        }

    val weekdays =
        (0L..6L).map {
            state.calendarWeekStart.plus(it)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    HistoryTags
                        .NUTRITION_CALENDAR
                )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                TextButton(
                    modifier =
                        Modifier.testTag(
                            HistoryTags
                                .NUTRITION_CALENDAR_PREVIOUS
                        ),
                    onClick = {
                        onAction(
                            HistoryAction
                                .PreviousNutritionMonth
                        )
                    },
                ) {
                    Text("Previous")
                }

                Text(
                    text =
                        month.format(
                            DateTimeFormatter
                                .ofPattern(
                                    "MMMM yyyy",
                                    LocalLocale.current.platformLocale,
                                )
                        ),
                    style =
                        MaterialTheme
                            .typography.titleMedium,
                )

                TextButton(
                    modifier =
                        Modifier.testTag(
                            HistoryTags
                                .NUTRITION_CALENDAR_NEXT
                        ),
                    enabled =
                        month.isBefore(
                            YearMonth.from(
                                currentDate
                            )
                        ),
                    onClick = {
                        onAction(
                            HistoryAction
                                .NextNutritionMonth
                        )
                    },
                ) {
                    Text("Next")
                }
            }

            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .NUTRITION_STAMP_ALL
                            ),
                        selected =
                            state.selectedStampTypes
                                .size ==
                                    NutritionStampType
                                        .entries.size,
                        onClick = {
                            onAction(
                                HistoryAction
                                    .SelectAllNutritionStamps
                            )
                        },
                        label = {
                            Text("All")
                        },
                    )
                }

                items(
                    items =
                        NutritionStampType.entries,
                    key = {
                        it.name
                    },
                ) { type ->
                    FilterChip(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .nutritionStampFilter(
                                        type
                                    )
                            ),
                        selected =
                            type in
                                    state.selectedStampTypes,
                        onClick = {
                            onAction(
                                HistoryAction
                                    .ToggleNutritionStamp(
                                        type
                                    )
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        6.dp
                                    ),
                            ) {
                                StampCircle(
                                    color =
                                        type.historyColor()
                                )

                                Text(type.label)
                            }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                weekdays.forEach { weekday ->
                    Text(
                        text =
                            weekday.getDisplayName(
                                java.time.format
                                    .TextStyle.SHORT,
                                LocalLocale.current.platformLocale,
                            ),
                        modifier =
                            Modifier.weight(1f),
                        style =
                            MaterialTheme
                                .typography.labelSmall,
                    )
                }
            }

            cells.chunked(7)
                .forEach { week ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                )
                            } else {
                                NutritionCalendarDayCell(
                                    modifier =
                                        Modifier.weight(1f),
                                    day = day,
                                    selectedTypes =
                                        state
                                            .selectedStampTypes,
                                    onClick = {
                                        onAction(
                                            HistoryAction
                                                .OpenNutritionCalendarDay(
                                                    day.date
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
}

@Composable
private fun RowScope.NutritionCalendarDayCell(
    modifier: Modifier,
    day: NutritionHistoryDayUiState,
    selectedTypes:
    Set<NutritionStampType>,
    onClick: () -> Unit,
) {
    val stamps =
        day.visibleStamps(selectedTypes)

    val clickableModifier =
        if (stamps.isEmpty()) {
            Modifier
        } else {
            Modifier.clickable(
                onClick = onClick
            )
        }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .background(
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(
                            alpha =
                                if (day.isFuture) {
                                    0.25f
                                } else {
                                    0.55f
                                }
                        )
                )
                .testTag(
                    HistoryTags
                        .nutritionCalendarDay(
                            day.date
                        )
                )
                .then(clickableModifier)
    ) {
        Canvas(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(
                        start = 3.dp,
                        top = 18.dp,
                        end = 3.dp,
                        bottom = 3.dp,
                    )
        ) {
            val radius =
                STAMP_RADIUS.toPx()

            stamps.forEachIndexed {
                    index,
                    type,
                ->
                val random =
                    Random(
                        stampSeed(
                            date = day.date,
                            type = type,
                            index = index,
                        )
                    )

                val availableWidth =
                    (
                            size.width -
                                    radius * 2f
                            ).coerceAtLeast(0f)

                val availableHeight =
                    (
                            size.height -
                                    radius * 2f
                            ).coerceAtLeast(0f)

                drawCircle(
                    color = type.historyColor(),
                    radius = radius,
                    center =
                        Offset(
                            x =
                                radius +
                                        random.nextFloat() *
                                        availableWidth,
                            y =
                                radius +
                                        random.nextFloat() *
                                        availableHeight,
                        ),
                )
            }
        }

        Text(
            text =
                day.date.dayOfMonth.toString(),
            modifier =
                Modifier.padding(4.dp),
            style =
                MaterialTheme
                    .typography.labelMedium,
            color =
                if (day.isFuture) {
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                        .copy(alpha = 0.4f)
                } else {
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                },
        )
    }
}

private fun NutritionHistoryDayUiState
        .visibleStamps(
    selectedTypes:
    Set<NutritionStampType>,
): List<NutritionStampType> =
    buildList {
        if (
            calorieGoalMet &&
            NutritionStampType.CALORIES in
            selectedTypes
        ) {
            add(NutritionStampType.CALORIES)
        }

        if (
            proteinGoalMet &&
            NutritionStampType.PROTEIN in
            selectedTypes
        ) {
            add(NutritionStampType.PROTEIN)
        }
    }

private fun stampSeed(
    date: LocalDate,
    type: NutritionStampType,
    index: Int,
): Int {
    var result =
        date.toEpochDay().hashCode()

    result =
        31 * result +
                type.name.hashCode()

    result =
        31 * result + index

    return result
}

@Composable
private fun NutritionCalendarDayDialog(
    day: NutritionHistoryDayUiState,
    selectedTypes:
    Set<NutritionStampType>,
    onDismiss: () -> Unit,
) {
    val stamps =
        day.visibleStamps(selectedTypes)

    val formatter =
        remember {
            DateTimeFormatter
                .ofLocalizedDate(
                    FormatStyle.FULL
                )
                .withLocale(
                    Locale.getDefault()
                )
        }

    AlertDialog(
        modifier =
            Modifier.testTag(
                HistoryTags
                    .NUTRITION_DAY_DIALOG
            ),
        onDismissRequest = onDismiss,
        title = {
            Text(
                day.date.format(formatter)
            )
        },
        text = {
            LazyColumn(
                modifier =
                    Modifier.heightIn(
                        max = 320.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = stamps,
                    key = {
                        it.name
                    },
                ) { type ->
                    Row(
                        modifier =
                            Modifier.testTag(
                                HistoryTags
                                    .nutritionDayStamp(
                                        type
                                    )
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),
                    ) {
                        StampCircle(
                            color =
                                type.historyColor()
                        )

                        Text(
                            "${type.label} goal met"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        HistoryTags
                            .NUTRITION_DAY_CLOSE
                    ),
                onClick = onDismiss,
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun StampCircle(
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(STAMP_DIAMETER)
                .background(
                    color = color,
                    shape = CircleShape,
                )
    )
}

private fun NutritionStampType.historyColor():
        Color =
    when (this) {
        NutritionStampType.CALORIES ->
            CALORIE_STAMP_COLOR

        NutritionStampType.PROTEIN ->
            PROTEIN_STAMP_COLOR
    }

private val CALORIE_STAMP_COLOR =
    Color(0xFF1976D2)

private val PROTEIN_STAMP_COLOR =
    Color(0xFF2E7D32)

private val STAMP_DIAMETER = 16.dp
private val STAMP_RADIUS = 8.dp

@Composable
private fun NutritionHistoryChart(
    title: String,
    days:
    List<NutritionHistoryDayUiState>,
    value:
        (NutritionHistoryDayUiState) ->
    Double,
    minimumGoal: Double,
    maximumGoal: Double?,
    unit: String,
    tag: String,
) {
    val loggedPoints =
        days.mapIndexedNotNull {
                index,
                day,
            ->
            if (day.hasLogs) {
                index to value(day)
            } else {
                null
            }
        }

    val highestValue =
        loggedPoints.maxOfOrNull {
            it.second
        } ?: 0.0

    val chartMaximum =
        nutritionChartMaximum(
            maxOf(
                highestValue,
                minimumGoal,
                maximumGoal ?: 0.0,
            )
        )

    val lineColor =
        MaterialTheme
            .colorScheme.primary

    val goalColor =
        MaterialTheme
            .colorScheme.tertiary

    val axisColor =
        MaterialTheme
            .colorScheme.outlineVariant

    val dateFormatter =
        remember {
            DateTimeFormatter.ofPattern(
                "MMM d",
                Locale.getDefault(),
            )
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics {
                    contentDescription =
                        "$title chart with " +
                                "${loggedPoints.size} " +
                                "logged days"
                },
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                "Goal: " +
                        historyGoalText(
                            minimum =
                                minimumGoal,
                            maximum =
                                maximumGoal,
                            unit = unit,
                        ),
                style =
                    MaterialTheme
                        .typography.bodySmall,
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(56.dp)
                            .height(180.dp),
                    verticalArrangement =
                        Arrangement.SpaceBetween,
                    horizontalAlignment =
                        Alignment.End,
                ) {
                    Text(
                        "${nutritionAmountText(chartMaximum)} $unit",
                        style =
                            MaterialTheme
                                .typography.labelSmall,
                    )

                    Text(
                        "0 $unit",
                        style =
                            MaterialTheme
                                .typography.labelSmall,
                    )
                }

                Canvas(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(180.dp)
                            .padding(
                                start = 8.dp
                            )
                ) {
                    val horizontalPadding =
                        6.dp.toPx()

                    val verticalPadding =
                        6.dp.toPx()

                    val plotWidth =
                        size.width -
                                horizontalPadding * 2f

                    val plotHeight =
                        size.height -
                                verticalPadding * 2f

                    fun xFor(
                        index: Int,
                    ): Float =
                        if (days.size <= 1) {
                            size.width / 2f
                        } else {
                            horizontalPadding +
                                    (
                                            index.toFloat() /
                                                    (
                                                            days.size -
                                                                    1
                                                            ).toFloat()
                                            ) *
                                    plotWidth
                        }

                    fun yFor(
                        amount: Double,
                    ): Float {
                        val fraction =
                            (
                                    amount /
                                            chartMaximum
                                    )
                                .toFloat()
                                .coerceIn(
                                    0f,
                                    1f,
                                )

                        return verticalPadding +
                                (
                                        1f - fraction
                                        ) *
                                plotHeight
                    }

                    drawLine(
                        color = axisColor,
                        start =
                            Offset(
                                horizontalPadding,
                                yFor(0.0),
                            ),
                        end =
                            Offset(
                                size.width -
                                        horizontalPadding,
                                yFor(0.0),
                            ),
                        strokeWidth =
                            1.dp.toPx(),
                    )

                    val dashed =
                        PathEffect
                            .dashPathEffect(
                                floatArrayOf(
                                    10.dp.toPx(),
                                    6.dp.toPx(),
                                )
                            )

                    drawLine(
                        color = goalColor,
                        start =
                            Offset(
                                horizontalPadding,
                                yFor(
                                    minimumGoal
                                ),
                            ),
                        end =
                            Offset(
                                size.width -
                                        horizontalPadding,
                                yFor(
                                    minimumGoal
                                ),
                            ),
                        strokeWidth =
                            2.dp.toPx(),
                        pathEffect = dashed,
                    )

                    maximumGoal?.let {
                            maximum,
                        ->
                        drawLine(
                            color = goalColor,
                            start =
                                Offset(
                                    horizontalPadding,
                                    yFor(maximum),
                                ),
                            end =
                                Offset(
                                    size.width -
                                            horizontalPadding,
                                    yFor(maximum),
                                ),
                            strokeWidth =
                                2.dp.toPx(),
                            pathEffect =
                                dashed,
                        )
                    }

                    val offsets =
                        loggedPoints.map {
                                (index, amount),
                            ->
                            Offset(
                                x = xFor(index),
                                y = yFor(amount),
                            )
                        }

                    if (offsets.size > 1) {
                        val path =
                            Path().apply {
                                moveTo(
                                    offsets.first().x,
                                    offsets.first().y,
                                )

                                offsets
                                    .drop(1)
                                    .forEach {
                                            point ->
                                        lineTo(
                                            point.x,
                                            point.y,
                                        )
                                    }
                            }

                        drawPath(
                            path = path,
                            color = lineColor,
                            style =
                                Stroke(
                                    width =
                                        3.dp.toPx(),
                                    cap =
                                        StrokeCap.Round,
                                ),
                        )
                    }

                    offsets.forEach { point ->
                        drawCircle(
                            color = lineColor,
                            radius =
                                4.dp.toPx(),
                            center = point,
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 64.dp
                        ),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    days.firstOrNull()
                        ?.date
                        ?.format(
                            dateFormatter
                        )
                        .orEmpty(),
                    style =
                        MaterialTheme
                            .typography.labelSmall,
                )

                Text(
                    days.lastOrNull()
                        ?.date
                        ?.format(
                            dateFormatter
                        )
                        .orEmpty(),
                    style =
                        MaterialTheme
                            .typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun NutritionMetricCard(
    title: String,
    statistics:
    NutritionHistoryMetricUiState,
    unit: String,
    tag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                "Logged days: " +
                        statistics.loggedDays
            )

            MetricRow(
                label =
                    "Daily average",
                value =
                    metricText(
                        statistics.average,
                        unit,
                    ),
            )

            MetricRow(
                label =
                    "Minimum (nonzero)",
                value =
                    metricText(
                        statistics
                            .minimumNonZero,
                        unit,
                    ),
            )

            MetricRow(
                label = "Maximum",
                value =
                    metricText(
                        statistics.maximum,
                        unit,
                    ),
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun NutritionCustomRangeDialog(
    state: NutritionHistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val currentDate =
        requireNotNull(
            state.currentDate
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
                    utcTimeMillis
                        .utcDate() <=
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
                    .DismissNutritionCustomRange
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
                            .NUTRITION_RANGE_CONFIRM
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
                                .SetNutritionCustomRange(
                                    NutritionHistoryDateRange(
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
                            .NUTRITION_RANGE_CANCEL
                    ),
                onClick = {
                    onAction(
                        HistoryAction
                            .DismissNutritionCustomRange
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

private fun historyGoalText(
    minimum: Double,
    maximum: Double?,
    unit: String,
): String =
    if (maximum == null) {
        "${nutritionAmountText(minimum)}+ $unit"
    } else {
        "${nutritionAmountText(minimum)}–" +
                "${nutritionAmountText(maximum)} $unit"
    }

private fun nutritionChartMaximum(
    highestValue: Double,
): Double {
    if (
        !highestValue.isFinite() ||
        highestValue <= 0.0
    ) {
        return 1.0
    }

    val step =
        when {
            highestValue >= 1_000.0 ->
                500.0

            highestValue >= 100.0 ->
                50.0

            highestValue >= 10.0 ->
                5.0

            else -> 1.0
        }

    return ceil(
        highestValue * 1.05 / step
    ) * step
}

private fun metricText(
    value: Double?,
    unit: String,
): String =
    value?.let {
        "${nutritionAmountText(it)} $unit"
    } ?: "—"