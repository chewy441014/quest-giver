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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random
import androidx.compose.ui.platform.LocalLocale

@Composable
internal fun HistoryStampCalendarCard(
    state: HistoryStampCalendarUiState,
    tagPrefix: String,
    title: String,
    emptyMessage: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleFilter: (String) -> Unit,
    onSelectAll: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onSetGroupSelected: (groupLabel: String, selected: Boolean) -> Unit,
) {
    val month = state.month
    val currentDate = state.currentDate

    if (month == null || currentDate == null) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "${tagPrefix}_calendar"
                    )
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        return
    }

    val firstDay = month.atDay(1)

    val leadingEmptyDays =
        (
                firstDay.dayOfWeek.value -
                        state.weekStart.value +
                        7
                ) % 7

    val cells =
        buildList<
                HistoryStampCalendarDayUiState?
                > {
            repeat(leadingEmptyDays) {
                add(null)
            }

            addAll(
                state.days.sortedBy {
                    it.date
                }
            )

            while (size % 7 != 0) {
                add(null)
            }
        }

    val weekdays =
        (0L..6L).map {
            state.weekStart.plus(it)
        }

    val filtersByKey =
        state.availableFilters
            .associateBy {
                it.key
            }

    val groupedFilters =
        state.availableFilters
            .groupBy {
                it.groupLabel
            }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "${tagPrefix}_calendar"
                )
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography.titleMedium,
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                TextButton(
                    modifier =
                        Modifier.testTag(
                            "${tagPrefix}_previous"
                        ),
                    onClick = onPreviousMonth,
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
                            "${tagPrefix}_next"
                        ),
                    enabled =
                        month.isBefore(
                            YearMonth.from(
                                currentDate
                            )
                        ),
                    onClick = onNextMonth,
                ) {
                    Text("Next")
                }
            }

            if (
                state.availableFilters.isEmpty()
            ) {
                Text(
                    text = emptyMessage,
                    style =
                        MaterialTheme
                            .typography.bodyMedium,
                )
            } else {
                FilterChip(
                    modifier =
                        Modifier.testTag(
                            "${tagPrefix}_all"
                        ),
                    selected =
                        state.availableFilters
                            .all {
                                it.key in
                                        state.selectedFilterKeys
                            },
                    onClick = onSelectAll,
                    label = {
                        Text("All")
                    },
                )

                groupedFilters.forEach {
                        (group, filters),
                    ->
                    val entireGroupSelected =
                        filters.all {
                            it.key in
                                    state.selectedFilterKeys
                        }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            text = group,
                            modifier =
                                Modifier.weight(1f),
                            style =
                                MaterialTheme
                                    .typography.labelLarge,
                        )

                        Switch(
                            modifier =
                                Modifier.testTag(
                                    "${tagPrefix}_" +
                                            "group_$group"
                                ),
                            checked =
                                entireGroupSelected,
                            onCheckedChange = { selected ->
                                onSetGroupSelected(
                                    group,
                                    selected,
                                )
                            },
                        )
                    }

                    LazyRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        items(
                            items = filters,
                            key = {
                                it.key
                            },
                        ) { filter ->
                            FilterChip(
                                modifier =
                                    Modifier.testTag(
                                        "${tagPrefix}_" +
                                                "filter_" +
                                                filter.key
                                    ),
                                selected =
                                    filter.key in
                                            state
                                                .selectedFilterKeys,
                                onClick = {
                                    onToggleFilter(
                                        filter.key
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
                                        ThreeStripeStamp(
                                            colors =
                                                filter.colors
                                        )

                                        Text(filter.label)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
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
                                HistoryStampDayCell(
                                    modifier =
                                        Modifier.weight(1f),
                                    day = day,
                                    selectedKeys =
                                        state
                                            .selectedFilterKeys,
                                    filtersByKey =
                                        filtersByKey,
                                    tagPrefix =
                                        tagPrefix,
                                    onClick = {
                                        onOpenDay(
                                            day.date
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
private fun RowScope.HistoryStampDayCell(
    modifier: Modifier,
    day: HistoryStampCalendarDayUiState,
    selectedKeys: Set<String>,
    filtersByKey:
    Map<String, HistoryStampFilterUiState>,
    tagPrefix: String,
    onClick: () -> Unit,
) {
    val visibleStamps =
        day.stampKeys
            .filter {
                it in selectedKeys
            }
            .mapNotNull {
                filtersByKey[it]
            }

    val clickModifier =
        if (visibleStamps.isEmpty()) {
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
                    "${tagPrefix}_day_${day.date}"
                )
                .then(clickModifier)
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

            visibleStamps.forEach {
                    stamp,
                ->
                val random =
                    Random(
                        stampPositionSeed(
                            date = day.date,
                            key = stamp.key,
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

                drawThreeStripeStamp(
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
                    radius = radius,
                    colors = stamp.colors,
                )
            }
        }

        Text(
            text =
                day.date.dayOfMonth
                    .toString(),
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

@Composable
internal fun HistoryStampCalendarDayDialog(
    state: HistoryStampCalendarUiState,
    tagPrefix: String,
    onDismiss: () -> Unit,
) {
    val selectedDate =
        state.selectedDate ?: return

    val day =
        state.days.firstOrNull {
            it.date == selectedDate
        } ?: return

    val filtersByKey =
        state.availableFilters
            .associateBy {
                it.key
            }

    val visibleStamps =
        day.stampKeys
            .filter {
                it in state.selectedFilterKeys
            }
            .mapNotNull {
                filtersByKey[it]
            }

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
                "${tagPrefix}_day_dialog"
            ),
        onDismissRequest = onDismiss,
        title = {
            Text(
                selectedDate.format(
                    formatter
                )
            )
        },
        text = {
            if (visibleStamps.isEmpty()) {
                Text(
                    "No selected stamps " +
                            "for this day."
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier.heightIn(
                            max = 320.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                ) {
                    items(
                        items = visibleStamps,
                        key = {
                            it.key
                        },
                    ) { stamp ->
                        Row(
                            modifier =
                                Modifier.testTag(
                                    "${tagPrefix}_" +
                                            "day_stamp_" +
                                            stamp.key
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                        ) {
                            ThreeStripeStamp(
                                colors =
                                    stamp.colors
                            )

                            Column {
                                Text(stamp.label)

                                Text(
                                    stamp.groupLabel,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier =
                    Modifier.testTag(
                        "${tagPrefix}_" +
                                "day_close"
                    ),
                onClick = onDismiss,
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ThreeStripeStamp(
    colors: HistoryStampColorsUiState,
) {
    Canvas(
        modifier =
            Modifier
                .size(STAMP_DIAMETER)
                .background(
                    color = Color.Transparent,
                    shape = CircleShape,
                )
    ) {
        drawThreeStripeStamp(
            center =
                Offset(
                    x = size.width / 2f,
                    y = size.height / 2f,
                ),
            radius =
                minOf(
                    size.width,
                    size.height,
                ) / 2f,
            colors = colors,
        )
    }
}

private fun DrawScope.drawThreeStripeStamp(
    center: Offset,
    radius: Float,
    colors: HistoryStampColorsUiState,
) {
    val diameter = radius * 2f

    val bounds =
        Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius,
        )

    val circle =
        Path().apply {
            addOval(bounds)
        }

    val stripeWidth =
        diameter / 3f

    clipPath(circle) {
        drawRect(
            color =
                stampColor(colors.left),
            topLeft =
                Offset(
                    bounds.left,
                    bounds.top,
                ),
            size =
                Size(
                    stripeWidth + 1f,
                    diameter,
                ),
        )

        drawRect(
            color =
                stampColor(colors.middle),
            topLeft =
                Offset(
                    bounds.left +
                            stripeWidth,
                    bounds.top,
                ),
            size =
                Size(
                    stripeWidth + 1f,
                    diameter,
                ),
        )

        drawRect(
            color =
                stampColor(colors.right),
            topLeft =
                Offset(
                    bounds.left +
                            stripeWidth * 2f,
                    bounds.top,
                ),
            size =
                Size(
                    stripeWidth + 1f,
                    diameter,
                ),
        )
    }
}

private fun stampPositionSeed(
    date: LocalDate,
    key: String,
): Int {
    var result =
        date.toEpochDay().hashCode()

    result =
        31 * result + key.hashCode()

    return result
}

private fun stampColor(
    index: Int,
): Color =
    STAMP_COLORS[index]

private val STAMP_COLORS =
    listOf(
        Color(0xFF1976D2),
        Color(0xFF2E7D32),
        Color(0xFFF9A825),
        Color(0xFFEF6C00),
        Color(0xFFC62828),
        Color(0xFF6A1B9A),
        Color(0xFFAD1457),

        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFFFF176),
        Color(0xFFFFB74D),
        Color(0xFFE57373),
        Color(0xFFBA68C8),
        Color(0xFFF06292),

        Color(0xFFBBDEFB),
        Color(0xFFC8E6C9),
        Color(0xFFFFF9C4),
        Color(0xFFFFE0B2),
        Color(0xFFFFCDD2),
        Color(0xFFE1BEE7),
        Color(0xFFF8BBD0),
    )

private val STAMP_DIAMETER = 16.dp
private val STAMP_RADIUS = 8.dp