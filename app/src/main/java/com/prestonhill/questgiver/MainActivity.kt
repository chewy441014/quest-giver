package com.prestonhill.questgiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prestonhill.questgiver.feature.habits.HabitAction
import com.prestonhill.questgiver.feature.habits.HabitCategory
import com.prestonhill.questgiver.feature.habits.HabitCategoryUiState
import com.prestonhill.questgiver.feature.habits.HabitDueStatus
import com.prestonhill.questgiver.feature.habits.HabitRowUiState
import com.prestonhill.questgiver.feature.habits.HabitScreen
import com.prestonhill.questgiver.feature.habits.HabitScreenUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var uiState by remember {
                    mutableStateOf(sampleHabitState())
                }

                HabitScreen(
                    uiState = uiState,
                    onAction = { action ->
                        uiState = reduceHabitState(uiState, action)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    }
}

private fun reduceHabitState(
    state: HabitScreenUiState,
    action: HabitAction
): HabitScreenUiState =
    when (action) {
        is HabitAction.AddCompletion ->
            state.changeCompletion(action.habitId, 1)

        is HabitAction.RemoveCompletion ->
            state.changeCompletion(action.habitId, -1)

        is HabitAction.InspectHabit ->
            state.copy(inspectedHabitId = action.habitId)

        is HabitAction.ToggleCategory ->
            state.copy(
                categories = state.categories.map { category ->
                    if (category.category == action.category) {
                        category.copy(
                            isExpanded = !category.isExpanded
                        )
                    } else {
                        category
                    }
                }
            )

        HabitAction.DismissHabitDetails ->
            state.copy(inspectedHabitId = null)

        is HabitAction.EditHabit -> state
        HabitAction.AddHabit -> state
    }

private fun HabitScreenUiState.changeCompletion(
    habitId: Long,
    change: Int
): HabitScreenUiState =
    copy(
        categories = categories.map { category ->
            category.copy(
                habits = category.habits.map { habit ->
                    if (habit.id != habitId) {
                        habit
                    } else {
                        val maximum =
                            if (habit.allowsMultipleCompletions) 100 else 1

                        val newDailyCount =
                            (habit.completionCountToday + change)
                                .coerceIn(0, maximum)

                        val appliedChange =
                            newDailyCount - habit.completionCountToday

                        val newScheduleCount =
                            (habit.scheduleCompletions + appliedChange)
                                .coerceAtLeast(0)

                        habit.copy(
                            completionCountToday = newDailyCount,
                            scheduleCompletions = newScheduleCount,
                            dueStatus =
                                if (
                                    newScheduleCount >=
                                    habit.scheduleTarget
                                ) {
                                    HabitDueStatus.COMPLETED
                                } else {
                                    HabitDueStatus.DUE
                                }
                        )
                    }
                }
            )
        }
    )

private fun sampleHabitState() =
    HabitScreenUiState(
        categories = listOf(
            HabitCategoryUiState(
                category = HabitCategory.MORNING,
                habits = listOf(
                    HabitRowUiState(
                        id = 1,
                        name = "Brush teeth",
                        streakCount = 12,
                        completionCountToday = 0,
                        allowsMultipleCompletions = false,
                        scheduleCompletions = 0,
                        scheduleTarget = 1,
                        dueStatus = HabitDueStatus.DUE
                    ),
                    HabitRowUiState(
                        id = 2,
                        name = "Eat breakfast",
                        streakCount = 7,
                        completionCountToday = 1,
                        allowsMultipleCompletions = false,
                        scheduleCompletions = 1,
                        scheduleTarget = 1,
                        dueStatus = HabitDueStatus.COMPLETED
                    )
                )
            ),
            HabitCategoryUiState(
                category = HabitCategory.ANYTIME,
                habits = listOf(
                    HabitRowUiState(
                        id = 3,
                        name = "Go to the gym",
                        streakCount = 4,
                        completionCountToday = 0,
                        allowsMultipleCompletions = true,
                        scheduleCompletions = 2,
                        scheduleTarget = 3,
                        dueStatus = HabitDueStatus.DUE
                    ),
                    HabitRowUiState(
                        id = 4,
                        name = "Practice trombone",
                        streakCount = 5,
                        completionCountToday = 1,
                        allowsMultipleCompletions = true,
                        scheduleCompletions = 1,
                        scheduleTarget = 1,
                        dueStatus = HabitDueStatus.COMPLETED
                    )
                )
            ),
            HabitCategoryUiState(
                category = HabitCategory.BEFORE_BED,
                habits = listOf(
                    HabitRowUiState(
                        id = 5,
                        name = "Stretch",
                        streakCount = 9,
                        completionCountToday = 0,
                        allowsMultipleCompletions = false,
                        scheduleCompletions = 0,
                        scheduleTarget = 1,
                        dueStatus = HabitDueStatus.DUE
                    )
                )
            )
        )
    )