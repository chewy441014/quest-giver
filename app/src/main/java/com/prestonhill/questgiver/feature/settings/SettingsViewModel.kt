package com.prestonhill.questgiver.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.data.repository.AppSettingsRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AppSettingsRepository,
) : ViewModel() {
    private val isSaving =
        MutableStateFlow(false)

    private val errorMessage =
        MutableStateFlow<String?>(null)

    val uiState =
        combine(
            repository.settings,
            isSaving,
            errorMessage,
        ) { settings, saving, error ->
            SettingsUiState(
                settings = settings,
                isLoading = false,
                isSaving = saving,
                errorMessage = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetDayBoundary -> {
                save("Day boundary could not be saved.") {
                    repository.setDayBoundary(action.time)
                }
            }

            is SettingsAction.SetWeekStart -> {
                save("Week start could not be saved.") {
                    repository.setWeekStart(action.day)
                }
            }

            SettingsAction.DismissError -> {
                errorMessage.value = null
            }
        }
    }

    private fun save(
        failureMessage: String,
        operation: suspend () -> Unit,
    ) {
        if (isSaving.value) {
            return
        }

        isSaving.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }

                errorMessage.value = failureMessage
            } finally {
                isSaving.value = false
            }
        }
    }
}

class SettingsViewModelFactory(
    private val repository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                SettingsViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}