package com.prestonhill.questgiver.feature.history

import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.feature.tasks.TaskScheduleCalculator
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.Locale

class TaskHistoryMapper {
    fun tasks(
        tasks: List<TaskEntity>,
    ): List<HistoryTaskUiState> =
        tasks.map { task ->
            HistoryTaskUiState(
                id = task.id,
                name = task.name,
                category = task.category,
                schedule = task.scheduleText(),
                isArchived =
                    task.archivedAtEpochMillis != null,
            )
        }

    fun tasks(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        appDay: AppDay,
        currentTimestampMillis: Long,
        calculator: TaskScheduleCalculator,
        changingTaskIds: Set<Long> =
            emptySet(),
    ): List<HistoryTaskUiState> =
        tasks.map { task ->
            val evaluation =
                calculator.evaluate(
                    task = task,
                    logs = logs,
                    appDay = appDay,
                    currentTimestampMillis =
                        currentTimestampMillis,
                )

            val canChange =
                task.archivedAtEpochMillis == null &&
                        evaluation.completionEpochDay != null &&
                        (
                                evaluation.isScheduledToday ||
                                        evaluation.shouldShowToday ||
                                        evaluation.isCompleted
                                )

            HistoryTaskUiState(
                id = task.id,
                name = task.name,
                category = task.category,
                schedule = task.scheduleText(),
                completionEpochDay =
                    evaluation.completionEpochDay,
                isCompleted =
                    evaluation.isCompleted,
                canChangeCompletion =
                    canChange,
                isChanging =
                    task.id in changingTaskIds,
                isArchived =
                    task.archivedAtEpochMillis != null,
            )
        }

    fun stampCalendar(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        month: YearMonth,
        currentDate: LocalDate,
        weekStart: DayOfWeek,
    ): HistoryStampCalendarUiState {
        val activeLogs =
            activeLogs(
                logs = logs,
                tasks = tasks,
            )

        val tasksById =
            tasks.associateBy {
                it.id
            }

        val taskIdsWithCompletions =
            activeLogs.mapTo(mutableSetOf()) {
                it.taskId
            }

        val recurringFilters =
            tasks.asSequence()
                .filter {
                    it.scheduleType !=
                            TaskScheduleTypeDb.ONE_TIME
                }
                .filter {
                    it.id in taskIdsWithCompletions
                }
                .sortedWith(
                    compareBy<TaskEntity> {
                        it.name.lowercase(
                            Locale.ROOT
                        )
                    }
                        .thenBy {
                            it.id
                        }
                )
                .map { task ->
                    val key =
                        recurringTaskStampKey(
                            task.id
                        )

                    HistoryStampFilterUiState(
                        key = key,
                        label = task.name,
                        groupLabel =
                            RECURRING_TASK_GROUP,
                        colors =
                            stampColors(key),
                    )
                }
                .toList()

        /*
         * activeLogs is newest-first, so putIfAbsent
         * retains the most recently used capitalization
         * for each normalized category.
         */
        val categoryLabels =
            linkedMapOf<String, String>()

        activeLogs.forEach { log ->
            val label =
                log.categorySnapshot
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?: return@forEach

            categoryLabels.putIfAbsent(
                categoryStampKey(label),
                label,
            )
        }

        val categoryFilters =
            categoryLabels.entries
                .sortedBy {
                    it.value.lowercase(
                        Locale.ROOT
                    )
                }
                .map { (key, label) ->
                    HistoryStampFilterUiState(
                        key = key,
                        label = label,
                        groupLabel =
                            CATEGORY_GROUP,
                        colors =
                            stampColors(key),
                    )
                }

        val filters =
            recurringFilters +
                    categoryFilters

        val filterOrder =
            filters.mapIndexed {
                    index,
                    filter,
                ->
                filter.key to index
            }
                .toMap()

        val logsByDate =
            activeLogs.groupBy { log ->
                LocalDate.ofEpochDay(
                    log.scheduledEpochDay
                )
            }

        val days =
            (1..month.lengthOfMonth())
                .map { dayOfMonth ->
                    val date =
                        month.atDay(dayOfMonth)

                    val dayLogs =
                        logsByDate[date]
                            .orEmpty()

                    val keys =
                        buildSet {
                            dayLogs.forEach { log ->
                                val task =
                                    tasksById[
                                        log.taskId
                                    ]

                                if (
                                    task != null &&
                                    task.scheduleType !=
                                    TaskScheduleTypeDb
                                        .ONE_TIME
                                ) {
                                    add(
                                        recurringTaskStampKey(
                                            task.id
                                        )
                                    )
                                }

                                log.categorySnapshot
                                    ?.trim()
                                    ?.takeIf {
                                        it.isNotEmpty()
                                    }
                                    ?.let {
                                        add(
                                            categoryStampKey(
                                                it
                                            )
                                        )
                                    }
                            }
                        }
                            .sortedBy {
                                filterOrder[it]
                                    ?: Int.MAX_VALUE
                            }

                    HistoryStampCalendarDayUiState(
                        date = date,
                        stampKeys = keys,
                        isFuture =
                            date.isAfter(
                                currentDate
                            ),
                    )
                }

        return HistoryStampCalendarUiState(
            month = month,
            currentDate = currentDate,
            weekStart = weekStart,
            availableFilters = filters,
            selectedFilterKeys =
                filters.mapTo(
                    linkedSetOf()
                ) {
                    it.key
                },
            days = days,
        )
    }
}

