package com.prestonhill.questgiver.feature.tasks

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.repository.TaskRepository
import java.time.Clock
import java.time.Instant
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
class TaskJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            TaskRepository

    private lateinit var viewModel:
            TaskViewModel

    private lateinit var viewModelStore:
            ViewModelStore

    private var taskId = 0L

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        val clock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-23T17:00:00Z"
                ),
                ZoneId.of("America/Chicago"),
            )

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
            TaskViewModelFactory(
                repository = repository,
                settings = flowOf(AppSettings()),
                clock = clock,
            )

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory,
            )[TaskViewModel::class.java]

        composeRule.setContent {
            val state by
            viewModel.uiState.collectAsState()

            MaterialTheme {
                TaskScreen(
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
    fun taskJourney(): Unit {
        createTask()
        editTask()
        completeTask()
        revealTask()
        deleteTask()
        verifyHistory()
    }

    private fun createTask() {
        composeRule
            .onNodeWithTag(TaskTags.ADD)
            .performClick()

        composeRule
            .onNodeWithTag(
                TaskTags.EDITOR_NAME
            )
            .performTextInput(ORIGINAL_NAME)

        composeRule
            .onNodeWithTag(
                TaskTags.EDITOR_SAVE
            )
            .performClick()

        waitForText(ORIGINAL_NAME)

        taskId = runBlocking {
            repository.observeTasks()
                .first { it.isNotEmpty() }
                .single()
                .id
        }
    }

    private fun editTask() {
        composeRule
            .onNodeWithTag(
                TaskTags.row(taskId)
            )
            .performClick()

        composeRule
            .onNodeWithTag(TaskTags.EDIT)
            .performClick()

        composeRule
            .onNodeWithTag(
                TaskTags.EDITOR_NAME
            )
            .performTextReplacement(EDITED_NAME)

        composeRule
            .onNodeWithTag(
                TaskTags.EDITOR_SAVE
            )
            .performClick()

        waitForText(EDITED_NAME)
        waitForNoText(ORIGINAL_NAME)
    }

    private fun completeTask() {
        composeRule
            .onNodeWithTag(
                TaskTags.check(taskId)
            )
            .performClick()

        waitForNoText(EDITED_NAME)
        waitForTag(TaskTags.HIDDEN_TOGGLE)
    }

    private fun revealTask() {
        composeRule
            .onNodeWithTag(
                TaskTags.HIDDEN_TOGGLE
            )
            .performClick()

        waitForText(EDITED_NAME)

        composeRule
            .onNodeWithTag(
                TaskTags.check(taskId)
            )
            .assertIsEnabled()
    }

    private fun deleteTask() {
        composeRule
            .onNodeWithTag(
                TaskTags.row(taskId)
            )
            .performClick()

        composeRule
            .onNodeWithText("Delete")
            .performClick()

        composeRule
            .onNodeWithTag(
                TaskTags.DELETE_TASK
            )
            .performClick()

        waitForNoText(EDITED_NAME)
    }

    private fun verifyHistory() {
        runBlocking {
            assertTrue(
                repository.observeTasks()
                    .first()
                    .isEmpty()
            )

            val logs =
                repository.observeLogs()
                    .first()

            assertEquals(1, logs.size)

            val log = logs.single()

            assertNull(log.taskId)
            assertEquals(
                EDITED_NAME,
                log.taskNameSnapshot,
            )
            assertEquals(1, log.delta)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForNoText(text: String) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val ORIGINAL_NAME =
            "Evening cleanup"

        const val EDITED_NAME =
            "Kitchen cleanup"
    }
}