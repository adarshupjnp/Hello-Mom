package com.adarsh.hellomom.presentation.profile

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.UserEntity

sealed class ProfileIntent : UiIntent {
    object LoadProfile : ProfileIntent()
    data class UpdateProfilePicture(val uri: String) : ProfileIntent()
    data class ChangeLanguage(val language: String) : ProfileIntent()
    data class ToggleVoiceReminder(val enabled: Boolean) : ProfileIntent()
    object Logout : ProfileIntent()
    object SyncData : ProfileIntent()
    object ShareApp : ProfileIntent()
    data class UpdateProfile(val updatedUser: UserEntity) : ProfileIntent()
}

data class ProfileState(
    val user: UserEntity? = null,
    val isOwner: Boolean = false,
    val pregnancyWeek: Int = 0,
    val pregnancyDay: Int = 0,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = false,
    val currentLanguage: String = "English",
    val isVoiceReminderEnabled: Boolean = true,
    val error: String? = null
) : UiState

sealed class ProfileEffect : UiEffect {
    object NavigateToLogin : ProfileEffect()
    object SyncSuccess : ProfileEffect()
    data class ShowError(val message: String) : ProfileEffect()

    /** Open the Android share sheet with the app download link. */
    data class ShareAppLink(val shareMessage: String) : ProfileEffect()
}
