package com.prestonhill.questgiver.data.repository

import androidx.room3.withWriteTransaction
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import kotlinx.coroutines.flow.Flow

class HabitRepository(
    private val database: QuestGiverDatabase
) {
    private val habitDao = database.habitDao()

    fun observeActiveHabits(): Flow<List<HabitEntity>> =
        habitDao.observeActiveHabits()

    fun observeHabitLogs(
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<HabitLogEntity>> {
        require(startTimestamp < endTimestamp)

        return habitDao.observeHabitLogs(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )
    }

    suspend fun createHabit(habit: HabitEntity): Long {
        validateHabit(habit)

        return habitDao.insertHabit(
            habit.copy(
                id = 0,
                name = habit.name.trim(),
                archivedAtEpochMillis = null
            )
        )
    }

    suspend fun updateHabit(habit: HabitEntity): Boolean =
        database.withWriteTransaction {
            val existing = habitDao.getHabit(habit.id)
                ?: return@withWriteTransaction false

            val updated = habit.copy(
                name = habit.name.trim(),

                // These properties cannot change after creation.
                allowsMultipleCompletions =
                    existing.allowsMultipleCompletions,
                createdAtEpochMillis =
                    existing.createdAtEpochMillis,

                // Archiving uses a separate operation.
                archivedAtEpochMillis =
                    existing.archivedAtEpochMillis
            )

            validateHabit(updated)
            habitDao.updateHabit(updated) == 1
        }

    suspend fun addCompletion(
        habitId: Long,
        completionTimestampMillis: Long,
        appDayStartMillis: Long,
        appDayEndMillis: Long,
        recordedTimestampMillis: Long =
            System.currentTimeMillis()
    ): CompletionChangeResult {
        require(appDayStartMillis < appDayEndMillis)
        require(
            completionTimestampMillis in
                    appDayStartMillis until appDayEndMillis
        )

        return database.withWriteTransaction {
            val habit = habitDao.getHabit(habitId)
                ?: return@withWriteTransaction CompletionChangeResult.HABIT_NOT_FOUND

            if (habit.archivedAtEpochMillis != null) {
                return@withWriteTransaction CompletionChangeResult.HABIT_ARCHIVED
            }

            val currentCount = habitDao.getCompletionCount(
                habitId = habitId,
                startTimestamp = appDayStartMillis,
                endTimestamp = appDayEndMillis
            )

            val maximumCount =
                if (habit.allowsMultipleCompletions) {
                    MAXIMUM_DAILY_COMPLETIONS
                } else {
                    1
                }

            if (currentCount >= maximumCount) {
                return@withWriteTransaction CompletionChangeResult.LIMIT_REACHED
            }

            habitDao.insertHabitLog(
                HabitLogEntity(
                    habitId = habitId,
                    completionTimestampMillis =
                        completionTimestampMillis,
                    recordedTimestampMillis =
                        recordedTimestampMillis,
                    delta = 1
                )
            )

            CompletionChangeResult.SUCCESS
        }
    }

    suspend fun removeCompletion(
        habitId: Long,
        appDayStartMillis: Long,
        appDayEndMillis: Long,
        recordedTimestampMillis: Long =
            System.currentTimeMillis()
    ): CompletionChangeResult {
        require(appDayStartMillis < appDayEndMillis)

        return database.withWriteTransaction {
            val habit = habitDao.getHabit(habitId)
                ?: return@withWriteTransaction CompletionChangeResult.HABIT_NOT_FOUND

            if (habit.archivedAtEpochMillis != null) {
                return@withWriteTransaction CompletionChangeResult.HABIT_ARCHIVED
            }

            val positiveLog =
                habitDao.getLatestActiveCompletion(
                    habitId = habitId,
                    startTimestamp = appDayStartMillis,
                    endTimestamp = appDayEndMillis
                )
                    ?: return@withWriteTransaction CompletionChangeResult.NOTHING_TO_REMOVE

            habitDao.insertHabitLog(
                HabitLogEntity(
                    habitId = habitId,

                    // The reversal belongs to the original app-day.
                    completionTimestampMillis =
                        positiveLog.completionTimestampMillis,

                    recordedTimestampMillis =
                        recordedTimestampMillis,
                    delta = -1,
                    reversesLogId = positiveLog.id
                )
            )

            CompletionChangeResult.SUCCESS
        }
    }

    suspend fun setHistoryVisibility(
        habitId: Long,
        visible: Boolean
    ): Boolean =
        database.withWriteTransaction {
            val habit = habitDao.getHabit(habitId)
                ?: return@withWriteTransaction false

            habitDao.updateHabit(
                habit.copy(isVisibleInHistory = visible)
            ) == 1
        }

    suspend fun archiveHabit(
        habitId: Long,
        timestampMillis: Long = System.currentTimeMillis()
    ): Boolean =
        habitDao.archiveHabit(
            habitId = habitId,
            timestamp = timestampMillis
        ) == 1

    suspend fun deleteHabit(
        habitId: Long): Boolean = habitDao.deleteHabit(habitId) == 1

    fun observeAllHabitLogs(): Flow<List<HabitLogEntity>> =
        habitDao.observeAllHabitLogs()

    private fun validateHabit(habit: HabitEntity) {
        require(habit.name.isNotBlank())
        require(habit.displayOrder >= 0)
        require(habit.scheduleTarget in 1..100)

        if (habit.scheduleType == HabitScheduleTypeDb.INTERVAL) {
            require(
                habit.intervalDays != null &&
                        habit.intervalDays > 0
            )
            require(habit.intervalBasis != null)
        }
    }

    private companion object {
        const val MAXIMUM_DAILY_COMPLETIONS = 100L
    }

    suspend fun getHabit(habitId: Long): HabitEntity? =
        habitDao.getHabit(habitId)

    fun observeArchivedHabits(): Flow<List<HabitEntity>> =
        habitDao.observeArchivedHabits()

    suspend fun restoreHabit(habitId: Long): Boolean =
        habitDao.restoreHabit(habitId) == 1
}

enum class CompletionChangeResult {
    SUCCESS,
    HABIT_NOT_FOUND,
    HABIT_ARCHIVED,
    LIMIT_REACHED,
    NOTHING_TO_REMOVE
}