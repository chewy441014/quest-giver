package com.prestonhill.questgiver.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.dao.TaskDao
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskLogEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: TaskRepository
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<
                    QuestGiverDatabase
                    >(context)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(
                    Dispatchers.IO
                )
                .build()

        repository = TaskRepository(database)
        dao = database.taskDao()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun completionCreatesLog() = runBlocking {
        val taskId =
            addTask(
                name = "  Buy groceries  ",
                category = "  Personal  ",
            )

        val result =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
                recordedTimestampMillis =
                    FIRST_COMPLETION,
            )

        assertEquals(
            TaskCompletionResult.SUCCESS,
            result,
        )

        val logs =
            repository.observeLogs().first()

        assertEquals(1, logs.size)

        with(logs.single()) {
            assertEquals(taskId, this.taskId)
            assertEquals(
                "Buy groceries",
                taskNameSnapshot,
            )
            assertEquals(
                "Personal",
                categorySnapshot,
            )
            assertEquals(
                TEST_DAY,
                scheduledEpochDay,
            )
            assertEquals(9 * 60, dueMinuteOfDaySnapshot)
            assertEquals(1, delta)
            assertNull(reversesLogId)
        }
    }

    @Test
    fun oneTimeCompletesOnce() = runBlocking {
        val taskId = addTask()

        val first =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

        val second =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY + 1,
                completionTimestampMillis =
                    SECOND_COMPLETION,
            )

        assertEquals(
            TaskCompletionResult.SUCCESS,
            first,
        )

        assertEquals(
            TaskCompletionResult.ALREADY_COMPLETED,
            second,
        )

        assertEquals(
            1,
            repository.observeLogs().first().size,
        )
    }

    @Test
    fun dailyCompletesEachDay() = runBlocking {
        val taskId =
            addTask(
                scheduleType =
                    TaskScheduleTypeDb.DAILY,
            )

        val first =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

        val duplicate =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION + 1,
            )

        val nextDay =
            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY + 1,
                completionTimestampMillis =
                    SECOND_COMPLETION,
            )

        assertEquals(
            TaskCompletionResult.SUCCESS,
            first,
        )

        assertEquals(
            TaskCompletionResult.ALREADY_COMPLETED,
            duplicate,
        )

        assertEquals(
            TaskCompletionResult.SUCCESS,
            nextDay,
        )

        assertEquals(
            2,
            repository.observeLogs().first().size,
        )
    }

    @Test
    fun correctionReopensTask() = runBlocking {
        val taskId = addTask()

        repository.complete(
            taskId = taskId,
            scheduledEpochDay = TEST_DAY,
            completionTimestampMillis =
                FIRST_COMPLETION,
        )

        val positive =
            repository.observeLogs()
                .first()
                .single()

        val result =
            repository.correctCompletion(
                logId = positive.id,
                recordedTimestampMillis =
                    SECOND_COMPLETION,
            )

        assertEquals(
            TaskCompletionResult.SUCCESS,
            result,
        )

        assertNull(
            dao.getLatestActiveLog(taskId)
        )

        val logs =
            repository.observeLogs().first()

        assertEquals(2, logs.size)

        val reversal =
            logs.single { it.delta == -1 }

        assertEquals(
            positive.id,
            reversal.reversesLogId,
        )

        assertEquals(
            positive.completionTimestampMillis,
            reversal.completionTimestampMillis,
        )
    }

    @Test
    fun activeTaskCannotBeDeleted() = runBlocking {
        val taskId = addTask()

        repository.complete(
            taskId = taskId,
            scheduledEpochDay = TEST_DAY,
            completionTimestampMillis =
                FIRST_COMPLETION,
        )

        assertFalse(
            repository.deleteArchivedTask(taskId)
        )

        assertNotNull(
            repository.getTask(taskId)
        )

        assertEquals(
            taskId,
            repository
                .observeLogs()
                .first()
                .single()
                .taskId,
        )
    }

    @Test
    fun archivedDeletionRemovesTaskAndLogs() =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            val positive =
                repository
                    .observeLogs()
                    .first()
                    .single()

            repository.correctCompletion(
                logId = positive.id,
                recordedTimestampMillis =
                    SECOND_COMPLETION,
            )

            assertEquals(
                2,
                repository.observeLogs()
                    .first()
                    .size,
            )

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis =
                        SECOND_COMPLETION,
                )
            )

            assertTrue(
                repository.deleteArchivedTask(taskId)
            )

            assertNull(
                repository.getTask(taskId)
            )

            assertTrue(
                repository.observeLogs()
                    .first()
                    .isEmpty()
            )

            assertTrue(
                repository.observeArchivedTasks()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun missingArchivedTaskCannotBeDeleted() =
        runBlocking {
            assertFalse(
                repository.deleteArchivedTask(
                    Long.MAX_VALUE
                )
            )
        }

    @Test
    fun setIncompleteAddsReversal(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            val result =
                repository.setCompletion(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completed = false,
                    completionTimestampMillis =
                        SECOND_COMPLETION,
                    recordedTimestampMillis =
                        SECOND_COMPLETION,
                )

            assertEquals(
                TaskCompletionResult.SUCCESS,
                result,
            )

            val logs =
                repository.observeLogs()
                    .first { it.size == 2 }

            val positive =
                logs.single { it.delta == 1 }

            val reversal =
                logs.single { it.delta == -1 }

            assertEquals(
                positive.id,
                reversal.reversesLogId,
            )

            assertEquals(
                positive.completionTimestampMillis,
                reversal.completionTimestampMillis,
            )
        }

    @Test
    fun setCompleteRestoresDay(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            repository.setCompletion(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completed = false,
                completionTimestampMillis =
                    SECOND_COMPLETION,
            )

            val restored =
                repository.setCompletion(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completed = true,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 1_000L,
                )

            assertEquals(
                TaskCompletionResult.SUCCESS,
                restored,
            )

            val logs =
                repository.observeLogs()
                    .first { it.size == 3 }

            assertEquals(
                2,
                logs.count { it.delta == 1 },
            )

            assertEquals(
                1,
                logs.count { it.delta == -1 },
            )

            val duplicate =
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 2_000L,
                )

            assertEquals(
                TaskCompletionResult
                    .ALREADY_COMPLETED,
                duplicate,
            )
        }

    @Test
    fun repeatedIncompleteIsSafe(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            repository.setCompletion(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completed = false,
                completionTimestampMillis =
                    SECOND_COMPLETION,
            )

            val repeated =
                repository.setCompletion(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completed = false,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 1_000L,
                )

            assertEquals(
                TaskCompletionResult
                    .ALREADY_INCOMPLETE,
                repeated,
            )

            assertEquals(
                2,
                repository.observeLogs()
                    .first()
                    .size,
            )
        }

    @Test
    fun recurringDaysAreIndependent(): Unit =
        runBlocking {
            val nextDay = TEST_DAY + 1L

            val taskId =
                addTask(
                    scheduleType =
                        TaskScheduleTypeDb.DAILY
                )

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = nextDay,
                completionTimestampMillis =
                    FIRST_COMPLETION + 1_000L,
            )

            repository.setCompletion(
                taskId = taskId,
                scheduledEpochDay = nextDay,
                completed = false,
                completionTimestampMillis =
                    SECOND_COMPLETION,
            )

            val firstDay =
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 1_000L,
                )

            val secondDay =
                repository.setCompletion(
                    taskId = taskId,
                    scheduledEpochDay = nextDay,
                    completed = true,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 2_000L,
                )

            assertEquals(
                TaskCompletionResult
                    .ALREADY_COMPLETED,
                firstDay,
            )

            assertEquals(
                TaskCompletionResult.SUCCESS,
                secondDay,
            )
        }
    @Test
    fun latestActiveLogWins(): Unit =
        runBlocking {
            val taskId = addTask()

            dao.insertLog(
                testLog(
                    taskId = taskId,
                    completionTime = 1_000L,
                )
            )

            val expectedId =
                dao.insertLog(
                    testLog(
                        taskId = taskId,
                        completionTime = 2_000L,
                    )
                )

            val correctedId =
                dao.insertLog(
                    testLog(
                        taskId = taskId,
                        completionTime = 3_000L,
                    )
                )

            dao.insertLog(
                testLog(
                    taskId = taskId,
                    completionTime = 3_000L,
                    recordedTime = 4_000L,
                    delta = -1,
                    reversesLogId = correctedId,
                )
            )

            val active =
                dao.getLatestActiveLog(taskId)

            assertEquals(
                expectedId,
                active?.id,
            )
        }

    @Test
    fun archiveKeepsHistory(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.complete(
                taskId = taskId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    FIRST_COMPLETION,
            )

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis = SECOND_COMPLETION,
                )
            )

            assertTrue(
                repository.observeTasks()
                    .first()
                    .none { it.id == taskId }
            )

            val archived =
                repository.observeArchivedTasks()
                    .first()
                    .single()

            assertEquals(taskId, archived.id)

            assertEquals(
                SECOND_COMPLETION,
                archived.archivedAtEpochMillis,
            )

            assertTrue(
                repository.observeAllTasks()
                    .first()
                    .any { it.id == taskId }
            )

            val log =
                repository.observeLogs()
                    .first()
                    .single()

            assertEquals(taskId, log.taskId)
        }

    @Test
    fun archivedTaskCannotChange(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.archiveTask(
                taskId = taskId,
                timestampMillis =
                    SECOND_COMPLETION,
            )

            val result =
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 1L,
                )

            assertEquals(
                TaskCompletionResult.TASK_ARCHIVED,
                result,
            )

            assertTrue(
                repository.observeLogs()
                    .first()
                    .isEmpty()
            )
        }

    @Test
    fun restoreReactivatesTask(): Unit =
        runBlocking {
            val taskId = addTask()

            repository.archiveTask(
                taskId = taskId,
                timestampMillis =
                    SECOND_COMPLETION,
            )

            assertTrue(
                repository.restoreTask(taskId)
            )

            val task =
                repository.observeTasks()
                    .first()
                    .single()

            assertEquals(taskId, task.id)

            assertNull(
                task.archivedAtEpochMillis
            )

            assertTrue(
                repository.observeArchivedTasks()
                    .first()
                    .isEmpty()
            )

            val result =
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay = TEST_DAY,
                    completionTimestampMillis =
                        SECOND_COMPLETION + 1L,
                )

            assertEquals(
                TaskCompletionResult.SUCCESS,
                result,
            )
        }

    @Test
    fun cleanupArchivesExpiredTasks(): Unit =
        runBlocking {
            val expiredId =
                addTask(name = "Expired")

            val recentId =
                addTask(name = "Recent")

            val dailyId =
                addTask(
                    name = "Daily",
                    scheduleType =
                        TaskScheduleTypeDb.DAILY,
                )

            repository.complete(
                taskId = expiredId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    1_000L,
            )

            repository.complete(
                taskId = recentId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    3_000L,
            )

            repository.complete(
                taskId = dailyId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis =
                    1_000L,
            )

            val count =
                repository.archiveExpiredTasks(
                    completedBefore = 2_000L,
                    archivedAt = 4_000L,
                )

            assertEquals(1, count)

            val expired =
                repository.getTask(expiredId)

            assertNotNull(expired)

            assertEquals(
                4_000L,
                expired?.archivedAtEpochMillis,
            )

            assertNull(
                repository.getTask(recentId)
                    ?.archivedAtEpochMillis
            )

            assertNull(
                repository.getTask(dailyId)
                    ?.archivedAtEpochMillis
            )

            assertTrue(
                repository.observeTasks()
                    .first()
                    .none { it.id == expiredId }
            )

            assertEquals(
                listOf(expiredId),
                repository
                    .observeArchivedTasks()
                    .first()
                    .map { it.id },
            )

            assertTrue(
                repository.observeLogs()
                    .first()
                    .all { it.taskId != null }
            )
        }
    private fun testLog(
        taskId: Long,
        completionTime: Long,
        recordedTime: Long = completionTime,
        delta: Int = 1,
        reversesLogId: Long? = null,
    ): TaskLogEntity =
        TaskLogEntity(
            taskId = taskId,
            taskNameSnapshot = "Test task",
            categorySnapshot = "General",
            scheduledEpochDay = TEST_DAY,
            dueMinuteOfDaySnapshot = 9 * 60,
            completionTimestampMillis =
                completionTime,
            recordedTimestampMillis =
                recordedTime,
            delta = delta,
            reversesLogId = reversesLogId,
        )

    private suspend fun addTask(
        name: String = "Test task",
        category: String? = "General",
        scheduleType: TaskScheduleTypeDb =
            TaskScheduleTypeDb.ONE_TIME,
    ): Long =
        repository.createTask(
            TaskEntity(
                name = name,
                category = category,
                displayOrder = 0,
                scheduleType = scheduleType,
                scheduledEpochDay =
                    if (
                        scheduleType ==
                        TaskScheduleTypeDb.ONE_TIME
                    ) {
                        TEST_DAY
                    } else {
                        null
                    },
                recurrenceStartEpochDay =
                    if (
                        scheduleType ==
                        TaskScheduleTypeDb.ONE_TIME
                    ) {
                        null
                    } else {
                        TEST_DAY
                    },
                dueMinuteOfDay = 9 * 60,
                createdAtEpochMillis = 500L,
            )
        )

    companion object {
        const val TEST_DAY = 20_000L
        const val FIRST_COMPLETION = 1_000L
        const val SECOND_COMPLETION = 2_000L
    }
}