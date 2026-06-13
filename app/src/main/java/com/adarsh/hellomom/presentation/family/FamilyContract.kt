package com.adarsh.hellomom.presentation.family

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity

sealed class FamilyIntent : UiIntent {
    object LoadFamily : FamilyIntent()
    data class OnInviteMember(val name: String, val email: String, val role: String) : FamilyIntent()
}

data class FamilyState(
    val members: List<FamilyMemberEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val error: String? = null
) : UiState

sealed class FamilyEffect : UiEffect {
    data class ShowError(val message: String) : FamilyEffect()
    data class ShareInviteLink(val inviteText: String) : FamilyEffect()
}
