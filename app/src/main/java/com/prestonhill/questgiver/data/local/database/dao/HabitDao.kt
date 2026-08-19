package com.prestonhill.questgiver.data.local.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query(
        """
        SELECT * FROM habits
        WHERE archivedAtEpochMillis IS NULL
        ORDER BY
            CASE category
                WHEN 'MORNING' THEN 0
                WHEN 'ANYTIME' THEN 1
                WHEN 'BEFORE_BED' THEN 2
            END,
            displayOrder
        """
    )
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    suspend fun getHabit(habitId: Long): HabitEntity?

    @Query(
        """
    SELECT * FROM habit_logs
    ORDER BY completionTimestampMillis, id
    """
    )
    fun observeAllHabitLogs(): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity): Int

    @Query(
        """
        UPDATE habits
        SET archivedAtEpochMillis = :timestamp
        WHERE id = :habitId
        """
    )
    suspend fun archiveHabit(
        habitId: Long,
        timestamp: Long
    ): Int

    @Delete
    suspend fun deleteHabitPermanently(habit: HabitEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHabitLog(log: HabitLogEntity): Long

    @Query(
        """
        SELECT * FROM habit_logs
        WHERE completionTimestampMillis >= :startTimestamp
          AND completionTimestampMillis < :endTimestamp
        ORDER BY completionTimestampMillis, id
        """
    )
    fun observeHabitLogs(
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<HabitLogEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0)
        FROM habit_logs
        WHERE habitId = :habitId
          AND completionTimestampMillis >= :startTimestamp
          AND completionTimestampMillis < :endTimestamp
        """
    )
    suspend fun getCompletionCount(
        habitId: Long,
        startTimestamp: Long,
        endTimestamp: Long
    ): Long

    @Query(
        """
        SELECT positive.* FROM habit_logs AS positive
        WHERE positive.habitId = :habitId
          AND positive.delta = 1
          AND positive.completionTimestampMillis >= :startTimestamp
          AND positive.completionTimestampMillis < :endTimestamp
          AND NOT EXISTS (
              SELECT 1 FROM habit_logs AS reversal
              WHERE reversal.reversesLogId = positive.id
          )
        ORDER BY positive.completionTimestampMillis DESC, positive.id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestActiveCompletion(
        habitId: Long,
        startTimestamp: Long,
        endTimestamp: Long
    ): HabitLogEntity?

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteHistoryForHabit(habitId: Long): Int
}