private fun recurringTaskStampKey(
    taskId: Long,
): String =
    "task:$taskId"

private fun categoryStampKey(
    category: String,
): String =
    "category:" +
            category
                .trim()
                .lowercase(Locale.ROOT)

private fun stampColors(
    key: String,
): HistoryStampColorsUiState =
    HistoryStampColorsUiState(
        left =
            stableColorIndex(
                key = key,
                stripe = 0,
            ),
        middle =
            stableColorIndex(
                key = key,
                stripe = 1,
            ),
        right =
            stableColorIndex(
                key = key,
                stripe = 2,
            ),
    )

private fun stableColorIndex(
    key: String,
    stripe: Int,
): Int {
    var hash =
        FNV_OFFSET_BASIS

    "$stripe:$key".forEach {
            character ->
        hash =
            (hash xor character.code) *
                    FNV_PRIME
    }

    return (
            (
                    hash.toLong() and
                            UNSIGNED_INT_MASK
                    ) %
                    HistoryStampColorsUiState
                        .COLOR_COUNT
            ).toInt()
}

private const val RECURRING_TASK_GROUP =
    "Recurring tasks"

private const val CATEGORY_GROUP =
    "Categories"

private const val FNV_OFFSET_BASIS =
    -2_128_830_103

private const val FNV_PRIME =
    16_777_619

private const val UNSIGNED_INT_MASK =
    0xFFFF_FFFFL

private data class TaskLogSlot(
    val taskId: Long,
    val scheduledEpochDay: Long? = null,
)

private fun activeLogs(
    logs: List<TaskLogEntity>,
    tasks: List<TaskEntity>,
): List<TaskLogEntity> {
    val correctedIds =
        logs.asSequence()
            .filter {
                it.delta == -1
            }
            .mapNotNull {
                it.reversesLogId
            }
            .toSet()

    val tasksById =
        tasks.associateBy {
            it.id
        }

    return logs.asSequence()
        .filter { log ->
            log.delta == 1 &&
                    log.id !in correctedIds
        }
        .sortedWith(
            compareByDescending<
                    TaskLogEntity
                    > {
                it.completionTimestampMillis
            }
                .thenByDescending {
                    it.id
                }
        )
        .distinctBy { log ->
            val task =
                tasksById[log.taskId]

            if (
                task?.scheduleType ==
                TaskScheduleTypeDb.ONE_TIME
            ) {
                TaskLogSlot(
                    taskId = log.taskId
                )
            } else {
                TaskLogSlot(
                    taskId = log.taskId,
                    scheduledEpochDay =
                        log.scheduledEpochDay,
                )
            }
        }
        .toList()
}

private fun TaskEntity.scheduleText(): String =
    when (scheduleType) {
        TaskScheduleTypeDb.ONE_TIME -> {
            val date =
                scheduledEpochDay?.let(
                    LocalDate::ofEpochDay
                )

            if (date == null) {
                "One time · Anytime"
            } else {
                "One time · $date"
            }
        }

        TaskScheduleTypeDb.DAILY ->
            "Daily"

        TaskScheduleTypeDb.WEEKLY_DAYS ->
            weeklyText(weekdaysMask ?: 0)

        TaskScheduleTypeDb.INTERVAL -> {
            val days = intervalDays ?: 1

            if (
                intervalBasis ==
                TaskIntervalBasisDb.FROM_COMPLETION
            ) {
                "Every $days days after completion"
            } else {
                "Every $days days"
            }
        }
    }

private fun weeklyText(mask: Int): String {
    val labels =
        listOf(
            "Mon",
            "Tue",
            "Wed",
            "Thu",
            "Fri",
            "Sat",
            "Sun",
        )

    val selected =
        labels.filterIndexed { index, _ ->
            mask and (1 shl index) != 0
        }

    return selected.joinToString(
        prefix = "Weekly · ",
        separator = ", ",
    )
}