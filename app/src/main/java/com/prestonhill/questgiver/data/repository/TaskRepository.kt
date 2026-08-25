package com.prestonhill.questgiver.data.repository

import androidx.room3.withWriteTransaction
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val database: QuestGiverDatabase,
) {
    private val dao = database.taskDao()

    fun observeTasks(): Flow<List<TaskEntity>> =
        dao.observeTasks()

    fun observeLogs(): Flow<List<TaskLogEntity>> =
        dao.observeLogs()

    suspend fun getTask(
        taskId: Long,
    ): TaskEntity? =
        dao.getTask(taskId)

    suspend fun getLog(
        logId: Long,
    ): TaskLogEntity? =
        dao.getLog(logId)

    suspend fun createTask(
        task: TaskEntity,
    ): Long {
        val cleaned =
            task.copy(
                id = 0,
                name = task.name.trim(),
                category =
                    task.category
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
            )

        validate(cleaned)

        return dao.insertTask(cleaned)
    }

    suspend fun updateTask(
        task: TaskEntity,
    ): Boolean =
        database.withWriteTransaction {
            val existing =
                dao.getTask(task.id)
                    ?: return@withWriteTransaction false

            val cleaned =
                task.copy(
                    id = existing.id,
                    name = task.name.trim(),
                    category =
                        task.category
                            ?.trim()
                            ?.takeIf(String::isNotEmpty),
                    createdAtEpochMillis =
                        existing.createdAtEpochMillis,
                )

            validate(cleaned)

            dao.updateTask(cleaned) == 1
        }

    suspend fun complete(
        taskId: Long,
        scheduledEpochDay: Long,
        completionTimestampMillis: Long,
        recordedTimestampMillis: Long =
            System.currentTimeMillis(),
    ): TaskCompletionResult =
        setCompletion(
            taskId = taskId,
            scheduledEpochDay =
                scheduledEpochDay,
            completed = true,
            completionTimestampMillis =
                completionTimestampMillis,
            recordedTimestampMillis =
                recordedTimestampMillis,
        )

    suspend fun setCompletion(
        taskId: Long,
        scheduledEpochDay: Long,
        completed: Boolean,
        completionTimestampMillis: Long,
        recordedTimestampMillis: Long =
            System.currentTimeMillis(),
    ): TaskCompletionResult =
        database.withWriteTransaction {
            val task =
                dao.getTask(taskId)
                    ?: return@withWriteTransaction TaskCompletionResult
                .TASK_NOT_FOUND

            val activeLog =
                if (
                    task.scheduleType ==
                    TaskScheduleTypeDb.ONE_TIME
                ) {
                    dao.getLatestActiveLog(taskId)
                } else {
                    dao.getActiveLog(
                        taskId = taskId,
                        scheduledEpochDay =
                            scheduledEpochDay,
                    )
                }

            when {
                completed &&
                        activeLog != null ->
                    return@withWriteTransaction TaskCompletionResult
                    .ALREADY_COMPLETED

                    !completed &&
                        activeLog == null ->
                return@withWriteTransaction TaskCompletionResult
                    .ALREADY_INCOMPLETE

                        completed -> insertCompletion(
                        task = task,
                        scheduledEpochDay =
                            scheduledEpochDay,
                        completionTimestampMillis =
                            completionTimestampMillis,
                        recordedTimestampMillis =
                            recordedTimestampMillis,
                    )

                else ->
                    insertReversal(
                        log = requireNotNull(
                            activeLog
                        ),
                        recordedTimestampMillis =
                            recordedTimestampMillis,
                    )
            }

            TaskCompletionResult.SUCCESS
        }

    suspend fun correctCompletion(
        logId: Long,
        recordedTimestampMillis: Long =
            System.currentTimeMillis(),
    ): TaskCompletionResult =
        database.withWriteTransaction {
            val original =
                dao.getLog(logId)
                    ?: return@withWriteTransaction TaskCompletionResult
                .LOG_NOT_FOUND

            if (original.delta != 1) {
                return@withWriteTransaction TaskCompletionResult
                    .ALREADY_CORRECTED
            }

            if (original.taskId == null) {
                return@withWriteTransaction TaskCompletionResult
                    .TASK_DELETED
            }

            val reversible =
                dao.getReversibleLog(logId)
                    ?: return@withWriteTransaction TaskCompletionResult
                .ALREADY_CORRECTED

            insertReversal(
                log = reversible,
                recordedTimestampMillis =
                    recordedTimestampMillis,
            )

            TaskCompletionResult.SUCCESS
        }

    suspend fun deleteTask(
        taskId: Long,
        deleteHistory: Boolean,
    ): Boolean =
        database.withWriteTransaction {
            dao.getTask(taskId)
                ?: return@withWriteTransaction false

            if (deleteHistory) {
                dao.deleteTaskLogs(taskId)
            }

            dao.deleteTask(taskId) == 1
        }

    suspend fun deleteHistory(
        positiveLogId: Long,
    ): Boolean =
        database.withWriteTransaction {
            val log =
                dao.getLog(positiveLogId)
                    ?: return@withWriteTransaction false

            if (
                log.taskId != null ||
                log.delta != 1
            ) {
                return@withWriteTransaction false
            }

            dao.deleteOrphanHistory(
                positiveLogId
            ) > 0
        }

    suspend fun deleteExpiredTasks(
        completedBefore: Long,
    ): Int =
        dao.deleteExpiredTasks(completedBefore)

    private suspend fun insertCompletion(
        task: TaskEntity,
        scheduledEpochDay: Long,
        completionTimestampMillis: Long,
        recordedTimestampMillis: Long,
    ) {
        dao.insertLog(
            TaskLogEntity(
                taskId = task.id,
                taskNameSnapshot = task.name,
                categorySnapshot = task.category,
                scheduledEpochDay =
                    scheduledEpochDay,
                dueMinuteOfDaySnapshot =
                    task.dueMinuteOfDay,
                completionTimestampMillis =
                    completionTimestampMillis,
                recordedTimestampMillis =
                    recordedTimestampMillis,
                delta = 1,
            )
        )
    }

    private suspend fun insertReversal(
        log: TaskLogEntity,
        recordedTimestampMillis: Long,
    ) {
        dao.insertLog(
            TaskLogEntity(
                taskId = log.taskId,
                taskNameSnapshot =
                    log.taskNameSnapshot,
                categorySnapshot =
                    log.categorySnapshot,
                scheduledEpochDay =
                    log.scheduledEpochDay,
                dueMinuteOfDaySnapshot =
                    log.dueMinuteOfDaySnapshot,
                completionTimestampMillis =
                    log.completionTimestampMillis,
                recordedTimestampMillis =
                    recordedTimestampMillis,
                delta = -1,
                reversesLogId = log.id,
            )
        )
    }

    private fun validate(task: TaskEntity) {
        require(task.name.isNotBlank())
        require(task.displayOrder >= 0)

        task.dueMinuteOfDay?.let { minute ->
            require(minute in 0..1_439)
        }

        when (task.scheduleType) {
            TaskScheduleTypeDb.ONE_TIME -> {
                require(
                    task.recurrenceStartEpochDay == null
                )
                require(task.weekdaysMask == null)
                require(task.intervalDays == null)
                require(task.intervalBasis == null)

                if (task.dueMinuteOfDay != null) {
                    require(
                        task.scheduledEpochDay != null
                    )
                }
            }

            TaskScheduleTypeDb.DAILY -> {
                require(
                    task.recurrenceStartEpochDay != null
                )
                require(
                    task.scheduledEpochDay == null
                )
                require(task.weekdaysMask == null)
                require(task.intervalDays == null)
                require(task.intervalBasis == null)
            }

            TaskScheduleTypeDb.WEEKLY_DAYS -> {
                require(
                    task.recurrenceStartEpochDay != null
                )
                require(
                    task.scheduledEpochDay == null
                )
                require(
                    task.weekdaysMask in 1..127
                )
                require(task.intervalDays == null)
                require(task.intervalBasis == null)
            }

            TaskScheduleTypeDb.INTERVAL -> {
                require(
                    task.recurrenceStartEpochDay != null
                )
                require(
                    task.scheduledEpochDay == null
                )
                require(task.weekdaysMask == null)
                require(
                    task.intervalDays
                        ?.let { it > 0 } == true
                )
                require(task.intervalBasis != null)
            }
        }
    }
}

enum class TaskCompletionResult {
    SUCCESS,
    TASK_NOT_FOUND,
    LOG_NOT_FOUND,
    TASK_DELETED,
    ALREADY_COMPLETED,
    ALREADY_CORRECTED,
    ALREADY_INCOMPLETE,
}