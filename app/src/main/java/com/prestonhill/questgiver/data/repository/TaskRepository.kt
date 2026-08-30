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

    fun observeAllTasks(): Flow<List<TaskEntity>> =
        dao.observeAllTasks()

    fun observeArchivedTasks():
            Flow<List<TaskEntity>> =
        dao.observeArchivedTasks()

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
                archivedAtEpochMillis = null,
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
                    archivedAtEpochMillis =
                        existing.archivedAtEpochMillis,
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

            if (task.archivedAtEpochMillis != null) {
                return@withWriteTransaction TaskCompletionResult.TASK_ARCHIVED
            }

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

            val taskId =
                original.taskId
                    ?: return@withWriteTransaction TaskCompletionResult.TASK_DELETED

            val task =
                dao.getTask(taskId)
                    ?: return@withWriteTransaction TaskCompletionResult.TASK_DELETED

            if (task.archivedAtEpochMillis != null) {
                return@withWriteTransaction TaskCompletionResult.TASK_ARCHIVED
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

    suspend fun archiveTask(
        taskId: Long,
        timestampMillis: Long =
            System.currentTimeMillis(),
    ): Boolean =
        dao.archiveTask(
            taskId = taskId,
            timestamp = timestampMillis,
        ) == 1

    suspend fun restoreTask(
        taskId: Long,
    ): Boolean =
        dao.restoreTask(taskId) == 1

    suspend fun archiveExpiredTasks(
        completedBefore: Long,
        archivedAt: Long =
            System.currentTimeMillis(),
    ): Int =
        dao.archiveExpiredTasks(
            completedBefore = completedBefore,
            archivedAt = archivedAt,
        )

    suspend fun deleteArchivedTask(
        taskId: Long,
    ): Boolean =
        dao.deleteArchivedTask(taskId) == 1

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
    TASK_ARCHIVED,
}