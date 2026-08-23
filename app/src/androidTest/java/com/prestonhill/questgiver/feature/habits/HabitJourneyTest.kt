package com.prestonhill.questgiver.feature.habits

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.repository.HabitRepository
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: HabitViewModel
    private lateinit var viewModelStore: ViewModelStore

    private var habitId = 0L

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder<QuestGiverDatabase>(
                context
            )
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        repository = HabitRepository(database)

        val appDayCalculator =
            AppDayCalculator(
                dayBoundary = LocalTime.MIDNIGHT,
                zoneId = ZoneId.systemDefault()
            )

        val factory =
            HabitViewModelFactory(
                repository = repository,
                appDayCalculator = appDayCalculator,
                scheduleCalculator =
                    HabitScheduleCalculator(
                        appDayCalculator = appDayCalculator,
                        weekStart = DayOfWeek.MONDAY
                    )
            )

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory = factory
            )[HabitViewModel::class.java]

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()

            MaterialTheme {
                HabitScreen(
                    uiState = state,
                    onAction = viewModel::onAction
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
    fun completeHabitJourney() {
        createHabit()
        completeAndReverse()
        editAndHide()
        revealHiddenHabit()
        archiveAndRestore()
        deleteHabit()

        runBlocking {
            assertTrue(
                repository.observeActiveHabits()
                    .first()
                    .isEmpty()
            )

            assertTrue(
                repository.observeArchivedHabits()
                    .first()
                    .isEmpty()
            )

            assertTrue(
                repository.observeAllHabitLogs()
                    .first()
                    .isEmpty()
            )
        }
    }

    private fun createHabit() {
        composeRule
            .onNodeWithTag(HabitTags.ADD)
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.NAME)
            .performTextInput(ORIGINAL_NAME)

        composeRule
            .onNodeWithTag(HabitTags.SAVE)
            .performClick()

        waitForText(ORIGINAL_NAME)

        habitId = runBlocking {
            repository.observeActiveHabits()
                .first()
                .single()
                .id
        }
    }

    private fun completeAndReverse() {
        composeRule
            .onNodeWithTag(
                HabitTags.completion(habitId)
            )
            .performClick()

        waitForText("S1")

        composeRule
            .onNodeWithTag(
                HabitTags.completion(habitId)
            )
            .performClick()

        waitForText("S0")
    }

    private fun editAndHide() {
        composeRule
            .onNodeWithTag(HabitTags.row(habitId))
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.EDIT)
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.NAME)
            .performTextReplacement(EDITED_NAME)

        composeRule
            .onNodeWithTag(
                HabitTags.visibility(
                    HabitScheduleVisibility.HIDE_AFTER_TARGET
                )
            )
            .performScrollTo()
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.SAVE)
            .performClick()

        waitForText(EDITED_NAME)

        composeRule
            .onNodeWithTag(
                HabitTags.completion(habitId)
            )
            .performClick()

        waitForNoText(EDITED_NAME)

        composeRule
            .onNodeWithText(EDITED_NAME)
            .assertDoesNotExist()
    }

    private fun revealHiddenHabit() {
        composeRule
            .onNodeWithTag(
                HabitTags.hidden(HabitCategory.ANYTIME)
            )
            .performClick()

        waitForText(EDITED_NAME)
    }

    private fun archiveAndRestore() {
        composeRule
            .onNodeWithTag(HabitTags.row(habitId))
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.ARCHIVE)
            .performClick()

        waitForNoText(EDITED_NAME)

        composeRule
            .onNodeWithTag(HabitTags.ARCHIVED)
            .performClick()

        composeRule
            .onNodeWithTag(
                HabitTags.restore(habitId)
            )
            .performClick()

        waitForText(EDITED_NAME)
    }

    private fun deleteHabit() {
        composeRule
            .onNodeWithTag(HabitTags.row(habitId))
            .performClick()

        composeRule
            .onNodeWithTag(
                HabitTags.delete(habitId)
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                HabitTags.CONFIRM_DELETE
            )
            .performClick()

        waitForNoText(EDITED_NAME)
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

    private companion object {
        const val ORIGINAL_NAME = "Evening walk"
        const val EDITED_NAME = "Morning walk"
    }
}