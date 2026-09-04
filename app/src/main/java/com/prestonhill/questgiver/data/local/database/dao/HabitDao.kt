package com.prestonhill.questgiver.data.local.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.prestonhill.questgiver.data.local.database.entity.HabitDisplaySectionEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query(
        """
    SELECT habits.*
    FROM habits
    INNER JOIN habit_display_sections AS sections
        ON sections.id = habits.category
    WHERE habits.archivedAtEpochMillis IS NULL
    ORDER BY
        sections.displayOrder,
        habits.displayOrder,
        habits.createdAtEpochMillis,
        habits.id
    """
    )
    fun observeActiveHabits():
            Flow<List<HabitEntity>>

    @Query(
        """
    SELECT * FROM habits
    ORDER BY createdAtEpochMillis, id
    """
    )
    fun observeAllHabits():
            Flow<List<HabitEntity>>

    @Query(
        """
    SELECT * FROM habit_display_sections
    ORDER BY displayOrder, id
    """
    )
    fun observeDisplaySections():
            Flow<List<HabitDisplaySectionEntity>>

    @Query(
        """
    SELECT * FROM habit_display_sections
    WHERE id = :sectionId
    LIMIT 1
    """
    )
    suspend fun getDisplaySection(
        sectionId: String,
    ): HabitDisplaySectionEntity?

    @Query(
        """
    SELECT * FROM habit_display_sections
    WHERE name = :name COLLATE NOCASE
    LIMIT 1
    """
    )
    suspend fun findDisplaySectionByName(
        name: String,
    ): HabitDisplaySectionEntity?

    @Query(
        """
    SELECT COALESCE(MAX(displayOrder), -1) + 1
    FROM habit_display_sections
    """
    )
    suspend fun nextDisplaySectionOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDisplaySection(
        section: HabitDisplaySectionEntity,
    )

    @Update
    suspend fun updateDisplaySection(
        section: HabitDisplaySectionEntity,
    ): Int

    @Query(
        """
    DELETE FROM habit_display_sections
    WHERE id = :sectionId
      AND NOT EXISTS (
          SELECT 1 FROM habits
          WHERE category = :sectionId
      )
      AND (
          SELECT COUNT(*)
          FROM habit_display_sections
      ) > 1
    """
    )
    suspend fun deleteEmptyDisplaySection(
        sectionId: String,
    ): Int

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

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long): Int


    @Query(
        """
    SELECT * FROM habits
    WHERE archivedAtEpochMillis IS NOT NULL
    ORDER BY archivedAtEpochMillis DESC
    """
    )
    fun observeArchivedHabits(): Flow<List<HabitEntity>>

    @Query(
        """
    UPDATE habits
    SET archivedAtEpochMillis = NULL
    WHERE id = :habitId
    """
    )
    suspend fun restoreHabit(habitId: Long): Int
}