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
    fun newItemEditorOpensFromManage(): Unit =
        runBlocking {
            val existingId = addItem()

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.OpenAddItem
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor != null
                    }.itemEditor
                )

            assertNull(editor.itemId)

            assertTrue(
                editor.knownItems.any {
                    it.id == existingId
                }
            )

            assertEquals(
                NutritionDestination.Manage,
                awaitState {
                    it.itemEditor != null
                }.destination,
            )

            viewModel.onAction(
                NutritionAction
                    .DismissItemEditor
            )

            val dismissed =
                awaitState {
                    it.itemEditor == null
                }

            assertEquals(
                NutritionDestination.Manage,
                dismissed.destination,
            )
        }
    @Test
    fun manualItemEditorLoadsStoredValues(): Unit =
        runBlocking {
            val itemId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Milk",
                        versionLabel =
                            "Brand A",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 120.0,
                                    proteinGrams = 8.0,
                                ),
                    ),
                    timestampMillis =
                        clock.millis(),
                )

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    itemId
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor?.itemId ==
                                itemId
                    }.itemEditor
                )



            assertEquals(
                "Milk",
                editor.nameText,
            )

            assertEquals(
                "Brand A",
                editor.versionLabelText,
            )

            assertEquals(
                "120",
                editor.caloriesPer100gText,
            )

            assertEquals(
                "8",
                editor.proteinPer100gText,
            )

            assertEquals(
                NutritionEntryMode
                    .PER_100_GRAMS,
                editor.entryMode,
            )

            assertTrue(
                editor.components.isEmpty()
            )

            assertFalse(editor.isDirty)
            assertFalse(editor.canSave)

            assertEquals(
                NutritionItemRemovalModeUiState
                    .DELETE,
                editor.removalMode,
            )
        }

    @Test
    fun composedItemEditorLoadsComponents(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "First",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 200.0,
                                    proteinGrams = 20.0,
                                ),
                    )
                )

            val secondId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Second",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 100.0,
                                    proteinGrams = 10.0,
                                ),
                    )
                )

            val parentId =
                repository.createComposedItem(
                    composedDraft(
                        "Combination",
                        firstId to 25.0,
                        secondId to 75.0,
                    )
                )

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    parentId
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor?.itemId ==
                                parentId
                    }.itemEditor
                )

            assertTrue(editor.isComposed)

            assertEquals(
                listOf(firstId, secondId),
                editor.components
                    .map { it.item.id },
            )

            assertEquals(
                listOf("25", "75"),
                editor.components
                    .map { it.gramsText },
            )

            assertEquals(
                125.0,
                editor
                    .calculatedCaloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                12.5,
                editor
                    .calculatedProteinPer100g,
                TOLERANCE,
            )

            assertTrue(editor.componentsValid)
            assertFalse(editor.isDirty)
        }

    @Test
    fun archivedItemEditorLoadsLifecycleState(): Unit =
        runBlocking {
            val itemId = addItem()

            repository.createComposedItem(
                composedDraft(
                    "Parent",
                    itemId to 100.0,
                )
            )

            assertEquals(
                NutritionItemRemovalResult
                    .ARCHIVED,
                repository.removeItem(
                    itemId = itemId,
                    timestampMillis =
                        clock.millis(),
                )
            )

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    itemId
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor?.itemId ==
                                itemId
                    }.itemEditor
                )

            assertTrue(editor.isArchived)

            assertEquals(
                NutritionItemRemovalModeUiState
                    .ARCHIVE,
                editor.removalMode,
            )

            assertTrue(
                editor.knownItems
                    .single {
                        it.id == itemId
                    }
                    .isArchived
            )
        }

    @Test
    fun itemEditorCanSwitchVersions(): Unit =
        runBlocking {
            val firstId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Milk",
                        versionLabel = "Original",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 100.0,
                                    proteinGrams = 8.0,
                                ),
                    )
                )

            val secondId =
                repository.createItem(
                    NutritionItemDraft(
                        name = "Milk",
                        versionLabel = "New",
                        nutrition =
                            NutritionValuesInput
                                .Per100Grams(
                                    calories = 120.0,
                                    proteinGrams = 10.0,
                                ),
                    )
                )

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    firstId
                )
            )

            val first =
                requireNotNull(
                    awaitState {
                        it.itemEditor?.itemId ==
                                firstId
                    }.itemEditor
                )

            assertEquals(
                listOf(firstId, secondId),
                first.versionOptions
                    .map { it.id },
            )

            viewModel.onAction(
                NutritionAction
                    .SelectItemEditorVersion(
                        secondId
                    )
            )

            val second =
                requireNotNull(
                    awaitState {
                        it.itemEditor?.itemId ==
                                secondId
                    }.itemEditor
                )

            assertEquals(1, second.version)

            assertEquals(
                "New",
                second.versionLabelText,
            )
        }

    @Test
    fun missingItemCannotOpenEditor(): Unit =
        runBlocking {
            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    Long.MAX_VALUE
                )
            )

            val state =
                awaitState {
                    it.operationError ==
                            "Food could not be found."
                }

            assertNull(state.itemEditor)

            assertEquals(
                NutritionDestination.Manage,
                state.destination,
            )
        }

    @Test
    fun selectingDateClosesItemEditor(): Unit =
        runBlocking {
            val itemId = addItem()

            viewModel.onAction(
                NutritionAction.OpenManage
            )

            viewModel.onAction(
                NutritionAction.InspectItem(
                    itemId
                )
            )

            awaitState {
                it.itemEditor?.itemId ==
                        itemId
            }

            viewModel.onAction(
                NutritionAction.SelectDate(
                    PAST_DATE
                )
            )

            val state =
                awaitState {
                    it.selectedDate ==
                            PAST_DATE &&
                            it.itemEditor == null
                }

            assertNull(state.destination)
            assertNull(state.itemEditor)
        }

    @Test
    fun itemEditorFieldsChange(): Unit =
        runBlocking {
            openNewItemEditorState()

            viewModel.onAction(
                NutritionAction.ChangeItemName(
                    "Milk"
                )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemVersionLabel(
                        "Brand A"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemEntryMode(
                        NutritionEntryMode.SERVING
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingWeight(
                        "250"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingCalories(
                        "300"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingProtein(
                        "20"
                    )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.servingProteinText ==
                                "20"
                    }.itemEditor
                )

            assertEquals(
                "Milk",
                editor.nameText,
            )

            assertEquals(
                "Brand A",
                editor.versionLabelText,
            )

            assertEquals(
                NutritionEntryMode.SERVING,
                editor.entryMode,
            )

            assertEquals(
                "250",
                editor.servingWeightText,
            )

            assertTrue(editor.canSave)
        }

    @Test
    fun itemComponentsCanBeAddedChangedAndRemoved(): Unit =
        runBlocking {
            val firstId =
                addNamedItem("First")

            val secondId =
                addNamedItem("Second")

            openNewItemEditorState()

            viewModel.onAction(
                NutritionAction
                    .OpenItemComponentPicker
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemComponentSearch(
                        "First"
                    )
            )

            var editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.componentSearch ==
                                "First"
                    }.itemEditor
                )

            assertTrue(
                editor.showComponentPicker
            )

            viewModel.onAction(
                NutritionAction.AddItemComponent(
                    firstId
                )
            )

            editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.components
                            ?.size == 1
                    }.itemEditor
                )

            assertEquals(
                "100",
                editor.components
                    .single()
                    .gramsText,
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemComponentWeight(
                        itemId = firstId,
                        value = "25",
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .OpenItemComponentPicker
            )

            viewModel.onAction(
                NutritionAction.AddItemComponent(
                    secondId
                )
            )

            editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.components
                            ?.size == 2
                    }.itemEditor
                )

            assertEquals(
                listOf("25", "75"),
                editor.components
                    .map { it.gramsText },
            )

            assertTrue(editor.componentsValid)

            viewModel.onAction(
                NutritionAction
                    .RemoveItemComponent(
                        firstId
                    )
            )

            editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.components
                            ?.size == 1
                    }.itemEditor
                )

            assertEquals(
                secondId,
                editor.components
                    .single()
                    .item.id,
            )
        }

    @Test
    fun perHundredGramItemIsCreated(): Unit =
        runBlocking {
            openNewItemEditorState()

            viewModel.onAction(
                NutritionAction.ChangeItemEntryMode(
                    NutritionEntryMode.PER_100_GRAMS
                )
            )

            viewModel.onAction(
                NutritionAction.ChangeItemName(
                    "Test milk"
                )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemVersionLabel(
                        "Brand A"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemCaloriesPer100g(
                        "120"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemProteinPer100g(
                        "8"
                    )
            )

            viewModel.onAction(
                NutritionAction.SaveItem
            )

            val state =
                awaitState {
                    it.itemEditor == null &&
                            it.manage.itemOptions.any {
                                    option ->
                                option.name ==
                                        "Test milk"
                            }
                }

            assertEquals(
                NutritionDestination.Manage,
                state.destination,
            )

            val item =
                repository.getVersions(
                    "Test milk"
                )
                    .single()

            assertEquals(
                "Brand A",
                item.versionLabel,
            )

            assertEquals(
                120.0,
                item.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                8.0,
                item.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun servingItemIsNormalizedOnSave(): Unit =
        runBlocking {
            openNewItemEditorState()

            viewModel.onAction(
                NutritionAction.ChangeItemName(
                    "Serving food"
                )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemEntryMode(
                        NutritionEntryMode.SERVING
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingWeight(
                        "250"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingCalories(
                        "500"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemServingProtein(
                        "25"
                    )
            )

            viewModel.onAction(
                NutritionAction.SaveItem
            )

            awaitState {
                it.itemEditor == null &&
                        it.manage.itemOptions.any {
                                option ->
                            option.name ==
                                    "Serving food"
                        }
            }

            val item =
                repository.getVersions(
                    "Serving food"
                )
                    .single()

            assertEquals(
                200.0,
                item.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                10.0,
                item.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun composedItemIsCreated(): Unit =
        runBlocking {
            val firstId =
                addNamedItem(
                    name = "First component",
                    calories = 200.0,
                    protein = 20.0,
                )

            val secondId =
                addNamedItem(
                    name = "Second component",
                    calories = 100.0,
                    protein = 10.0,
                )

            openNewItemEditorState()

            viewModel.onAction(
                NutritionAction.ChangeItemName(
                    "Combination"
                )
            )

            viewModel.onAction(
                NutritionAction
                    .OpenItemComponentPicker
            )

            viewModel.onAction(
                NutritionAction.AddItemComponent(
                    firstId
                )
            )

            awaitState {
                it.itemEditor
                    ?.components
                    ?.singleOrNull()
                    ?.item
                    ?.id == firstId
            }

            viewModel.onAction(
                NutritionAction
                    .ChangeItemComponentWeight(
                        itemId = firstId,
                        value = "25",
                    )
            )

            awaitState {
                it.itemEditor
                    ?.components
                    ?.singleOrNull()
                    ?.gramsText == "25"
            }

            viewModel.onAction(
                NutritionAction
                    .OpenItemComponentPicker
            )

            viewModel.onAction(
                NutritionAction.AddItemComponent(
                    secondId
                )
            )

            awaitState { state ->
                state.itemEditor
                    ?.let { editor ->
                        editor.components.size == 2 &&
                                editor.components.map {
                                    it.item.id
                                } ==
                                listOf(
                                    firstId,
                                    secondId,
                                ) &&
                                editor.components.map {
                                    it.gramsText
                                } ==
                                listOf("25", "75") &&
                                editor.componentsValid
                    } == true
                }.itemEditor

            viewModel.onAction(
                NutritionAction.SaveItem
            )

            awaitState {
                it.itemEditor == null &&
                        it.manage.itemOptions.any {
                                option ->
                            option.name ==
                                    "Combination"
                        }
            }

            val item =
                repository.getVersions(
                    "Combination"
                )
                    .single()

            val details =
                requireNotNull(
                    repository.getItemDetails(
                        item.id
                    )
                )

            assertEquals(
                listOf(firstId, secondId),
                details.components
                    .map { it.item.id },
            )

            assertEquals(
                listOf(25.0, 75.0),
                details.components
                    .map { it.gramsPer100g },
            )

            assertEquals(
                125.0,
                item.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                12.5,
                item.proteinPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun editingItemUpdatesHistoricalNutrition(): Unit =
        runBlocking {
            val itemId =
                addNamedItem(
                    name = "Editable",
                    calories = 100.0,
                    protein = 10.0,
                )

            addLog(
                itemId = itemId,
                date = CURRENT_DATE,
                hour = 12,
                weightGrams = 100.0,
            )

            awaitState {
                it.totalCalories == 100.0 &&
                        it.totalProteinGrams ==
                        10.0
            }

            openExistingItemEditorState(
                itemId
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemCaloriesPer100g(
                        "200"
                    )
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemProteinPer100g(
                        "20"
                    )
            )

            viewModel.onAction(
                NutritionAction.SaveItem
            )

            val state =
                awaitState {
                    it.itemEditor == null &&
                            it.totalCalories ==
                            200.0 &&
                            it.totalProteinGrams ==
                            20.0
                }

            assertEquals(
                NutritionDestination.Manage,
                state.destination,
            )
        }

    @Test
    fun editedItemCanSaveAsNewVersion(): Unit =
        runBlocking {
            val originalId =
                addNamedItem(
                    name = "Versioned",
                    calories = 100.0,
                    protein = 10.0,
                )

            openExistingItemEditorState(
                originalId
            )

            viewModel.onAction(
                NutritionAction
                    .ChangeItemCaloriesPer100g(
                        "150"
                    )
            )

            val dirty =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.canSaveAsVersion ==
                                true
                    }.itemEditor
                )

            assertEquals(
                "Save as v1",
                dirty.saveAsVersionText,
            )

            viewModel.onAction(
                NutritionAction
                    .SaveItemAsVersion
            )

            awaitState {
                it.itemEditor == null &&
                        it.manage.itemOptions
                            .count { option ->
                                option.nameKey ==
                                        "versioned"
                            } == 2
            }

            val versions =
                repository.getVersions(
                    "Versioned"
                )

            assertEquals(2, versions.size)

            assertEquals(
                100.0,
                versions.single {
                    it.version == 0
                }.caloriesPer100g,
                TOLERANCE,
            )

            assertEquals(
                150.0,
                versions.single {
                    it.version == 1
                }.caloriesPer100g,
                TOLERANCE,
            )
        }

    @Test
    fun cyclicComponentSaveShowsError(): Unit =
        runBlocking {
            val baseId =
                addNamedItem("Base")

            val middleId =
                repository.createComposedItem(
                    composedDraft(
                        "Middle",
                        baseId to 100.0,
                    )
                )

            val topId =
                repository.createComposedItem(
                    composedDraft(
                        "Top",
                        middleId to 100.0,
                    )
                )

            openExistingItemEditorState(
                baseId
            )

            viewModel.onAction(
                NutritionAction
                    .OpenItemComponentPicker
            )

            viewModel.onAction(
                NutritionAction.AddItemComponent(
                    topId
                )
            )

            viewModel.onAction(
                NutritionAction.SaveItem
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.itemEditor
                            ?.errorMessage ==
                                "Food could not be saved."
                    }.itemEditor
                )

            assertFalse(editor.isSaving)

            val details =
                requireNotNull(
                    repository.getItemDetails(
                        baseId
                    )
                )

            assertTrue(
                details.components.isEmpty()
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

    private suspend fun openNewItemEditorState():
            NutritionItemEditorUiState {
        viewModel.onAction(
            NutritionAction.OpenManage
        )

        viewModel.onAction(
            NutritionAction.OpenAddItem
        )

        return requireNotNull(
            awaitState {
                it.itemEditor != null
            }.itemEditor
        )
    }

    private suspend fun openExistingItemEditorState(
        itemId: Long,
    ): NutritionItemEditorUiState {
        viewModel.onAction(
            NutritionAction.OpenManage
        )

        viewModel.onAction(
            NutritionAction.InspectItem(
                itemId
            )
        )

        return requireNotNull(
            awaitState {
                it.itemEditor?.itemId ==
                        itemId
            }.itemEditor
        )
    }

    private suspend fun addNamedItem(
        name: String,
        calories: Double = 100.0,
        protein: Double = 10.0,
    ): Long =
        repository.createItem(
            draft =
                NutritionItemDraft(
                    name = name,
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