package com.prestonhill.questgiver.feature.history

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            TaskRepository

    private lateinit var viewModel:
            HistoryViewModel

    private lateinit var viewModelStore:
            ViewModelStore

    private var taskId = 0L

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

        val factory =
            HistoryViewModelFactory(
                repository = repository,
                settings =
                    flowOf(AppSettings()),
                clock = CLOCK,
            )

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory,
            )[HistoryViewModel::class.java]

        composeRule.setContent {
            val state by
            viewModel.uiState.collectAsState()

            MaterialTheme {
                HistoryScreen(
                    state = state,
                    onAction = viewModel::onAction,
                )
            }
        }
    }

    @After
    fun close() {
        viewModelStore.clear()
        database.close()
    }

    @Test
    fun archivedTaskDeletionJourney(): Unit {
        seedArchivedTask()
        openArchivedTasks()
        deleteTask()
        verifyDeletion()
    }

    private fun seedArchivedTask() {
        runBlocking {
            taskId =
                repository.createTask(
                    TaskEntity(
                        name = TASK_NAME,
                        category = "General",
                        displayOrder = 0,
                        scheduleType =
                            TaskScheduleTypeDb.DAILY,
                        recurrenceStartEpochDay =
                            DAY,
                        createdAtEpochMillis =
                            CLOCK.millis(),
                    )
                )

            assertEquals(
                TaskCompletionResult.SUCCESS,
                repository.complete(
                    taskId = taskId,
                    scheduledEpochDay = DAY,
                    completionTimestampMillis =
                        CLOCK.millis(),
                ),
            )

            assertTrue(
                repository.archiveTask(
                    taskId = taskId,
                    timestampMillis =
                        CLOCK.millis() + 1L,
                )
            )
        }
    }

    private fun openArchivedTasks() {
        composeRule
            .onNodeWithText("View all tasks")
            .performClick()

        waitForTag(
            HistoryTags.ALL_TASKS
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.ARCHIVED_TOGGLE
            )
            .performClick()

        waitForTag(
            HistoryTags.task(taskId)
        )
    }

    private fun deleteTask() {
        composeRule
            .onNodeWithTag(
                HistoryTags.task(taskId)
            )
            .performClick()

        waitForTag(
            HistoryTags.deleteTask(taskId)
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.deleteTask(taskId)
            )
            .performClick()

        waitForTag(
            HistoryTags.CONFIRM_DELETE
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.CONFIRM_DELETE
            )
            .performClick()

        waitForNoTag(
            HistoryTags.CONFIRM_DELETE
        )

        waitForNoTag(
            HistoryTags.task(taskId)
        )

        waitForNoTag(
            HistoryTags.deleteTask(taskId)
        )
    }

    private fun verifyDeletion() {
        runBlocking {
            assertNull(
                repository.getTask(taskId)
            )

            assertTrue(
                repository.observeArchivedTasks()
                    .first()
                    .isEmpty()
            )

            assertTrue(
                repository.observeLogs()
                    .first()
                    .isEmpty()
            )
        }
    }

    private fun waitForTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForNoTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private companion object {
        const val TASK_NAME =
            "Archived test task"

        val DAY =
            LocalDate.of(2026, 8, 23)
                .toEpochDay()

        val CLOCK: Clock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-23T17:00:00Z"
                ),
                ZoneId.of(
                    "America/Chicago"
                ),
            )
    }
}