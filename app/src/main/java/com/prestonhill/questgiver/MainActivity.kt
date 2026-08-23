package com.prestonhill.questgiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.data.local.database.DatabaseProvider
import com.prestonhill.questgiver.data.repository.HabitRepository
import com.prestonhill.questgiver.feature.habits.HabitScheduleCalculator
import com.prestonhill.questgiver.feature.habits.HabitScreen
import com.prestonhill.questgiver.feature.habits.HabitViewModel
import com.prestonhill.questgiver.feature.habits.HabitViewModelFactory
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database =
            DatabaseProvider.get(applicationContext)

        val repository =
            HabitRepository(database)

        val appDayCalculator =
            AppDayCalculator(
                dayBoundary = LocalTime.MIDNIGHT,
                zoneId = ZoneId.systemDefault()
            )

        val scheduleCalculator =
            HabitScheduleCalculator(
                appDayCalculator = appDayCalculator,
                weekStart = DayOfWeek.MONDAY
            )

        val viewModelFactory =
            HabitViewModelFactory(
                repository = repository,
                appDayCalculator = appDayCalculator,
                scheduleCalculator = scheduleCalculator
            )

        setContent {
            MaterialTheme {
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

                HabitScreen(
                    uiState = uiState,
                    onAction = habitViewModel::onAction,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    }
}