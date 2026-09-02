package com.prestonhill.questgiver.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    private lateinit var dataStore:
            DataStore<Preferences>

    private lateinit var repository:
            AppSettingsRepository

    private lateinit var viewModel:
            SettingsViewModel

    private lateinit var viewModelStore:
            ViewModelStore

    private lateinit var dataStoreScope:
            CoroutineScope

    private lateinit var testFile: File

    @Before
    fun setup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        testFile =
            File(
                context.cacheDir,
                "settings_view_model_" +
                        "${System.nanoTime()}" +
                        ".preferences_pb",
            )

        dataStoreScope =
            CoroutineScope(
                SupervisorJob() +
                        Dispatchers.IO
            )

        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = {
                    testFile
                },
            )

        repository =
            AppSettingsRepository(dataStore)

        viewModelStore = ViewModelStore()

        viewModel =
            ViewModelProvider.create(
                store = viewModelStore,
                factory =
                    SettingsViewModelFactory(
                        repository
                    ),
            )[SettingsViewModel::class.java]
    }

    @After
    fun close() {
        viewModelStore.clear()
        dataStoreScope.cancel()
        testFile.delete()
    }

    @Test
    fun nutritionGoalEditorUsesCurrentSettings(): Unit =
        runBlocking {
            repository.setNutritionGoals(
                calorieGoal = 2_000.0,
                maximumCalorieGoal = 2_400.0,
                proteinGoalGrams = 100.0,
                maximumProteinGoalGrams = 150.0,
            )
            awaitState {
                !it.isLoading &&
                        it.settings.calorieGoal ==
                        2_000.0
            }

            viewModel.onAction(
                SettingsAction
                    .EditNutritionGoals
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor !=
                                null
                    }.nutritionGoalsEditor
                )

            assertEquals(
                "2400",
                editor.maximumCalorieGoalText,
            )

            assertEquals(
                "150",
                editor.maximumProteinGoalText,
            )

            assertEquals(
                "2000",
                editor.calorieGoalText,
            )

            assertEquals(
                "100",
                editor.proteinGoalText,
            )

            assertFalse(editor.canSave)
        }

    @Test
    fun nutritionGoalValidationUpdates(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                SettingsAction
                    .EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeCalorieGoal(
                        "2000"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeProteinGoal(
                        "invalid"
                    )
            )

            val invalid =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.proteinGoalText ==
                                "invalid"
                    }.nutritionGoalsEditor
                )

            assertFalse(invalid.canSave)

            viewModel.onAction(
                SettingsAction
                    .ChangeProteinGoal(
                        "100"
                    )
            )

            val valid =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.canSave == true
                    }.nutritionGoalsEditor
                )

            assertTrue(valid.canSave)
        }

    @Test
    fun nutritionGoalsAreSaved(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                SettingsAction
                    .EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeCalorieGoal(
                        "2250"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeProteinGoal(
                        "125"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeMaximumCalorieGoal(
                        "2750"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeMaximumProteinGoal(
                        "175"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .SaveNutritionGoals
            )

            val state =
                awaitState {
                    !it.isSaving &&
                            it.nutritionGoalsEditor ==
                            null &&
                            it.settings.calorieGoal ==
                            2_250.0 &&
                            it.settings
                                .proteinGoalGrams ==
                            125.0 &&
                            it.settings.maximumCalorieGoal == 2750.0 &&
                            it.settings.maximumProteinGoalGrams == 175.0
                }

            assertEquals(
                2_250.0,
                state.settings.calorieGoal,
                0.0,
            )

            assertEquals(
                125.0,
                state.settings
                    .proteinGoalGrams,
                0.0,
            )

            assertEquals(2750.0,
                requireNotNull(state.settings.maximumCalorieGoal),
                0.0)

            assertEquals(175.0,
                requireNotNull(state.settings.maximumProteinGoalGrams),
                0.0)

            assertNull(state.errorMessage)
        }

    @Test
    fun nutritionGoalBoundsAreValidated(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                SettingsAction.EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction.ChangeCalorieGoal(
                    "399"
                )
            )

            var editor =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.calorieGoalText ==
                                "399"
                    }.nutritionGoalsEditor
                )

            assertFalse(editor.canSave)

            viewModel.onAction(
                SettingsAction.ChangeCalorieGoal(
                    "400"
                )
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeMaximumCalorieGoal(
                        "399"
                    )
            )

            editor =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.maximumCalorieGoalText ==
                                "399"
                    }.nutritionGoalsEditor
                )

            assertFalse(editor.canSave)

            viewModel.onAction(
                SettingsAction
                    .ChangeMaximumCalorieGoal(
                        "400"
                    )
            )

            editor =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.maximumCalorieGoalText ==
                                "400"
                    }.nutritionGoalsEditor
                )

            assertTrue(editor.canSave)
        }

    @Test
    fun blankMinimumsRestoreDefaults(): Unit =
        runBlocking {
            repository.setNutritionGoals(
                calorieGoal = 2_000.0,
                maximumCalorieGoal = null,
                proteinGoalGrams = 100.0,
                maximumProteinGoalGrams =
                    null,
            )

            awaitState {
                it.settings.calorieGoal ==
                        2_000.0
            }

            viewModel.onAction(
                SettingsAction.EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction.ChangeCalorieGoal(
                    ""
                )
            )

            viewModel.onAction(
                SettingsAction.ChangeProteinGoal(
                    ""
                )
            )

            val editor =
                requireNotNull(
                    awaitState {
                        it.nutritionGoalsEditor
                            ?.canSave == true
                    }.nutritionGoalsEditor
                )

            assertEquals(
                1_500.0,
                requireNotNull(editor.calorieGoal),
                0.0,
            )

            assertEquals(
                40.0,
                requireNotNull(editor.proteinGoalGrams),
                0.0,
            )

            viewModel.onAction(
                SettingsAction.SaveNutritionGoals
            )

            val saved =
                awaitState {
                    it.nutritionGoalsEditor ==
                            null &&
                            it.settings.calorieGoal ==
                            1_500.0 &&
                            it.settings
                                .proteinGoalGrams ==
                            40.0
                }

            assertNull(
                saved.settings.maximumCalorieGoal
            )
        }

    @Test
    fun invalidNutritionGoalsCannotSave(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                SettingsAction
                    .EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeCalorieGoal(
                        "0"
                    )
            )

            viewModel.onAction(
                SettingsAction
                    .SaveNutritionGoals
            )

            val state =
                awaitState {
                    it.nutritionGoalsEditor
                        ?.calorieGoalText == "0"
                }

            assertFalse(state.isSaving)
            assertFalse(
                requireNotNull(
                    state.nutritionGoalsEditor
                ).canSave
            )

            val persisted =
                repository.settings.first()

            assertEquals(
                1_500.0,
                persisted.calorieGoal,
                0.0,
            )

            assertEquals(
                40.0,
                persisted.proteinGoalGrams,
                0.0,
            )
        }

    @Test
    fun nutritionGoalEditorCanBeDismissed(): Unit =
        runBlocking {
            awaitState {
                !it.isLoading
            }

            viewModel.onAction(
                SettingsAction
                    .EditNutritionGoals
            )

            viewModel.onAction(
                SettingsAction
                    .ChangeCalorieGoal(
                        "2000"
                    )
            )

            awaitState {
                it.nutritionGoalsEditor
                    ?.canSave == true
            }

            viewModel.onAction(
                SettingsAction
                    .DismissNutritionGoals
            )

            val state =
                awaitState {
                    it.nutritionGoalsEditor ==
                            null
                }

            assertNull(
                state.nutritionGoalsEditor
            )

            val persisted =
                repository.settings.first()

            assertEquals(
                1_500.0,
                persisted.calorieGoal,
                0.0,
            )
        }

    private suspend fun awaitState(
        condition:
            (SettingsUiState) -> Boolean,
    ): SettingsUiState =
        withTimeout(5_000.milliseconds) {
            viewModel.uiState.first(condition)
        }
}