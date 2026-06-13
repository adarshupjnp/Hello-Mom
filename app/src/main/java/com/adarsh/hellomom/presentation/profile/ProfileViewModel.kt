package com.adarsh.hellomom.presentation.profile

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.domain.repository.AppUpdateRepository
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val syncRepository: SyncRepository,
    private val roleManager: RoleManager,
    private val preferenceManager: PreferenceManager,
    private val appUpdateRepository: AppUpdateRepository
) : BaseViewModel<ProfileIntent, ProfileState, ProfileEffect>() {

    override fun createInitialState(): ProfileState = ProfileState(
        currentLanguage = preferenceManager.selectedLanguage,
        isVoiceReminderEnabled = preferenceManager.isVoiceEnabled
    )

    init {
        handleIntent(ProfileIntent.LoadProfile)
    }

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.LoadProfile -> loadProfile()
            is ProfileIntent.UpdateProfilePicture -> updateProfilePicture(intent.uri)
            is ProfileIntent.ChangeLanguage -> changeLanguage(intent.language)
            is ProfileIntent.ToggleVoiceReminder -> {
                preferenceManager.isVoiceEnabled = intent.enabled
                setState { copy(isVoiceReminderEnabled = intent.enabled) }
            }
            ProfileIntent.Logout -> logout()
            ProfileIntent.SyncData -> syncData()
            ProfileIntent.ShareApp -> shareApp()
        }
    }

    private fun shareApp() {
        viewModelScope.launch {
            try {
                // Reuse the update data layer to fetch the current download link (apk_url).
                val info = appUpdateRepository.getAppUpdateInfo()
                val message = buildString {
                    append("Try Hello Mom+ 🤰 — your pregnancy companion app.\n")
                    append("Version ${info.latestVersionName}\n")
                    append("Download: ${info.apkUrl}")
                }
                setEffect { ProfileEffect.ShareAppLink(message) }
            } catch (e: Exception) {
                setEffect {
                    ProfileEffect.ShowError(
                        "Couldn't fetch the share link: ${e.message ?: "No internet connection"}"
                    )
                }
            }
        }
    }

    private fun syncData() {
        viewModelScope.launch {
            setState { copy(isSyncing = true) }
            // Full two-way sync via the shared SyncRepository (profile + owner data + family members,
            // appointments, medicines, symptoms, reminders). Offline => fails gracefully and the
            // existing local cache stays on screen.
            val result = syncRepository.syncAll()
            setState { copy(isSyncing = false) }
            result.fold(
                onSuccess = { setEffect { ProfileEffect.SyncSuccess } },
                onFailure = { e -> setEffect { ProfileEffect.ShowError("Sync failed: ${e.message ?: "No internet connection"}") } }
            )
        }
    }

    private fun loadProfile() {
        // Throttled background sync so the profile (and the owner's start date for family
        // members) is refreshed just by opening this screen — no manual "Sync Data" tap needed.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // Resolve the owner whose pregnancy we track. Family members have no pregnancyStartDate
            // of their own, so the week must be computed from the linked owner's start date —
            // otherwise the Information section always showed "Week 1 Day 1".
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Profile resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val ownerStartDate = access.owner?.pregnancyStartDate

            authRepository.getCurrentUser().collectLatest { user ->
                if (user != null) {
                    val isOwner = access.isOwner
                    // Owner uses their own start date; family uses the resolved owner's start date.
                    val startDate = if (isOwner) user.pregnancyStartDate else ownerStartDate
                    setState {
                        copy(
                            user = user,
                            isOwner = isOwner,
                            pregnancyWeek = PregnancyProgress.week(startDate),
                            pregnancyDay = PregnancyProgress.dayOfWeek(startDate),
                            isLoading = false
                        )
                    }
                } else {
                    setState { copy(isLoading = false) }
                    setEffect { ProfileEffect.NavigateToLogin }
                }
            }
        }
    }

    private fun updateProfilePicture(uri: String) {
        viewModelScope.launch {
            val user = uiState.value.user ?: return@launch
            userRepository.updateUser(user.copy(profilePicture = uri))
        }
    }

    private fun changeLanguage(language: String) {
        preferenceManager.selectedLanguage = language
        setState { copy(currentLanguage = language) }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            setEffect { ProfileEffect.NavigateToLogin }
        }
    }
}
