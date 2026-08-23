package com.prestonhill.questgiver.feature.shell

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.prestonhill.questgiver.feature.habits.HabitAction
import com.prestonhill.questgiver.feature.habits.HabitScreen
import com.prestonhill.questgiver.feature.habits.HabitScreenUiState
import kotlinx.coroutines.launch

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    habitState: HabitScreenUiState,
    onHabitAction: (HabitAction) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pages = AppPage.entries

    val pagerState =
        rememberPagerState(
            initialPage = AppPage.HABITS.ordinal,
            pageCount = pages::size,
        )

    val coroutineScope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentPage.title)
                },
                actions = {
                    TextButton(
                        onClick = onOpenSettings,
                    ) {
                        Text("Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                pages.forEachIndexed { index, page ->
                    NavigationBarItem(
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
                    PlaceholderPage("Tasks coming soon")
                }

                AppPage.HABITS -> {
                    HabitScreen(
                        uiState = habitState,
                        onAction = onHabitAction,
                    )
                }

                AppPage.NUTRITION -> {
                    PlaceholderPage(
                        "Nutrition coming soon"
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderPage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}