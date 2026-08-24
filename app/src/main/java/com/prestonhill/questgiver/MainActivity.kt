package com.prestonhill.questgiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prestonhill.questgiver.data.local.database.DatabaseProvider
import com.prestonhill.questgiver.data.repository.HabitRepository
import com.prestonhill.questgiver.feature.habits.HabitViewModel
import com.prestonhill.questgiver.feature.habits.HabitViewModelFactory
import java.time.Clock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.prestonhill.questgiver.feature.shell.AppShell
import com.prestonhill.questgiver.data.local.preferences.appSettingsDataStore
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import com.prestonhill.questgiver.feature.settings.SettingsScreen
import com.prestonhill.questgiver.feature.settings.SettingsViewModel
import com.prestonhill.questgiver.feature.settings.SettingsViewModelFactory
import com.prestonhill.questgiver.data.repository.TaskRepository
import com.prestonhill.questgiver.feature.tasks.TaskViewModel
import com.prestonhill.questgiver.feature.tasks.TaskViewModelFactory
import com.prestonhill.questgiver.feature.history.HistoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database =
            DatabaseProvider.get(applicationContext)

        val repository =
            HabitRepository(database)

        val taskRepository =
            TaskRepository(database)

        val appClock =
            Clock.systemDefaultZone()

        val settingsRepository =
            AppSettingsRepository(
                applicationContext.appSettingsDataStore
            )

        val viewModelFactory =
            HabitViewModelFactory(
                repository = repository,
                settings = settingsRepository.settings,
                clock = appClock,
            )

        val taskViewModelFactory =
            TaskViewModelFactory(
                repository = taskRepository,
                settings = settingsRepository.settings,
                clock = appClock,
            )

        val settingsViewModelFactory =
            SettingsViewModelFactory(
                repository = settingsRepository
            )

        setContent {
            MaterialTheme {
                val historyViewModel: HistoryViewModel =
                    viewModel()

                val historyState by
                historyViewModel.uiState
                    .collectAsStateWithLifecycle()

                val taskViewModel: TaskViewModel =
                    viewModel(factory = taskViewModelFactory)

                val taskState by
                taskViewModel.uiState
                    .collectAsStateWithLifecycle()

                val settingsViewModel: SettingsViewModel =
                    viewModel(factory = settingsViewModelFactory)

                val settingsState by
                settingsViewModel.uiState
                    .collectAsStateWithLifecycle()

                val habitViewModel: HabitViewModel =
                    viewModel(factory = viewModelFactory)

                val uiState by
                habitViewModel.uiState
                    .collectAsStateWithLifecycle()

                LifecycleEventEffect(
                    event = Lifecycle.Event.ON_RESUME
                ) {
                    habitViewModel.refreshAppDay()
                    taskViewModel.refresh()
                }

                var showSettings by rememberSaveable {
                    mutableStateOf(false)
                }

                BackHandler(enabled = showSettings) {
                    showSettings = false
                }

                if (showSettings) {
                    SettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onBack = {
                            showSettings = false
                        },
                    )
                } else {
                    AppShell(
                        taskState = taskState,
                        onTaskAction = taskViewModel::onAction,
                        habitState = uiState,
                        onHabitAction =
                            habitViewModel::onAction,
                        onOpenSettings = {
                            showSettings = true
                        },
                        historyState = historyState,
                        onHistoryAction =
                            historyViewModel::onAction,
                    )
                }
            }
        }
    }
}