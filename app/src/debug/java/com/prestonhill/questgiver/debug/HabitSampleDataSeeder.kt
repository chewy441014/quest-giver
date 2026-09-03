package com.prestonhill.questgiver.debug

import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.HabitCategoryDb
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitIntervalBasisDb
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleVisibilityDb
import com.prestonhill.questgiver.data.repository.CompletionChangeResult
import com.prestonhill.questgiver.data.repository.HabitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class HabitSeedResult(
    val habits: Int,
    val logs: Int,
)

class HabitSampleDataSeeder(
    database: QuestGiverDatabase,
    private val clock: Clock,
) {
    private val repository =
        HabitRepository(database)

    suspend fun seed(
        currentDate: LocalDate,
        calculator: AppDayCalculator,
    ): HabitSeedResult {
        val earliestDate =
            currentDate.minusDays(419)

        val createdAt =
            calculator
                .forDate(earliestDate)
                .startTimestampMillis

        var habitCount = 0
        var logCount = 0

        suspend fun create(
            name: String,
            category: HabitCategoryDb,
            scheduleType:
            HabitScheduleTypeDb,
            scheduleTarget: Int = 1,
            allowsMultiple:
            Boolean = false,
            intervalDays: Int? = null,
            intervalBasis:
            HabitIntervalBasisDb? = null,
            fixedAnchor:
            LocalDate? = null,
            visibility:
            HabitScheduleVisibilityDb =
                HabitScheduleVisibilityDb
                    .ALWAYS,
        ): Long {
            val id =
                repository.createHabit(
                    HabitEntity(
                        name = name,
                        category = category,
                        displayOrder =
                            habitCount,
                        allowsMultipleCompletions =
                            allowsMultiple,
                        scheduleType =
                            scheduleType,
                        scheduleTarget =
                            scheduleTarget,
                        intervalDays =
                            intervalDays,
                        intervalBasis =
                            intervalBasis,
                        fixedScheduleAnchorEpochDay =
                            fixedAnchor
                                ?.toEpochDay(),
                        scheduleVisibility =
                            visibility,
                        createdAtEpochMillis =
                            createdAt +
                                    habitCount *
                                    1_000L,
                    )
                )

            habitCount += 1
            return id
        }

        suspend fun complete(
            habitId: Long,
            date: LocalDate,
            time: LocalTime,
        ) {
            val day =
                calculator.forDate(date)

            val timestamp =
                calculator.timestampFor(
                    appDate = date,
                    time = time,
                )

            val result =
                repository.addCompletion(
                    habitId = habitId,
                    completionTimestampMillis =
                        timestamp,
                    appDayStartMillis =
                        day.startTimestampMillis,
                    appDayEndMillis =
                        day.endTimestampMillis,
                    recordedTimestampMillis =
                        timestamp,
                )

            check(
                result ==
                        CompletionChangeResult.SUCCESS
            )

            logCount += 1
        }

        suspend fun removeCompletion(
            habitId: Long,
            date: LocalDate,
        ) {
            val day =
                calculator.forDate(date)

            val result =
                repository.removeCompletion(
                    habitId = habitId,
                    appDayStartMillis =
                        day.startTimestampMillis,
                    appDayEndMillis =
                        day.endTimestampMillis,
                    recordedTimestampMillis =
                        calculator.timestampFor(
                            appDate = date,
                            time =
                                LocalTime.of(
                                    23,
                                    0,
                                ),
                        ),
                )

            check(
                result ==
                        CompletionChangeResult.SUCCESS
            )

            // The reversal is also a stored log.
            logCount += 1
        }

        val morningWalk =
            create(
                name = "Morning walk",
                category =
                    HabitCategoryDb.MORNING,
                scheduleType =
                    HabitScheduleTypeDb.DAILY,
            )

        val water =
            create(
                name = "Drink water",
                category =
                    HabitCategoryDb.ANYTIME,
                scheduleType =
                    HabitScheduleTypeDb.DAILY,
                scheduleTarget = 3,
                allowsMultiple = true,
                visibility =
                    HabitScheduleVisibilityDb
                        .HIDE_AFTER_TARGET,
            )

        val gym =
            create(
                name = "Gym",
                category =
                    HabitCategoryDb.ANYTIME,
                scheduleType =
                    HabitScheduleTypeDb
                        .WEEKLY_TARGET,
                scheduleTarget = 3,
            )

        val stretch =
            create(
                name = "Stretch",
                category =
                    HabitCategoryDb.MORNING,
                scheduleType =
                    HabitScheduleTypeDb.DAILY,
                visibility =
                    HabitScheduleVisibilityDb
                        .HIDE_AFTER_TARGET,
            )

        val shave =
            create(
                name = "Shave",
                category =
                    HabitCategoryDb.ANYTIME,
                scheduleType =
                    HabitScheduleTypeDb.INTERVAL,
                intervalDays = 3,
                intervalBasis =
                    HabitIntervalBasisDb
                        .FIXED_SCHEDULE,
                fixedAnchor = earliestDate,
                visibility =
                    HabitScheduleVisibilityDb
                        .WHEN_DUE,
            )

        val recovery =
            create(
                name = "Recovery session",
                category =
                    HabitCategoryDb.ANYTIME,
                scheduleType =
                    HabitScheduleTypeDb
                        .WEEKLY_TARGET,
                scheduleTarget = 4,
            )

        val oldJournal =
            create(
                name = "Old evening journal",
                category =
                    HabitCategoryDb.BEFORE_BED,
                scheduleType =
                    HabitScheduleTypeDb.DAILY,
            )

        for (offset in 0L..179L) {
            val date =
                currentDate.minusDays(offset)

            if (offset % 7L != 6L) {
                complete(
                    habitId = morningWalk,
                    date = date,
                    time =
                        LocalTime.of(7, 30),
                )

                if (offset % 31L == 5L) {
                    removeCompletion(
                        habitId = morningWalk,
                        date = date,
                    )
                }
            }

            if (offset % 10L != 9L) {
                val waterCount =
                    2 +
                            (
                                    offset % 3L
                                    ).toInt()

                repeat(waterCount) { index ->
                    complete(
                        habitId = water,
                        date = date,
                        time =
                            LocalTime.of(
                                9 + index * 3,
                                0,
                            ),
                    )
                }

                if (offset % 29L == 7L) {
                    removeCompletion(
                        habitId = water,
                        date = date,
                    )
                }
            }

            if (
                date.dayOfWeek in
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.FRIDAY,
                ) &&
                offset % 13L != 12L
            ) {
                complete(
                    habitId = gym,
                    date = date,
                    time =
                        LocalTime.of(18, 0),
                )
            }

            if (offset % 4L != 3L) {
                complete(
                    habitId = stretch,
                    date = date,
                    time =
                        LocalTime.of(7, 45),
                )
            }

            if (offset % 3L == 0L) {
                complete(
                    habitId = shave,
                    date = date,
                    time =
                        LocalTime.of(20, 0),
                )
            }

            if (
                date.dayOfWeek in
                setOf(
                    DayOfWeek.TUESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY,
                ) &&
                offset % 17L != 16L
            ) {
                complete(
                    habitId = recovery,
                    date = date,
                    time =
                        LocalTime.of(19, 0),
                )
            }
        }

        // Sparse older history exercises the
        // six-month and one-year ranges.
        for (offset in 182L..419L step 14) {
            val date =
                currentDate.minusDays(offset)

            complete(
                habitId = morningWalk,
                date = date,
                time = LocalTime.of(7, 30),
            )
        }

        for (offset in 182L..419L step 7) {
            val date =
                currentDate.minusDays(offset)

            complete(
                habitId = oldJournal,
                date = date,
                time = LocalTime.of(21, 30),
            )
        }

        check(
            repository.archiveHabit(
                habitId = oldJournal,
                timestampMillis =
                    clock.millis(),
            )
        )

        return HabitSeedResult(
            habits = habitCount,
            logs = logCount,
        )
    }
}