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
import java.time.ZoneId
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database =
            DatabaseProvider.get(applicationContext)

        val repository =
            HabitRepository(database)

        val settingsRepository =
            AppSettingsRepository(
                applicationContext.appSettingsDataStore
            )

        val viewModelFactory =
            HabitViewModelFactory(
                repository = repository,
                settings = settingsRepository.settings,
                zoneId = ZoneId.systemDefault(),
            )

        val settingsViewModelFactory =
            SettingsViewModelFactory(
                repository = settingsRepository
            )

        setContent {
            MaterialTheme {
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
                        habitState = uiState,
                        onHabitAction = habitViewModel::onAction,
                        onOpenSettings = {
                            showSettings = true
                        },
                    )
                }
            }
        }
    }
}