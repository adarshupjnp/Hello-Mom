package com.adarsh.hellomom.presentation.family

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.FamilyRepository
import com.adarsh.hellomom.domain.repository.InviteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val familyRepository: FamilyRepository,
    private val authRepository: AuthRepository
) : BaseViewModel<InviteIntent, InviteState, InviteEffect>() {

    override fun createInitialState(): InviteState = InviteState()

    override fun handleIntent(intent: InviteIntent) {
        when (intent) {
            is InviteIntent.JoinFamily -> joinFamily(intent.code)
        }
    }

    private fun joinFamily(code: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true, inviteCode = code, error = null) }
            
            val currentUser = authRepository.getCurrentUser().first()
            if (currentUser == null) {
                setState { copy(isLoading = false, error = "Please login first to join a family") }
                return@launch
            }

            // 1. Fetch invite
            inviteRepository.getInvite(code).onSuccess { invite ->
                // 2. Validate
                
                // Requirement 8: Edge case - User already in this family
                if (currentUser.linkedPrimaryUserId == invite.familyId) {
                    setState { copy(isLoading = false) }
                    setEffect { InviteEffect.NavigateToHome }
                    return@onSuccess
                }

                if (invite.used) {
                    setState { copy(isLoading = false, error = "This invite code has already been used.") }
                    return@onSuccess
                }

                // 3. Join Family — pass creatorId so linkedPrimaryUserId points to the owner's user, not the family UUID
                familyRepository.joinFamily(invite.familyId, currentUser.userId, invite.creatorId).onSuccess {
                    // 4. Mark used
                    inviteRepository.markInviteAsUsed(code).onSuccess {
                        setState { copy(isLoading = false, isJoining = false) }
                        setEffect { InviteEffect.NavigateToHome }
                    }.onFailure {
                        setState { copy(isLoading = false, error = "Joined family but failed to update invite status.") }
                    }
                }.onFailure { e ->
                    setState { copy(isLoading = false, error = "Failed to join family: ${e.message}") }
                }
            }.onFailure {
                setState { copy(isLoading = false, error = "Invalid or expired invite code.") }
            }
        }
    }
}
