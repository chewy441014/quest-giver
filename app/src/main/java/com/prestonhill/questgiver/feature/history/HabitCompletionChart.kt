package com.prestonhill.questgiver.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
internal fun HabitCompletionChartCard(
    state: HabitCompletionChartUiState,
    onToggleHabit: (Long) -> Unit,
    onSelectAll: () -> Unit,
) {
    val visibleSeries =
        state.visibleSeries

    val hasChartData =
        visibleSeries.any { series ->
            series.points.any {
                it.completionCount != null
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    HistoryTags
                        .HABIT_COMPLETION_CHART
                )
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Daily multi-completions",
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            when {
                state.series.isEmpty() ->
                    Text(
                        "No multi-completion habits " +
                                "are available."
                    )

                else -> {
                    LazyRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        item {
                            FilterChip(
                                modifier =
                                    Modifier.testTag(
                                        HistoryTags
                                            .HABIT_COMPLETION_CHART_ALL
                                    ),
                                selected =
                                    state.series.all {
                                        it.habitId in
                                                state
                                                    .selectedHabitIds
                                    },
                                onClick = onSelectAll,
                                label = {
                                    Text("All")
                                },
                            )
                        }

                        items(
                            items = state.series,
                            key = {
                                it.habitId
                            },
                        ) { series ->
                            FilterChip(
                                modifier =
                                    Modifier.testTag(
                                        HistoryTags
                                            .habitCompletionChartFilter(
                                                series.habitId
                                            )
                                    ),
                                selected =
                                    series.habitId in
                                            state
                                                .selectedHabitIds,
                                onClick = {
                                    onToggleHabit(
                                        series.habitId
                                    )
                                },
                                label = {
                                    Row(
                                        verticalAlignment =
                                            Alignment
                                                .CenterVertically,
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                6.dp
                                            ),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(
                                                        10.dp
                                                    )
                                                    .background(
                                                        color =
                                                            historyColor(
                                                                series
                                                                    .colorIndex
                                                            ),
                                                        shape =
                                                            CircleShape,
                                                    )
                                        )

                                        Text(series.name)
                                    }
                                },
                            )
                        }
                    }

                    if (!hasChartData) {
                        Text(
                            "No multi-completion habit " +
                                    "data for this range."
                        )
                    } else {
                        HabitCompletionChart(
                            state = state
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCompletionChart(
    state: HabitCompletionChartUiState,
) {
    val visibleSeries =
        state.visibleSeries

    val maximum =
        visibleSeries
            .asSequence()
            .flatMap {
                it.points.asSequence()
            }
            .mapNotNull {
                it.completionCount
            }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1

    val gridColor =
        MaterialTheme
            .colorScheme
            .outlineVariant

    val formatter =
        DateTimeFormatter.ofPattern(
            "MMM d",
            LocalLocale.current.platformLocale,
        )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {
            Text(
                text = maximum.toString(),
                style =
                    MaterialTheme
                        .typography.labelSmall,
            )

            Text(
                text = "completions",
                style =
                    MaterialTheme
                        .typography.labelSmall,
            )
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .testTag(
                        HistoryTags
                            .HABIT_COMPLETION_PLOT
                    )
        ) {
            val width =
                size.width.coerceAtLeast(1f)

            val height =
                size.height.coerceAtLeast(1f)

            listOf(
                0f,
                0.5f,
                1f,
            ).forEach { fraction ->
                val y =
                    height -
                            height * fraction

                drawLine(
                    color = gridColor,
                    start =
                        Offset(
                            x = 0f,
                            y = y,
                        ),
                    end =
                        Offset(
                            x = width,
                            y = y,
                        ),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val denominator =
                (
                        state.dates.size - 1
                        )
                    .coerceAtLeast(1)

            visibleSeries.forEach { series ->
                val color =
                    historyColor(
                        series.colorIndex
                    )

                var previous:
                        Offset? = null

                series.points
                    .forEachIndexed {
                            index,
                            point,
                        ->
                        val count =
                            point.completionCount

                        if (count == null) {
                            previous = null
                            return@forEachIndexed
                        }

                        val offset =
                            Offset(
                                x =
                                    width *
                                            index.toFloat() /
                                            denominator,
                                y =
                                    height -
                                            height *
                                            count.toFloat() /
                                            maximum.toFloat(),
                            )

                        previous?.let {
                            drawLine(
                                color = color,
                                start = it,
                                end = offset,
                                strokeWidth =
                                    2.dp.toPx(),
                            )
                        }

                        drawCircle(
                            color = color,
                            radius = 2.dp.toPx(),
                            center = offset,
                        )

                        previous = offset
                    }
            }
        }

        if (state.dates.isNotEmpty()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        state.dates
                            .first()
                            .format(formatter),
                    style =
                        MaterialTheme
                            .typography.labelSmall,
                )

                Text(
                    text =
                        state.dates
                            .last()
                            .format(formatter),
                    style =
                        MaterialTheme
                            .typography.labelSmall,
                )
            }
        }
    }
}