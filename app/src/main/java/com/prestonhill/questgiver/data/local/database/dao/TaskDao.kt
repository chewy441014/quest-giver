package com.prestonhill.questgiver.data.local.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
    SELECT * FROM tasks
    WHERE archivedAtEpochMillis IS NULL
    ORDER BY displayOrder, createdAtEpochMillis, id
    """
    )
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query(
        """
    SELECT * FROM tasks
    ORDER BY displayOrder, createdAtEpochMillis, id
    """
    )
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query(
        """
    SELECT * FROM tasks
    WHERE archivedAtEpochMillis IS NOT NULL
    ORDER BY archivedAtEpochMillis DESC, id DESC
    """
    )
    fun observeArchivedTasks():
            Flow<List<TaskEntity>>

    @Query(
        """
    UPDATE tasks
    SET archivedAtEpochMillis = :timestamp
    WHERE id = :taskId
      AND archivedAtEpochMillis IS NULL
    """
    )
    suspend fun archiveTask(
        taskId: Long,
        timestamp: Long,
    ): Int

    @Query(
        """
    UPDATE tasks
    SET archivedAtEpochMillis = NULL
    WHERE id = :taskId
      AND archivedAtEpochMillis IS NOT NULL
    """
    )
    suspend fun restoreTask(
        taskId: Long,
    ): Int

    @Query(
        """
        SELECT * FROM task_logs
        ORDER BY completionTimestampMillis, id
        """
    )
    fun observeLogs(): Flow<List<TaskLogEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE id = :taskId
        LIMIT 1
        """
    )
    suspend fun getTask(
        taskId: Long,
    ): TaskEntity?

    @Query(
        """
        SELECT * FROM task_logs
        WHERE id = :logId
        LIMIT 1
        """
    )
    suspend fun getLog(
        logId: Long,
    ): TaskLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(
        task: TaskEntity,
    ): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(
        log: TaskLogEntity,
    ): Long

    @Update
    suspend fun updateTask(
        task: TaskEntity,
    ): Int

    @Query(
        """
        SELECT positive.* FROM task_logs AS positive
        WHERE positive.taskId = :taskId
          AND positive.scheduledEpochDay = :scheduledEpochDay
          AND positive.delta = 1
          AND NOT EXISTS (
              SELECT 1 FROM task_logs AS reversal
              WHERE reversal.reversesLogId = positive.id
          )
        ORDER BY
            positive.completionTimestampMillis DESC,
            positive.id DESC
        LIMIT 1
        """
    )
    suspend fun getActiveLog(
        taskId: Long,
        scheduledEpochDay: Long,
    ): TaskLogEntity?

    @Query(
        """
        SELECT positive.* FROM task_logs AS positive
        WHERE positive.id = :logId
          AND positive.delta = 1
          AND NOT EXISTS (
              SELECT 1 FROM task_logs AS reversal
              WHERE reversal.reversesLogId = positive.id
          )
        LIMIT 1
        """
    )
    suspend fun getReversibleLog(
        logId: Long,
    ): TaskLogEntity?

    @Query(
        """
    DELETE FROM tasks
    WHERE id = :taskId
      AND archivedAtEpochMillis IS NOT NULL
    """
    )
    suspend fun deleteArchivedTask(
        taskId: Long,
    ): Int

    @Query(
        """
    UPDATE tasks
    SET archivedAtEpochMillis = :archivedAt
    WHERE archivedAtEpochMillis IS NULL
      AND scheduleType = 'ONE_TIME'
      AND EXISTS (
          SELECT 1
          FROM task_logs AS positive
          WHERE positive.taskId = tasks.id
            AND positive.delta = 1
            AND positive.completionTimestampMillis <
                :completedBefore
            AND NOT EXISTS (
                SELECT 1
                FROM task_logs AS reversal
                WHERE reversal.reversesLogId =
                    positive.id
            )
      )
    """
    )
    suspend fun archiveExpiredTasks(
        completedBefore: Long,
        archivedAt: Long,
    ): Int

    @Query(
        """
    SELECT positive.* FROM task_logs AS positive
    WHERE positive.taskId = :taskId
      AND positive.delta = 1
      AND NOT EXISTS (
          SELECT 1 FROM task_logs AS reversal
          WHERE reversal.reversesLogId = positive.id
      )
    ORDER BY
        positive.completionTimestampMillis DESC,
        positive.id DESC
    LIMIT 1
    """
    )
    suspend fun getLatestActiveLog(
        taskId: Long,
    ): TaskLogEntity?
}