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
import androidx.compose.ui.test.onAllNodesWithTag
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.repository.HabitRepository
import java.time.ZoneId
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.prestonhill.questgiver.core.settings.AppSettings
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import com.prestonhill.questgiver.data.local.database.HABIT_DISPLAY_SECTION_CALLBACK
import com.prestonhill.questgiver.data.local.database.entity.DefaultHabitDisplaySections
import kotlinx.coroutines.flow.flowOf

class HabitJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: QuestGiverDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: HabitViewModel
    private lateinit var viewModelStore: ViewModelStore

    private lateinit var habitSectionId: String

    private var habitId = 0L

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        val clock =
            Clock.fixed(
                Instant.parse("2026-08-23T17:00:00Z"),
                ZoneId.of("America/Chicago"),
            )

        database =
            Room.inMemoryDatabaseBuilder<
                    QuestGiverDatabase
                    >(context)
                .setDriver(
                    AndroidSQLiteDriver()
                )
                .setQueryCoroutineContext(
                    Dispatchers.IO
                )
                .addCallback(
                    HABIT_DISPLAY_SECTION_CALLBACK
                )
                .build()

        repository = HabitRepository(database)

        val factory =
            HabitViewModelFactory(
                repository = repository,
                settings = flowOf(AppSettings()),
                clock = clock,
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
        manageDisplaySection()
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

    private fun createHabit() {
        composeRule
            .onNodeWithTag(HabitTags.ADD)
            .performClick()

        composeRule
            .onNodeWithTag(HabitTags.NAME)
            .performTextInput(ORIGINAL_NAME)

        composeRule
            .onNodeWithTag(
                HabitTags.NEW_SECTION
            )
            .performScrollTo()
            .performClick()

        composeRule
            .onNodeWithTag(
                HabitTags.NEW_SECTION_NAME
            )
            .performScrollTo()
            .performTextInput(
                ORIGINAL_SECTION_NAME
            )

        composeRule
            .onNodeWithTag(HabitTags.SAVE)
            .performClick()

        waitForText(ORIGINAL_NAME)

        val habit =
            runBlocking {
                repository
                    .observeActiveHabits()
                    .first()
                    .single()
            }

        habitId = habit.id
        habitSectionId =
            habit.displaySectionId
    }

    private fun manageDisplaySection() {
        val originalSectionId =
            habitSectionId

        /*
         * AppShell owns the Sections button and has
         * its own Compose test, so open the manager
         * through the ViewModel here.
         */
        composeRule.runOnIdle {
            viewModel.onAction(
                HabitAction.ShowSectionManager
            )
        }

        waitForTag(
            HabitTags.SECTION_MANAGER
        )

        composeRule
            .onNodeWithTag(
                HabitTags.moveSectionUp(
                    originalSectionId
                )
            )
            .performClick()

        runBlocking {
            repository
                .observeDisplaySections()
                .first { sections ->
                    val sectionIndex =
                        sections.indexOfFirst {
                            it.id ==
                                    originalSectionId
                        }

                    val uncategorizedIndex =
                        sections.indexOfFirst {
                            it.id ==
                                    DefaultHabitDisplaySections
                                        .UNCATEGORIZED_ID
                        }

                    sectionIndex >= 0 &&
                            sectionIndex <
                            uncategorizedIndex
                }
        }

        composeRule
            .onNodeWithTag(
                HabitTags.sectionRow(
                    originalSectionId
                )
            )
            .performTouchInput {
                longClick()
            }

        waitForTag(
            HabitTags.SECTION_NAME
        )

        composeRule
            .onNodeWithTag(
                HabitTags.SECTION_NAME
            )
            .performTextReplacement(
                EDITED_SECTION_NAME
            )

        composeRule
            .onNodeWithTag(
                HabitTags.SAVE_SECTION
            )
            .performClick()

        waitForTag(
            HabitTags.SECTION_MANAGER
        )

        waitForText(
            EDITED_SECTION_NAME
        )

        composeRule
            .onNodeWithTag(
                HabitTags.sectionRow(
                    originalSectionId
                )
            )
            .performTouchInput {
                longClick()
            }

        composeRule
            .onNodeWithTag(
                HabitTags.deleteSection(
                    originalSectionId
                )
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                HabitTags.CONFIRM_SECTION_DELETE
            )
            .performClick()

        waitForTag(
            HabitTags.SECTION_MANAGER
        )

        runBlocking {
            val moved =
                repository
                    .observeActiveHabits()
                    .first { habits ->
                        habits.any { habit ->
                            habit.id == habitId &&
                                    habit.displaySectionId ==
                                    DefaultHabitDisplaySections
                                        .UNCATEGORIZED_ID
                        }
                    }
                    .single {
                        it.id == habitId
                    }

            assertEquals(
                DefaultHabitDisplaySections
                    .UNCATEGORIZED_ID,
                moved.displaySectionId,
            )

            assertTrue(
                repository
                    .observeDisplaySections()
                    .first()
                    .none {
                        it.id == originalSectionId
                    }
            )
        }

        habitSectionId =
            DefaultHabitDisplaySections
                .UNCATEGORIZED_ID

        composeRule
            .onNodeWithText("Close")
            .performClick()

        waitForText(ORIGINAL_NAME)
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
        val hiddenTag =
            HabitTags.hidden(
                habitSectionId
            )

        composeRule
            .onNodeWithTag(
                hiddenTag,
                useUnmergedTree = true,
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

        const val ORIGINAL_SECTION_NAME =
            "Training"

        const val EDITED_SECTION_NAME =
            "Exercise"
    }
}