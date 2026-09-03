package com.prestonhill.questgiver.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.prestonhill.questgiver.MainActivity
import com.prestonhill.questgiver.data.local.database.DatabaseProvider
import com.prestonhill.questgiver.data.local.preferences.appSettingsDataStore
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import java.time.Clock
import kotlinx.coroutines.launch

class SampleDataActivity :
    ComponentActivity() {
    private var showConfirmation by
    mutableStateOf(false)

    private var isLoading by
    mutableStateOf(false)

    private var result by
    mutableStateOf<
            SampleDataResult?
            >(null)

    private var errorMessage by
    mutableStateOf<String?>(null)

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        val seeder =
            SampleDataSeeder(
                database =
                    DatabaseProvider.get(
                        applicationContext
                    ),
                settingsRepository =
                    AppSettingsRepository(
                        applicationContext
                            .appSettingsDataStore
                    ),
                clock =
                    Clock.systemDefaultZone(),
            )

        setContent {
            MaterialTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            16.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Quest Giver sample data",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                    )

                    Text(
                        "This replaces all data in " +
                                "the debug app."
                    )

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                showConfirmation =
                                    true
                            },
                        ) {
                            Text(
                                "Replace with sample data"
                            )
                        }
                    }

                    result?.let { seeded ->
                        Text(
                            "${seeded.nutritionItems} foods · " +
                                    "${seeded.foodLogs} food logs"
                        )

                        Text(
                            "${seeded.tasks} tasks · " +
                                    "${seeded.taskLogs} task logs"
                        )

                        Text(
                            "${seeded.habits} habits · " +
                                    "${seeded.habitLogs} habit logs"
                        )

                        Button(
                            onClick = {
                                startActivity(
                                    Intent(
                                        this@SampleDataActivity,
                                        MainActivity::class.java,
                                    )
                                )

                                finish()
                            },
                        ) {
                            Text("Open Quest Giver")
                        }
                    }

                    errorMessage?.let {
                            message ->
                        Text(
                            message,
                            color =
                                MaterialTheme
                                    .colorScheme.error,
                        )
                    }
                }

                if (showConfirmation) {
                    AlertDialog(
                        onDismissRequest = {
                            showConfirmation =
                                false
                        },
                        title = {
                            Text(
                                "Replace debug data?"
                            )
                        },
                        text = {
                            Text(
                                "All existing data in " +
                                        "the debug app will " +
                                        "be permanently deleted."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showConfirmation =
                                        false
                                    isLoading = true
                                    result = null
                                    errorMessage = null

                                    lifecycleScope.launch {
                                        runCatching {
                                            seeder.replaceAll()
                                        }
                                            .onSuccess {
                                                    seeded ->
                                                result =
                                                    seeded
                                            }
                                            .onFailure {
                                                    error ->
                                                errorMessage =
                                                    error.message
                                                        ?: "Sample data could not be created."
                                            }

                                        isLoading = false
                                    }
                                },
                            ) {
                                Text("Replace")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showConfirmation =
                                        false
                                },
                            ) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}