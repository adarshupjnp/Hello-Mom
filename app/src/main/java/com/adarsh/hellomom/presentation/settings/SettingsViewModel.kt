package com.adarsh.hellomom.presentation.settings

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : BaseViewModel<SettingsIntent, SettingsState, SettingsEffect>() {

    override fun createInitialState(): SettingsState = SettingsState(
        selectedLanguage = preferenceManager.selectedLanguage,
        pendingLanguage = preferenceManager.selectedLanguage,
        isVoiceEnabled = preferenceManager.isVoiceEnabled
    )

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.OnVoiceToggle -> {
                preferenceManager.isVoiceEnabled = intent.enabled
                setState { copy(isVoiceEnabled = intent.enabled) }
            }
            is SettingsIntent.OnLanguageSelected -> setState { copy(pendingLanguage = intent.language) }
            SettingsIntent.OnApplyLanguage -> applyLanguage()
            SettingsIntent.OnLogoutClicked -> logoutWithSync()
        }
    }

    private fun applyLanguage() {
        val language = uiState.value.pendingLanguage
        preferenceManager.selectedLanguage = language
        setState { copy(selectedLanguage = language) }
        
        setEffect { SettingsEffect.RestartApp }
    }

    private fun logoutWithSync() {
        viewModelScope.launch {
            setState { copy(isSyncingBeforeLogout = true) }
            // Simulate sync
            for (i in 1..10) {
                delay(200)
                setState { copy(syncProgress = i / 10f) }
            }
            authRepository.logout()
            setEffect { SettingsEffect.NavigateToLogin }
        }
    }
}
