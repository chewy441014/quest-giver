package com.prestonhill.questgiver.feature.history

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.local.database.entity.TaskEntity
import com.prestonhill.questgiver.data.local.database.entity.TaskScheduleTypeDb
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.repository.TaskCompletionResult
import com.prestonhill.questgiver.data.repository.TaskRepository
import com.prestonhill.questgiver.data.repository.HabitRepository
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.entity.DefaultHabitDisplaySections
import com.prestonhill.questgiver.data.local.database.entity.HabitEntity
import com.prestonhill.questgiver.data.local.database.entity.HabitScheduleTypeDb
import com.prestonhill.questgiver.data.repository.CompletionChangeResult
import java.time.LocalTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText

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

    private lateinit var nutritionRepository:
            NutritionRepository

    private lateinit var habitRepository:
            HabitRepository
    private lateinit var settings:
            MutableStateFlow<AppSettings>


    private var taskId = 0L

    private var habitId = 0L
    private var excludedHabitId = 0L

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

        habitRepository =
            HabitRepository(database)

        runBlocking {
            DefaultHabitDisplaySections.all
                .forEach {
                    database.habitDao()
                        .insertDisplaySection(it)
                }
        }

        repository = TaskRepository(database)

        settings =
            MutableStateFlow(
                AppSettings()
            )


        nutritionRepository =
            NutritionRepository(database)

        val factory =
            HistoryViewModelFactory(
                repository = repository,
                nutritionRepository =
                    nutritionRepository,
                habitRepository =
                    habitRepository,
                settings = settings,
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

    @Test
    fun habitHistoryJourney(): Unit {
        seedHabitHistory()
        openHabitHistory()
        verifyActiveHabitHistory()
        correctMultipleCompletions()
        archiveHabit()
        openArchivedHabitHistory()
        verifyArchivedHabitHistory()
    }

    @Test
    fun taskCalendarJourney(): Unit {
        seedCalendarTask()
        filterCalendarToRecurringTask()
        inspectCalendarDay()
        removeCompletionFromHistory()
        verifyCalendarCleared()
    }

    private fun seedHabitHistory() {
        runBlocking {
            habitId =
                habitRepository.createHabit(
                    HabitEntity(
                        name = HABIT_NAME,
                        displaySectionId =
                            DefaultHabitDisplaySections
                                .ANYTIME_ID,
                        historyCategory =
                            HABIT_CATEGORY,
                        displayOrder = 0,
                        isVisibleInHistory = true,
                        allowsMultipleCompletions =
                            true,
                        scheduleType =
                            HabitScheduleTypeDb.DAILY,
                        scheduleTarget = 1,
                        createdAtEpochMillis =
                            habitTimestamp(
                                HABIT_CREATED_DATE,
                                12,
                            ),
                    )
                )

            excludedHabitId =
                habitRepository.createHabit(
                    HabitEntity(
                        name = EXCLUDED_HABIT_NAME,
                        displaySectionId =
                            DefaultHabitDisplaySections
                                .ANYTIME_ID,
                        displayOrder = 1,
                        isVisibleInHistory = false,
                        allowsMultipleCompletions =
                            true,
                        scheduleType =
                            HabitScheduleTypeDb.DAILY,
                        scheduleTarget = 1,
                        createdAtEpochMillis =
                            habitTimestamp(
                                HABIT_CREATED_DATE,
                                13,
                            ),
                    )
                )

            addHabitCompletion(
                date = SINGLE_COMPLETION_DATE,
                hour = 10,
            )

            addHabitCompletion(
                date = DOUBLE_COMPLETION_DATE,
                hour = 10,
            )

            addHabitCompletion(
                date = DOUBLE_COMPLETION_DATE,
                hour = 11,
            )
        }
    }

    private fun openHabitHistory() {
        composeRule
            .onNodeWithTag(
                HistoryTags.tab(
                    HistorySection.HABITS
                )
            )
            .performClick()

        waitForTag(
            HistoryTags.HABIT_DASHBOARD
        )

        waitForHabitState {
            it.habits.performance.any {
                    performance ->
                performance.habitId == habitId
            }
        }
    }

    private fun verifyActiveHabitHistory() {
        val performanceTag =
            HistoryTags.habitPerformance(
                habitId
            )

        scrollHabitHistoryTo(
            performanceTag
        )

        composeRule
            .onNodeWithTag(performanceTag)
            .assertIsDisplayed()

        composeRule
            .onNode(
                hasText(HABIT_NAME) and
                        hasAnyAncestor(
                            hasTestTag(
                                performanceTag
                            )
                        )
            )
            .assertIsDisplayed()

        scrollHabitHistoryTo(
            HistoryTags
                .HABIT_COMPLETION_CHART
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .HABIT_COMPLETION_PLOT
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .habitCompletionChartFilter(
                        habitId
                    )
            )
            .performScrollTo()
            .assertIsDisplayed()

        scrollHabitHistoryTo(
            HistoryTags.HABIT_STAMP_CALENDAR
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.habitStampFilter(
                    HABIT_CATEGORY_KEY
                )
            )
            .performScrollTo()
            .assertIsDisplayed()

        waitForHabitState { state ->
            val chartCount =
                state.habits
                    .completionChart
                    .series
                    .singleOrNull {
                        it.habitId == habitId
                    }
                    ?.points
                    ?.singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.completionCount

            val hasStamp =
                state.habits
                    .stampCalendar
                    .days
                    .singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.stampKeys
                    ?.contains(
                        HABIT_CATEGORY_KEY
                    ) == true

            chartCount == 2 &&
                    hasStamp &&
                    state.habits.performance.none {
                        it.habitId ==
                                excludedHabitId
                    } &&
                    state.habits
                        .completionChart
                        .series
                        .none {
                            it.habitId ==
                                    excludedHabitId
                        }
        }
    }

    private fun correctMultipleCompletions() {
        runBlocking {
            removeHabitCompletion(
                DOUBLE_COMPLETION_DATE
            )
        }

        /*
         * One completion remains, so the daily
         * stamp remains while the chart falls to 1.
         */
        waitForHabitState { state ->
            val count =
                state.habits
                    .completionChart
                    .series
                    .singleOrNull {
                        it.habitId == habitId
                    }
                    ?.points
                    ?.singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.completionCount

            val hasStamp =
                state.habits
                    .stampCalendar
                    .days
                    .singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.stampKeys
                    ?.contains(
                        HABIT_CATEGORY_KEY
                    ) == true

            count == 1 && hasStamp
        }

        runBlocking {
            removeHabitCompletion(
                DOUBLE_COMPLETION_DATE
            )
        }

        waitForHabitState { state ->
            val count =
                state.habits
                    .completionChart
                    .series
                    .singleOrNull {
                        it.habitId == habitId
                    }
                    ?.points
                    ?.singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.completionCount

            val hasStamp =
                state.habits
                    .stampCalendar
                    .days
                    .singleOrNull {
                        it.date ==
                                DOUBLE_COMPLETION_DATE
                    }
                    ?.stampKeys
                    ?.contains(
                        HABIT_CATEGORY_KEY
                    ) == true

            count == 0 && !hasStamp
        }
    }

    private fun archiveHabit() {
        runBlocking {
            assertTrue(
                habitRepository.archiveHabit(
                    habitId = habitId,
                    timestampMillis =
                        CLOCK.millis(),
                )
            )
        }

        waitForHabitState { state ->
            state.habits.performance.none {
                it.habitId == habitId
            } &&
                    state.habits
                        .completionChart
                        .series
                        .none {
                            it.habitId == habitId
                        }
        }
    }

    private fun openArchivedHabitHistory() {
        scrollHabitHistoryTo(
            HistoryTags.HABIT_ARCHIVED_TOGGLE
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.HABIT_ARCHIVED_TOGGLE
            )
            .performClick()

        waitForHabitState {
            it.habits.showArchivedHabits &&
                    it.habits.performance.any {
                            performance ->
                        performance.habitId ==
                                habitId
                    }
        }
    }

    private fun verifyArchivedHabitHistory() {
        val performanceTag =
            HistoryTags.habitPerformance(
                habitId
            )

        scrollHabitHistoryTo(
            performanceTag
        )

        composeRule
            .onNodeWithTag(performanceTag)
            .assertIsDisplayed()

        scrollHabitHistoryTo(
            HistoryTags
                .HABIT_COMPLETION_CHART
        )

        composeRule
            .onNodeWithTag(
                HistoryTags
                    .habitCompletionChartFilter(
                        habitId
                    )
            )
            .performScrollTo()
            .assertIsDisplayed()

        scrollHabitHistoryTo(
            HistoryTags.HABIT_STAMP_CALENDAR
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.habitStampFilter(
                    HABIT_CATEGORY_KEY
                )
            )
            .performScrollTo()
            .assertIsDisplayed()

        waitForHabitState { state ->
            state.habits.performance.any {
                it.habitId == habitId
            } &&
                    state.habits
                        .completionChart
                        .series
                        .any {
                            it.habitId == habitId
                        } &&
                    state.habits.performance.none {
                        it.habitId ==
                                excludedHabitId
                    }
        }
    }

    private suspend fun addHabitCompletion(
        date: LocalDate,
        hour: Int,
    ) {
        val day =
            habitDayCalculator()
                .forDate(date)

        assertEquals(
            CompletionChangeResult.SUCCESS,
            habitRepository.addCompletion(
                habitId = habitId,
                completionTimestampMillis =
                    habitTimestamp(
                        date,
                        hour,
                    ),
                appDayStartMillis =
                    day.startTimestampMillis,
                appDayEndMillis =
                    day.endTimestampMillis,
                recordedTimestampMillis =
                    CLOCK.millis(),
            ),
        )
    }

    private suspend fun removeHabitCompletion(
        date: LocalDate,
    ) {
        val day =
            habitDayCalculator()
                .forDate(date)

        assertEquals(
            CompletionChangeResult.SUCCESS,
            habitRepository.removeCompletion(
                habitId = habitId,
                appDayStartMillis =
                    day.startTimestampMillis,
                appDayEndMillis =
                    day.endTimestampMillis,
                recordedTimestampMillis =
                    CLOCK.millis(),
            ),
        )
    }

    private fun habitDayCalculator() =
        AppDayCalculator(
            dayBoundary =
                settings.value.dayBoundary,
            zoneId = CLOCK.zone,
        )

    private fun habitTimestamp(
        date: LocalDate,
        hour: Int,
    ): Long =
        date.atTime(
            LocalTime.of(hour, 0)
        )
            .atZone(CLOCK.zone)
            .toInstant()
            .toEpochMilli()

    private fun scrollHabitHistoryTo(
        tag: String,
    ) {
        composeRule
            .onNodeWithTag(
                HistoryTags.HABIT_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(tag)
            )
    }

    private fun waitForHabitState(
        condition:
            (HistoryScreenUiState) ->
        Boolean,
    ) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            condition(
                viewModel.uiState.value
            )
        }
    }

    private fun seedCalendarTask() {
        runBlocking {
            taskId =
                repository.createTask(
                    TaskEntity(
                        name =
                            CALENDAR_TASK_NAME,
                        category =
                            CALENDAR_CATEGORY,
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
                    recordedTimestampMillis =
                        CLOCK.millis(),
                ),
            )
        }

        waitForTag(
            HistoryTags.taskStampFilter(
                recurringStampKey
            )
        )

        waitForTag(
            HistoryTags.taskStampFilter(
                CATEGORY_STAMP_KEY
            )
        )
    }
    private fun filterCalendarToRecurringTask() {
        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags.taskStampGroup(
                        "Categories"
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampGroup(
                    "Categories"
                )
            )
            .performClick()
    }

    private fun inspectCalendarDay() {
        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_DASHBOARD
            )
            .performScrollToNode(
                hasTestTag(
                    HistoryTags.taskStampDay(
                        CALENDAR_DATE
                    )
                )
            )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskStampDay(
                    CALENDAR_DATE
                )
            )
            .performClick()

        waitForTag(
            HistoryTags.taskDayStamp(
                recurringStampKey
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskDayStamp(
                    recurringStampKey
                )
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                HistoryTags.taskDayStamp(
                    CATEGORY_STAMP_KEY
                )
            )
            .assertDoesNotExist()

        composeRule
            .onNodeWithTag(
                HistoryTags.TASK_STAMP_DAY_CLOSE
            )
            .performClick()
    }

    private fun removeCompletionFromHistory() {
        composeRule
            .onNodeWithText("View all tasks")
            .performScrollTo()
            .performClick()

        waitForTag(HistoryTags.ALL_TASKS)

        composeRule
            .onNodeWithTag(
                HistoryTags.task(taskId)
            )
            .performClick()

        waitForTag(
            HistoryTags.taskCompletion(
                taskId
            )
        )

        composeRule
            .onNodeWithTag(
                HistoryTags.taskCompletion(
                    taskId
                )
            )
            .performClick()

        /*
         * Wait for the repository operation rather
         * than assuming Compose idleness means the
         * ViewModel coroutine has completed.
         */
        runBlocking {
            repository.observeLogs()
                .first { logs ->
                    logs.any {
                        it.taskId == taskId &&
                                it.delta == -1
                    }
                }
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Close")
            .performClick()

        composeRule
            .onNodeWithText("Back")
            .performClick()

        waitForTag(
            HistoryTags.TASK_STAMP_CALENDAR
        )
    }

    private fun verifyCalendarCleared() {
        waitForNoTag(
            HistoryTags.taskStampFilter(
                recurringStampKey
            )
        )

        waitForNoTag(
            HistoryTags.taskStampFilter(
                CATEGORY_STAMP_KEY
            )
        )

        runBlocking {
            val logs =
                repository.observeLogs()
                    .first {
                        it.size == 2
                    }

            assertEquals(
                1,
                logs.count {
                    it.delta == 1
                },
            )

            assertEquals(
                1,
                logs.count {
                    it.delta == -1
                },
            )
        }
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

    private val recurringStampKey: String
        get() = "task:$taskId"

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

        const val CALENDAR_TASK_NAME =
            "Daily planning"

        const val CALENDAR_CATEGORY =
            "General"

        const val CATEGORY_STAMP_KEY =
            "category:general"

        val CALENDAR_DATE: LocalDate =
            LocalDate.ofEpochDay(DAY)

        const val HABIT_NAME =
            "Drink water"

        const val EXCLUDED_HABIT_NAME =
            "Private habit"

        const val HABIT_CATEGORY =
            "Hydration"

        const val HABIT_CATEGORY_KEY =
            "habit-category:hydration"

        val HABIT_CREATED_DATE:
                LocalDate =
            CALENDAR_DATE.minusDays(4)

        val SINGLE_COMPLETION_DATE:
                LocalDate =
            CALENDAR_DATE.minusDays(2)

        val DOUBLE_COMPLETION_DATE:
                LocalDate =
            CALENDAR_DATE.minusDays(1)

    }
}