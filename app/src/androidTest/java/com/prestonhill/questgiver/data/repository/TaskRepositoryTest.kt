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
    fun deletionKeepsHistory() = runBlocking {
        val taskId = addTask()

        repository.complete(
            taskId = taskId,
            scheduledEpochDay = TEST_DAY,
            completionTimestampMillis =
                FIRST_COMPLETION,
        )

        assertTrue(
            repository.deleteTask(
                taskId = taskId,
                deleteHistory = false,
            )
        )

        assertNull(
            repository.getTask(taskId)
        )

        val log =
            repository.observeLogs()
                .first()
                .single()

        assertNull(log.taskId)
        assertEquals(
            "Test task",
            log.taskNameSnapshot,
        )
    }

    @Test
    fun deletionCanRemoveHistory() = runBlocking {
        val taskId = addTask()

        repository.complete(
            taskId = taskId,
            scheduledEpochDay = TEST_DAY,
            completionTimestampMillis =
                FIRST_COMPLETION,
        )

        assertTrue(
            repository.deleteTask(
                taskId = taskId,
                deleteHistory = true,
            )
        )

        assertNull(
            repository.getTask(taskId)
        )

        assertTrue(
            repository.observeLogs()
                .first()
                .isEmpty()
        )
    }

    @Test
    fun orphanHistoryCanBeDeleted() = runBlocking {
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

        assertFalse(
            repository.deleteHistory(positive.id)
        )

        repository.correctCompletion(
            logId = positive.id,
            recordedTimestampMillis =
                SECOND_COMPLETION,
        )

        repository.deleteTask(
            taskId = taskId,
            deleteHistory = false,
        )

        assertTrue(
            repository.deleteHistory(positive.id)
        )

        assertTrue(
            repository.observeLogs()
                .first()
                .isEmpty()
        )
    }

    @Test
    fun cleanupDeletesOnlyExpiredOneTimeTasks() =
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
                completionTimestampMillis = 1_000L,
            )

            repository.complete(
                taskId = recentId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis = 3_000L,
            )

            repository.complete(
                taskId = dailyId,
                scheduledEpochDay = TEST_DAY,
                completionTimestampMillis = 1_000L,
            )

            val deleted =
                repository.deleteExpiredTasks(
                    completedBefore = 2_000L,
                )

            assertEquals(1, deleted)
            assertNull(repository.getTask(expiredId))
            assertNotNull(repository.getTask(recentId))
            assertNotNull(repository.getTask(dailyId))

            val logs =
                repository.observeLogs().first()

            assertEquals(3, logs.size)

            assertNull(
                logs.single {
                    it.taskNameSnapshot == "Expired"
                }.taskId
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