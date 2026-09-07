package com.prestonhill.questgiver.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.prestonhill.questgiver.feature.habits.HabitAction
import com.prestonhill.questgiver.feature.habits.HabitScreen
import com.prestonhill.questgiver.feature.habits.HabitScreenUiState
import kotlinx.coroutines.launch
import com.prestonhill.questgiver.feature.tasks.TaskAction
import com.prestonhill.questgiver.feature.tasks.TaskScreen
import com.prestonhill.questgiver.feature.tasks.TaskScreenUiState
import com.prestonhill.questgiver.feature.history.HistoryAction
import com.prestonhill.questgiver.feature.history.HistoryScreen
import com.prestonhill.questgiver.feature.history.HistoryScreenUiState
import com.prestonhill.questgiver.feature.nutrition.NutritionAction
import com.prestonhill.questgiver.feature.nutrition.NutritionScreen
import com.prestonhill.questgiver.feature.nutrition.NutritionScreenUiState
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.prestonhill.questgiver.R
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.prestonhill.questgiver.feature.habits.HabitTags
import androidx.compose.runtime.LaunchedEffect

enum class AppPage(
    val title: String,
    val shortLabel: String,
) {
    TASKS(
        title = "Tasks",
        shortLabel = "T",
    ),
    HABITS(
        title = "Habits",
        shortLabel = "H",
    ),
    NUTRITION(
        title = "Nutrition",
        shortLabel = "N",
    ),
    HISTORY(
        title = "History",
        shortLabel = "Y",
    ),
}

object AppShellTags {
    const val SETTINGS = "app_settings"

    fun page(page: AppPage) =
        "app_page_${page.name}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    taskState: TaskScreenUiState,
    onTaskAction: (TaskAction) -> Unit,
    habitState: HabitScreenUiState,
    onHabitAction: (HabitAction) -> Unit,
    onOpenSettings: () -> Unit,
    historyState: HistoryScreenUiState,
    onHistoryAction: (HistoryAction) -> Unit,
    nutritionState: NutritionScreenUiState,
    onNutritionAction: (NutritionAction) -> Unit,
    selectedPage: AppPage =
        AppPage.HABITS,
    onPageChanged: (AppPage) -> Unit = {},
) {
    val pages = AppPage.entries

    val pagerState =
        rememberPagerState(
            initialPage = selectedPage.ordinal,
            pageCount = pages::size,
        )

    LaunchedEffect(
        pagerState.settledPage
    ) {
        onPageChanged(
            pages[pagerState.settledPage]
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentPage.title)
                },
                actions = {
                    if (currentPage == AppPage.HABITS) {
                        OutlinedButton(
                            modifier =
                                Modifier.testTag(
                                    HabitTags.SECTIONS
                                ),
                            onClick = {
                                onHabitAction(
                                    HabitAction.ShowSectionManager
                                )
                            },
                        ) {
                            Text("Sections")
                        }
                    }

                    IconButton(
                        modifier =
                            Modifier.testTag(
                                AppShellTags.SETTINGS
                            ),
                        onClick = onOpenSettings,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_settings_24
                                ),
                            contentDescription =
                                "Open settings",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                pages.forEachIndexed { index, page ->
                    NavigationBarItem(
                        modifier =
                            Modifier.testTag(
                                AppShellTags.page(page)
                            ),
                        selected =
                            pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    index
                                )
                            }
                        },
                        icon = {
                            Text(page.shortLabel)
                        },
                        label = {
                            Text(page.title)
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) { pageIndex ->
            when (pages[pageIndex]) {
                AppPage.TASKS -> {
                    TaskScreen(
                        state = taskState,
                        onAction = onTaskAction,
                    )
                }

                AppPage.HABITS -> {
                    HabitScreen(
                        uiState = habitState,
                        onAction = onHabitAction,
                    )
                }

                AppPage.NUTRITION -> {
                    NutritionScreen(
                        state = nutritionState,
                        onAction =
                            onNutritionAction,
                    )
                }

                AppPage.HISTORY -> {
                    HistoryScreen(
                        state = historyState,
                        onAction = onHistoryAction,
                    )
                }
            }
        }
    }
}

