package com.adarsh.hellomom.presentation.settings

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class SettingsIntent : UiIntent {
    data class OnVoiceToggle(val enabled: Boolean) : SettingsIntent()
    data class OnLanguageSelected(val language: String) : SettingsIntent()
    object OnApplyLanguage : SettingsIntent()
    object OnLogoutClicked : SettingsIntent()
}

data class SettingsState(
    val isVoiceEnabled: Boolean = true,
    val selectedLanguage: String = "English",
    val pendingLanguage: String = "English",
    val isSyncingBeforeLogout: Boolean = false,
    val syncProgress: Float = 0f
) : UiState

sealed class SettingsEffect : UiEffect {
    object NavigateToLogin : SettingsEffect()
    object RestartApp : SettingsEffect()
    data class ShowMessage(val message: String) : SettingsEffect()
}
