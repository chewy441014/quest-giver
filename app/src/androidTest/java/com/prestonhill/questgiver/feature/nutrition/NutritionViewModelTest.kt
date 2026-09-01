package com.prestonhill.questgiver.feature.nutrition

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.BoundaryTimer
import com.prestonhill.questgiver.data.local.database.QuestGiverDatabase
import com.prestonhill.questgiver.data.repository.FoodLogDraft
import com.prestonhill.questgiver.data.repository.NutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.repository.NutritionValuesInput
import com.prestonhill.questgiver.data.repository.ComposedNutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionComponentDraft
import com.prestonhill.questgiver.data.repository.NutritionItemRemovalResult
import org.junit.Assert.assertNull
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class NutritionViewModelTest {
    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            NutritionRepository

    private lateinit var settings:
            MutableStateFlow<AppSettings>

    private lateinit var clock:
            TestClock

    private lateinit var timer:
            TestTimer

    private lateinit var viewModel:
            NutritionViewModel

    private lateinit var viewModelStore:
            ViewModelStore

    private lateinit var viewModelJob: Job

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

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
                .build()

        repository =
            NutritionRepository(database)

        settings =
            MutableStateFlow(
                AppSettings()
            )

        clock =
            TestClock(
                initialInstant =
                    Instant.parse(
                        "2026-08-30T17:00:00Z"
                    ),
                testZone =
                    ZoneId.of(
                        "America/Chicago"
                    ),
            )

        timer = TestTimer()
        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory =
                    NutritionViewModelFactory(
                        repository = repository,
                        settings = settings,
                        clock = clock,
                        timer = timer,
                    ),
            )[NutritionViewModel::class.java]

        viewModelJob =
            requireNotNull(
                viewModel.viewModelScope
                    .coroutineContext[Job]
            )
    }

    @After
    fun close(): Unit =
        runBlocking {
            viewModelStore.clear()
            viewModelJob.join()
            database.close()
        }

    @Test
    fun currentDayShowsLogsAndTotals(): Unit =
        runBlocking {
            val itemId =
                addItem(
                    calories = 100.0,
                    protein = 10.0,
                )

            val logId =
                addLog(
                    itemId = itemId,
                    date = CURRENT_DATE,
                    hour = 12,
                    weightGrams = 200.0,
                )

            val state =
                awaitState {
                    it.logs.any { row ->
                        row.logId == logId
                    }
                }

            assertEquals(
                CURRENT_DATE,
                state.selectedDate,
            )

            assertEquals(
                CURRENT_DATE,
                state.currentDate,
            )

            assertTrue(state.isCurrentDay)

            assertEquals(
                200.0,
                state.totalCalories,
                TOLERANCE,
            )

            assertEquals(
                20.0,
                state.totalProteinGrams,
                TOLERANCE,
            )

            assertEquals(
                1_500.0,
                state.calorieGoal,
                TOLERANCE,
            )

            assertEquals(
                40.0,
                state.proteinGoalGrams,
                TOLERANCE,
            )
        }

    @Test
    fun addEditorUsesSelectedDateAndActiveItems(): Unit =
        runBlocking {
            val activeId = addItem()
            val archivedId = addItem()

            repository.createComposedItem(
                ComposedNutritionItemDraft(
                    name = "Archived parent",
                    components =
                        listOf(
                            NutritionComponentDraft(
                                itemId = archivedId,
                                gramsPer100g =
                                    100.0,
                            )
                        ),
                )
            )

            assertEquals(
                NutritionItemRemovalResult.ARCHIVED,
                repository.removeItem(
                    itemId = archivedId,
                    timestampMillis =
                        clock.millis(),
                ),
            )

            viewModel.onAction(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            )

            awaitState {
                it.selectedDate == PAST_DATE
            }

            viewModel.onAction(
                NutritionAction.OpenAddLog
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.logEditor != null
                    }.logEditor
                )

            assertEquals(
                PAST_DATE,
                editor.date,
            )

            assertTrue(
                editor.itemOptions.any {
                    it.id == activeId
                }
            )

            assertTrue(
                editor.itemOptions.none {
                    it.id == archivedId
                }
            )

            assertEquals(
                LocalTime.of(12, 0),
                editor.time,
            )

            assertFalse(editor.isEditing)
            assertFalse(editor.canSave)
        }

    @Test
    fun addEditorSavesLogForSelectedDay(): Unit =
        runBlocking {
            val itemId = addItem()

            viewModel.onAction(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            )

            awaitState {
                it.selectedDate == PAST_DATE
            }

            viewModel.onAction(
                NutritionAction.OpenAddLog
            )

            awaitState {
                it.logEditor?.itemOptions
                    ?.any { option ->
                        option.id == itemId
                    } == true
            }

            viewModel.onAction(
                NutritionAction.SelectLogItem(
                    itemId
                )
            )

            viewModel.onAction(
                NutritionAction.ChangeLogWeight(
                    "125.5"
                )
            )

            viewModel.onAction(
                NutritionAction.ChangeLogTime(
                    LocalTime.of(8, 30)
                )
            )

            viewModel.onAction(
                NutritionAction.SaveLog
            )

            val state =
                awaitState {
                    it.logEditor == null &&
                            it.logs.any { row ->
                                row.itemId == itemId
                            }
                }

            val row =
                state.logs.single {
                    it.itemId == itemId
                }

            assertEquals(
                PAST_DATE,
                state.selectedDate,
            )

            assertEquals(
                125.5,
                row.weightGrams,
                TOLERANCE,
            )

            assertEquals(
                LocalTime.of(8, 30),
                row.consumedTime,
            )

            assertNull(state.destination)
        }

    @Test
    fun editLogCanChangeItemWeightAndTime(): Unit =
        runBlocking {
            val firstItemId = addItem()
            val secondItemId = addItem()

            val logId =
                addLog(
                    itemId = firstItemId,
                    date = CURRENT_DATE,
                    hour = 10,
                )

            awaitState {
                it.logs.any { row ->
                    row.logId == logId
                }
            }

            viewModel.onAction(
                NutritionAction.InspectLog(
                    logId
                )
            )

            val opened =
                requireNotNull(
                    awaitState {
                        it.logEditor?.logId ==
                                logId
                    }.logEditor
                )

            assertTrue(opened.isEditing)

            assertEquals(
                firstItemId,
                opened.selectedItemId,
            )

            assertEquals(
                "100",
                opened.weightText,
            )

            assertEquals(
                LocalTime.of(10, 0),
                opened.time,
            )

            viewModel.onAction(
                NutritionAction.SelectLogItem(
                    secondItemId
                )
            )

            viewModel.onAction(
                NutritionAction.ChangeLogWeight(
                    "75"
                )
            )

            viewModel.onAction(
                NutritionAction.ChangeLogTime(
                    LocalTime.of(11, 30)
                )
            )

            viewModel.onAction(
                NutritionAction.SaveLog
            )

            val state =
                awaitState {
                    it.logEditor == null &&
                            it.logs.any { row ->
                                row.logId == logId &&
                                        row.itemId ==
                                        secondItemId
                            }
                }

            val row =
                state.logs.single {
                    it.logId == logId
                }

            assertEquals(
                75.0,
                row.weightGrams,
                TOLERANCE,
            )

            assertEquals(
                LocalTime.of(11, 30),
                row.consumedTime,
            )
        }

    @Test
    fun archivedLogItemIsRetainedForEditing(): Unit =
        runBlocking {
            val itemId = addItem()

            val logId =
                addLog(
                    itemId = itemId,
                    date = CURRENT_DATE,
                    hour = 12,
                )

            repository.createComposedItem(
                ComposedNutritionItemDraft(
                    name = "Parent item",
                    components =
                        listOf(
                            NutritionComponentDraft(
                                itemId = itemId,
                                gramsPer100g =
                                    100.0,
                            )
                        ),
                )
            )

            assertEquals(
                NutritionItemRemovalResult.ARCHIVED,
                repository.removeItem(
                    itemId = itemId,
                    timestampMillis =
                        clock.millis(),
                ),
            )

            viewModel.onAction(
                NutritionAction.InspectLog(
                    logId
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.logEditor?.logId ==
                                logId
                    }.logEditor
                )

            val selectedOption =
                editor.itemOptions.single {
                    it.id == itemId
                }

            assertTrue(
                selectedOption.isArchived
            )

            assertEquals(
                itemId,
                editor.selectedItemId,
            )

            viewModel.onAction(
                NutritionAction.ChangeLogWeight(
                    "150"
                )
            )

            viewModel.onAction(
                NutritionAction.SaveLog
            )

            val state =
                awaitState {
                    it.logEditor == null &&
                            it.logs.any { row ->
                                row.logId == logId &&
                                        row.weightGrams ==
                                        150.0
                            }
                }

            assertTrue(
                state.logs.single {
                    it.logId == logId
                }.isItemArchived
            )
        }

    @Test
    fun editLogCanBeDeleted(): Unit =
        runBlocking {
            val itemId = addItem()

            val logId =
                addLog(
                    itemId = itemId,
                    date = CURRENT_DATE,
                    hour = 12,
                )

            awaitState {
                it.logs.any { row ->
                    row.logId == logId
                }
            }

            viewModel.onAction(
                NutritionAction.InspectLog(
                    logId
                )
            )

            awaitState {
                it.logEditor?.logId == logId
            }

            viewModel.onAction(
                NutritionAction
                    .RequestDeleteLog
            )

            awaitState {
                it.logEditor
                    ?.showDeleteConfirmation ==
                        true
            }

            viewModel.onAction(
                NutritionAction
                    .DismissDeleteLog
            )

            awaitState {
                it.logEditor
                    ?.showDeleteConfirmation ==
                        false
            }

            viewModel.onAction(
                NutritionAction
                    .RequestDeleteLog
            )

            awaitState {
                it.logEditor
                    ?.showDeleteConfirmation ==
                        true
            }

            viewModel.onAction(
                NutritionAction.DeleteLog
            )

            val state =
                awaitState {
                    it.logEditor == null &&
                            it.logs.none { row ->
                                row.logId == logId
                            }
                }

            assertNull(
                repository.getLog(logId)
            )

            assertNull(state.destination)
        }

    @Test
    fun manageFiltersActiveAndArchivedItems(): Unit =
        runBlocking {
            val activeId = addItem()
            val archivedId = addItem()

            repository.createComposedItem(
                composedDraft(
                    "Archive parent",
                    archivedId to 100.0,
                )
            )

            assertEquals(
                NutritionItemRemovalResult.ARCHIVED,
                repository.removeItem(
                    itemId = archivedId,
                    timestampMillis =
                        clock.millis(),
                )
            )

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            val active =
                awaitState { state ->
                    state.destination ==
                            NutritionDestination.Manage &&
                            state.manage.itemOptions.any {
                                it.id == activeId
                            } &&
                            state.manage.itemOptions.any {
                                it.id == archivedId
                            }
                }

            assertTrue(
                active.manage.visibleFoodGroups
                    .flatMap { it.versions }
                    .any { it.id == activeId }
            )

            assertTrue(
                active.manage.visibleFoodGroups
                    .flatMap { it.versions }
                    .none { it.id == archivedId }
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageArchiveFilter(
                        NutritionArchiveFilter
                            .ARCHIVED
                    )
            )

            val archived =
                awaitState {
                    it.manage.archiveFilter ==
                            NutritionArchiveFilter
                                .ARCHIVED
                }

            assertTrue(
                archived.manage.visibleFoodGroups
                    .flatMap { it.versions }
                    .any { it.id == archivedId }
            )

            assertTrue(
                archived.manage.visibleFoodGroups
                    .flatMap { it.versions }
                    .none { !it.isArchived }
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageArchiveFilter(
                        NutritionArchiveFilter.ALL
                    )
            )

            val all =
                awaitState {
                    it.manage.archiveFilter ==
                            NutritionArchiveFilter.ALL
                }

            val visibleIds =
                all.manage.visibleFoodGroups
                    .flatMap { it.versions }
                    .map { it.id }
                    .toSet()

            assertTrue(activeId in visibleIds)
            assertTrue(archivedId in visibleIds)
        }

    @Test
    fun manageControlsChangeAndReset(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageSearch(
                        "milk"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageSort(
                        NutritionItemSort.PROTEIN
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageMinimumProtein(
                        "20"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageMinimumProteinRatio(
                        "10"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeManageArchiveFilter(
                        NutritionArchiveFilter.ALL
                    )
            )

            val changed =
                awaitState {
                    it.manage.itemSearch ==
                            "milk" &&
                            it.manage.itemSort ==
                            NutritionItemSort.PROTEIN &&
                            it.manage
                                .minimumProteinText ==
                            "20" &&
                            it.manage
                                .minimumProteinRatioText ==
                            "10" &&
                            it.manage.archiveFilter ==
                            NutritionArchiveFilter.ALL
                }

            assertEquals(
                "milk",
                changed.manage.itemSearch,
            )

            viewModel.onAction(
                NutritionAction.ResetManageFilters
            )

            val reset =
                awaitState {
                    it.manage.itemSort ==
                            NutritionItemSort.RECENT &&
                            it.manage
                                .minimumProteinText
                                .isEmpty() &&
                            it.manage
                                .minimumProteinRatioText
                                .isEmpty() &&
                            it.manage.archiveFilter ==
                            NutritionArchiveFilter.ACTIVE
                }

            // Search is separate from filters.
            assertEquals(
                "milk",
                reset.manage.itemSearch,
            )
        }

    @Test
    fun missingLogCannotOpenEditor(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                NutritionAction.InspectLog(
                    Long.MAX_VALUE
                )
            )

            val state =
                awaitState {
                    it.operationError ==
                            "Food log could not be found."
                }

            assertNull(state.logEditor)
            assertNull(state.destination)
        }

    @Test
    fun manageDestinationCanBeDismissed(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            awaitState {
                it.destination ==
                        NutritionDestination.Manage
            }

            viewModel.onAction(
                NutritionAction
                    .DismissDestination
            )

            val state =
                awaitState {
                    it.destination == null
                }

            assertNull(state.destination)
            assertNull(state.logEditor)
        }

    @Test
    fun selectingPastDateChangesLogRange(): Unit =
        runBlocking {
            val itemId = addItem()

            val pastLogId =
                addLog(
                    itemId = itemId,
                    date = PAST_DATE,
                    hour = 9,
                )

            val currentLogId =
                addLog(
                    itemId = itemId,
                    date = CURRENT_DATE,
                    hour = 12,
                )

            awaitState {
                it.logs.any { row ->
                    row.logId == currentLogId
                }
            }

            viewModel.onAction(
                NutritionAction.OpenDatePicker
            )

            awaitState {
                it.showDatePicker
            }

            viewModel.onAction(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            )

            val state =
                awaitState {
                    it.selectedDate == PAST_DATE &&
                            it.logs.any { row ->
                                row.logId == pastLogId
                            }
                }

            assertFalse(state.showDatePicker)
            assertFalse(state.isCurrentDay)
            assertTrue(state.canSelectNextDay)

            assertEquals(
                listOf(pastLogId),
                state.logs.map { it.logId },
            )
        }

    @Test
    fun futureDateIsRejected(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            val futureDate =
                CURRENT_DATE.plusDays(1)

            viewModel.onAction(
                NutritionAction.SelectDate(
                    futureDate
                )
            )

            val rejected =
                awaitState {
                    it.operationError != null
                }

            assertEquals(
                CURRENT_DATE,
                rejected.selectedDate,
            )

            assertEquals(
                "Future nutrition dates are unavailable.",
                rejected.operationError,
            )

            viewModel.onAction(
                NutritionAction
                    .DismissOperationError
            )

            val dismissed =
                awaitState {
                    it.operationError == null
                }

            assertEquals(
                null,
                dismissed.operationError,
            )
        }

    @Test
    fun goalChangesUpdateProgress(): Unit =
        runBlocking {
            val itemId =
                addItem(
                    calories = 100.0,
                    protein = 10.0,
                )

            addLog(
                itemId = itemId,
                date = CURRENT_DATE,
                hour = 12,
            )

            awaitState {
                it.totalCalories == 100.0
            }

            settings.value =
                AppSettings(
                    calorieGoal = 200.0,
                    proteinGoalGrams = 20.0,
                )

            val state =
                awaitState {
                    it.calorieGoal == 200.0 &&
                            it.proteinGoalGrams ==
                            20.0
                }

            assertEquals(
                0.5f,
                state.calorieProgress,
                FLOAT_TOLERANCE,
            )

            assertEquals(
                0.5f,
                state.proteinProgress,
                FLOAT_TOLERANCE,
            )
        }

    @Test
    fun selectingFoodWithVersionsRequiresVersionChoice(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Milk",
                        versionLabel = "Store",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 100.0,
                                    proteinGrams =
                                        10.0,
                                ),
                    )
                )

            val secondId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Milk",
                        versionLabel = "Brand",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 120.0,
                                    proteinGrams =
                                        12.0,
                                ),
                    )
                )

            viewModel.onAction(
                NutritionAction.OpenAddLog
            )

            awaitState {
                it.logEditor
                    ?.visibleFoodGroups
                    ?.any { group ->
                        group.nameKey == "milk"
                    } == true
            }

            viewModel.onAction(
                NutritionAction.SelectLogFood(
                    "milk"
                )
            )

            val choosing =
                requireNotNull(
                    awaitState {
                        it.logEditor
                            ?.versionGroupNameKey ==
                                "milk"
                    }.logEditor
                )

            assertEquals(
                listOf(firstId, secondId),
                choosing.versionChoices
                    .map { it.id },
            )

            viewModel.onAction(
                NutritionAction.SelectLogItem(
                    secondId
                )
            )

            val selected =
                requireNotNull(
                    awaitState {
                        it.logEditor
                            ?.selectedItemId ==
                                secondId
                    }.logEditor
                )

            assertEquals(
                secondId,
                selected.selectedItemId,
            )

            assertNull(
                selected.versionGroupNameKey
            )
        }

    @Test
    fun archivedLogSearchExcludesOtherArchivedItems(): Unit =
        runBlocking {
            val selectedArchivedId =
                addItem()

            val otherArchivedId =
                addItem()

            val logId =
                addLog(
                    itemId =
                        selectedArchivedId,
                    date = CURRENT_DATE,
                    hour = 12,
                )

            repository.createComposedItem(
                composedDraft(
                    "Selected parent",
                    selectedArchivedId to 100.0,
                )
            )

            repository.createComposedItem(
                composedDraft(
                    "Other parent",
                    otherArchivedId to 100.0,
                )
            )

            repository.removeItem(
                itemId = selectedArchivedId,
                timestampMillis =
                    clock.millis(),
            )

            repository.removeItem(
                itemId = otherArchivedId,
                timestampMillis =
                    clock.millis(),
            )

            viewModel.onAction(
                NutritionAction.InspectLog(
                    logId
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.logEditor?.logId ==
                                logId
                    }.logEditor
                )

            assertTrue(
                editor.itemOptions.any {
                    it.id == selectedArchivedId &&
                            it.isArchived
                }
            )

            assertTrue(
                editor.itemOptions.none {
                    it.id == otherArchivedId
                }
            )
        }

    @Test
    fun customBoundaryIncludesAfterMidnightLog(): Unit =
        runBlocking {
            settings.value =
                AppSettings(
                    dayBoundary =
                        LocalTime.of(4, 0)
                )

            val itemId = addItem()

            val logId =
                addLog(
                    itemId = itemId,
                    date =
                        CURRENT_DATE
                            .plusDays(1),
                    hour = 2,
                )

            val state =
                awaitState {
                    it.selectedDate ==
                            CURRENT_DATE &&
                            it.logs.any { row ->
                                row.logId == logId
                            }
                }

            assertEquals(
                LocalTime.of(2, 0),
                state.logs
                    .single {
                        it.logId == logId
                    }
                    .consumedTime,
            )
        }

    @Test
    fun refreshFollowsCurrentDayButPreservesPastSelection(): Unit =
        runBlocking {
            awaitState {
                it.selectedDate ==
                        CURRENT_DATE
            }

            clock.setTime(
                timestamp(
                    CURRENT_DATE.plusDays(1),
                    12,
                )
            )

            viewModel.refresh()

            awaitState {
                it.currentDate ==
                        CURRENT_DATE.plusDays(1) &&
                        it.selectedDate ==
                        CURRENT_DATE.plusDays(1)
            }

            viewModel.onAction(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            )

            awaitState {
                it.selectedDate == PAST_DATE
            }

            clock.setTime(
                timestamp(
                    CURRENT_DATE.plusDays(2),
                    12,
                )
            )

            viewModel.refresh()

            val state =
                awaitState {
                    it.currentDate ==
                            CURRENT_DATE.plusDays(2)
                }

            assertEquals(
                PAST_DATE,
                state.selectedDate,
            )

            assertFalse(state.isCurrentDay)
        }

    @Test
    fun boundaryTimerAdvancesCurrentDay(): Unit =
        runBlocking {
            awaitState {
                it.currentDate ==
                        CURRENT_DATE
            }

            val wait = timer.next()

            assertTrue(
                wait.milliseconds > 0L
            )

            clock.setTime(
                timestamp(
                    CURRENT_DATE.plusDays(1),
                    0,
                ) + 1L
            )

            wait.resume.complete(Unit)

            val nextDate =
                CURRENT_DATE.plusDays(1)

            val state =
                awaitState {
                    it.currentDate == nextDate &&
                            it.selectedDate == nextDate
                }

            assertEquals(
                nextDate,
                state.selectedDate,
            )
        }

    private fun composedDraft(
        name: String,
        vararg components:
        Pair<Long, Double>,
    ): ComposedNutritionItemDraft =
        ComposedNutritionItemDraft(
            name = name,
            components =
                components.map {
                        (itemId, grams) ->
                    NutritionComponentDraft(
                        itemId = itemId,
                        gramsPer100g = grams,
                    )
                },
        )

    private suspend fun addItem(
        calories: Double = 100.0,
        protein: Double = 10.0,
    ): Long =
        repository.createItem(
            draft =
                NutritionItemDraft(
                    name =
                        "Item ${System.nanoTime()}",
                    nutrition =
                        NutritionValuesInput
                            .Per100Grams(
                                calories =
                                    calories,
                                proteinGrams =
                                    protein,
                            ),
                ),
            timestampMillis =
                clock.millis(),
        )

    private suspend fun addLog(
        itemId: Long,
        date: LocalDate,
        hour: Int,
        weightGrams: Double = 100.0,
    ): Long =
        requireNotNull(
            repository.createLog(
                draft =
                    FoodLogDraft(
                        itemId = itemId,
                        consumedAtEpochMillis =
                            timestamp(
                                date,
                                hour,
                            ),
                        weightGrams =
                            weightGrams,
                    ),
                timestampMillis =
                    clock.millis(),
            )
        )

    private suspend fun awaitState(
        condition:
            (NutritionScreenUiState) -> Boolean,
    ): NutritionScreenUiState =
        withTimeout(5_000.milliseconds) {
            viewModel.uiState.first(condition)
        }

    private fun timestamp(
        date: LocalDate,
        hour: Int,
    ): Long =
        date.atTime(hour, 0)
            .atZone(clock.zone)
            .toInstant()
            .toEpochMilli()

    private class TestClock(
        initialInstant: Instant,
        private val testZone: ZoneId,
    ) : Clock() {
        private var currentInstant =
            initialInstant

        override fun getZone(): ZoneId =
            testZone

        override fun withZone(
            zone: ZoneId,
        ): Clock =
            TestClock(
                initialInstant =
                    currentInstant,
                testZone = zone,
            )

        override fun instant(): Instant =
            currentInstant

        fun setTime(
            timestampMillis: Long,
        ) {
            currentInstant =
                Instant.ofEpochMilli(
                    timestampMillis
                )
        }
    }

    private class TestTimer :
        BoundaryTimer {
        private val waits =
            Channel<TimerWait>(
                Channel.UNLIMITED
            )

        override suspend fun pause(
            milliseconds: Long,
        ) {
            val wait =
                TimerWait(
                    milliseconds =
                        milliseconds,
                    resume =
                        CompletableDeferred(),
                )

            waits.send(wait)
            wait.resume.await()
        }

        suspend fun next(): TimerWait =
            waits.receive()
    }

    private data class TimerWait(
        val milliseconds: Long,
        val resume:
        CompletableDeferred<Unit>,
    )

    private companion object {
        val CURRENT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                8,
                30,
            )

        val PAST_DATE:
                LocalDate =
            CURRENT_DATE.minusDays(1)

        const val TOLERANCE =
            0.000_001

        const val FLOAT_TOLERANCE =
            0.000_001f
    }
}