package com.prestonhill.questgiver.feature.nutrition

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.prestonhill.questgiver.data.repository.ComposedNutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionComponentDraft
import com.prestonhill.questgiver.data.repository.NutritionRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database:
            QuestGiverDatabase

    private lateinit var repository:
            NutritionRepository

    private lateinit var viewModel:
            NutritionViewModel

    private lateinit var viewModelStore:
            ViewModelStore

    private lateinit var clock: Clock

    private var foodId = 0L
    private var parentId = 0L
    private var logId = 0L

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        clock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-30T17:00:00Z"
                ),
                ZoneId.of(
                    "America/Chicago"
                ),
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
                .build()

        repository =
            NutritionRepository(database)

        viewModelStore =
            ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory =
                    NutritionViewModelFactory(
                        repository = repository,
                        settings =
                            flowOf(
                                AppSettings()
                            ),
                        clock = clock,
                    ),
            )[NutritionViewModel::class.java]

        composeRule.setContent {
            val state by
            viewModel.uiState
                .collectAsState()

            MaterialTheme {
                NutritionScreen(
                    state = state,
                    onAction =
                        viewModel::onAction,
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
    fun nutritionJourney(): Unit {
        createFood()
        createReference()
        addFoodLog()
        editFoodLog()
        editFood()
        archiveAndRestoreFood()
        deleteReferenceAndFood()
        verifyDeleted()
    }

    private fun createFood() {
        waitForTag(NutritionTags.MANAGE)

        composeRule
            .onNodeWithTag(
                NutritionTags.MANAGE
            )
            .performClick()

        waitForTag(
            NutritionManageTags.SCREEN
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.ADD_ITEM
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.EDITOR
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.NAME
            )
            .performTextInput(FOOD_NAME)

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .VERSION_LABEL
            )
            .performTextInput(
                VERSION_LABEL
            )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_WEIGHT
            )
            .performTextInput("100")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_CALORIES
            )
            .performTextInput("100")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .SERVING_PROTEIN
            )
            .performTextInput("10")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        foodId =
            runBlocking {
                repository.observeAllItems()
                    .first { items ->
                        items.any {
                            it.name ==
                                    FOOD_NAME
                        }
                    }
                    .single {
                        it.name == FOOD_NAME
                    }
                    .id
            }

        waitForTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )
    }

    private fun createReference() {
        parentId =
            runBlocking {
                repository.createComposedItem(
                    draft =
                        ComposedNutritionItemDraft(
                            name = PARENT_NAME,
                            components =
                                listOf(
                                    NutritionComponentDraft(
                                        itemId =
                                            foodId,
                                        gramsPer100g =
                                            100.0,
                                    )
                                ),
                        ),
                    timestampMillis =
                        clock.millis(),
                )
            }

        waitForTag(
            NutritionManageTags.group(
                PARENT_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.BACK
            )
            .performClick()

        waitForTag(NutritionTags.ADD)
    }

    private fun addFoodLog() {
        composeRule
            .onNodeWithTag(
                NutritionTags.ADD
            )
            .performClick()

        waitForTag(
            NutritionLogEditorTags.EDITOR
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SEARCH
            )
            .performTextInput(FOOD_NAME)

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.food(
                    FOOD_KEY
                )
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.WEIGHT
            )
            .performTextInput("100")

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        waitForNoTag(
            NutritionLogEditorTags.EDITOR
        )

        logId =
            runBlocking {
                viewModel.uiState
                    .first { state ->
                        state.logs.size == 1
                    }
                    .logs
                    .single()
                    .logId
            }

        waitForTag(
            NutritionTags.log(logId)
        )

        val state =
            runBlocking {
                viewModel.uiState.first {
                    it.totalCalories ==
                            100.0 &&
                            it.totalProteinGrams ==
                            10.0
                }
            }

        assertEquals(
            100.0,
            state.totalCalories,
            TOLERANCE,
        )
    }

    private fun editFoodLog() {
        composeRule
            .onNodeWithTag(
                NutritionTags.log(logId)
            )
            .performClick()

        waitForTag(
            NutritionLogEditorTags.EDITOR
        )

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.WEIGHT
            )
            .performTextReplacement("200")

        composeRule
            .onNodeWithTag(
                NutritionLogEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        waitForNoTag(
            NutritionLogEditorTags.EDITOR
        )

        val state =
            runBlocking {
                viewModel.uiState.first {
                    it.logs.singleOrNull()
                        ?.weightGrams ==
                            200.0 &&
                            it.totalCalories ==
                            200.0 &&
                            it.totalProteinGrams ==
                            20.0
                }
            }

        assertEquals(
            200.0,
            state.logs
                .single()
                .weightGrams,
            TOLERANCE,
        )
    }

    private fun editFood() {
        composeRule
            .onNodeWithTag(
                NutritionTags.MANAGE
            )
            .performClick()

        waitForTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    FOOD_KEY
                )
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.EDITOR
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .CALORIES_PER_100
            )
            .performScrollTo()
            .performTextReplacement("150")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .PROTEIN_PER_100
            )
            .performScrollTo()
            .performTextReplacement("15")

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.SAVE
            )
            .assertIsEnabled()
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.BACK
            )
            .performClick()

        val state =
            runBlocking {
                viewModel.uiState.first {
                    it.totalCalories ==
                            300.0 &&
                            it.totalProteinGrams ==
                            30.0
                }
            }

        assertEquals(
            300.0,
            state.totalCalories,
            TOLERANCE,
        )

        assertEquals(
            30.0,
            state.totalProteinGrams,
            TOLERANCE,
        )
    }

    private fun archiveAndRestoreFood() {
        composeRule
            .onNodeWithTag(
                NutritionTags.MANAGE
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    FOOD_KEY
                )
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.ARCHIVE
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.ARCHIVE
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .REMOVE_CONFIRM
            )
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        waitForNoTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags.archiveFilter(
                    NutritionArchiveFilter
                        .ARCHIVED
                )
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_APPLY
            )
            .performClick()

        waitForTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    FOOD_KEY
                )
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.RESTORE
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.RESTORE
            )
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        waitForNoTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.FILTER
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionManageTags
                    .FILTER_RESET
            )
            .performClick()

        waitForTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )
    }

    private fun deleteReferenceAndFood() {
        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    PARENT_KEY
                )
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.DELETE
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.DELETE
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .REMOVE_CONFIRM
            )
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        waitForNoTag(
            NutritionManageTags.group(
                PARENT_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.group(
                    FOOD_KEY
                )
            )
            .performClick()

        waitForTag(
            NutritionItemEditorTags.DELETE
        )

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags.DELETE
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                NutritionItemEditorTags
                    .REMOVE_CONFIRM
            )
            .performClick()

        waitForNoTag(
            NutritionItemEditorTags.EDITOR
        )

        waitForNoTag(
            NutritionManageTags.group(
                FOOD_KEY
            )
        )

        composeRule
            .onNodeWithTag(
                NutritionManageTags.BACK
            )
            .performClick()
    }

    private fun verifyDeleted() {
        waitForText(
            "No food logged for this day."
        )

        runBlocking {
            assertTrue(
                repository.observeAllItems()
                    .first()
                    .isEmpty()
            )

            val state =
                viewModel.uiState.first {
                    it.logs.isEmpty()
                }

            assertTrue(state.logs.isEmpty())
            assertEquals(
                0.0,
                state.totalCalories,
                TOLERANCE,
            )
            assertEquals(
                0.0,
                state.totalProteinGrams,
                TOLERANCE,
            )
        }
    }

    private fun waitForText(
        text: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis = 5_000
        ) {
            composeRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
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
        const val FOOD_NAME =
            "Journey food"
        const val FOOD_KEY =
            "journey food"
        const val VERSION_LABEL =
            "Original"

        const val PARENT_NAME =
            "Journey parent"
        const val PARENT_KEY =
            "journey parent"

        const val TOLERANCE =
            0.000_001
    }
}