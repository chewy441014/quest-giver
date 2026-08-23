package com.prestonhill.questgiver.feature.settings

import com.prestonhill.questgiver.core.settings.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)