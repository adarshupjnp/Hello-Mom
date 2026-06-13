package com.adarsh.hellomom.presentation.family

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.domain.repository.FamilyRepository
import com.adarsh.hellomom.domain.repository.InviteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val inviteRepository: InviteRepository,
    private val roleManager: RoleManager
) : BaseViewModel<FamilyIntent, FamilyState, FamilyEffect>() {

    override fun createInitialState(): FamilyState = FamilyState()

    init {
        handleIntent(FamilyIntent.LoadFamily)
    }

    override fun handleIntent(intent: FamilyIntent) {
        when (intent) {
            FamilyIntent.LoadFamily -> loadFamily()
            is FamilyIntent.OnInviteMember -> inviteMember(intent)
        }
    }

    private fun loadFamily() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            val access = roleManager.resolveAccess()
            if (access.user == null) {
                setState { copy(isLoading = false, error = "User not logged in") }
                return@launch
            }
            // Both owner and family members see the owner's connected member list (read-only for family).
            familyRepository.getFamilyMembers(access.activeUserId).collectLatest { list ->
                setState { copy(members = list, isOwner = access.isOwner, isLoading = false, error = null) }
            }
        }
    }

    private fun inviteMember(intent: FamilyIntent.OnInviteMember) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            val user = access.user ?: return@launch
            // Only owners may invite / manage family members.
            if (!access.isOwner) {
                setEffect { FamilyEffect.ShowError("Read-only access: you cannot invite members.") }
                return@launch
            }

            // 1. Get or Create Family
            var familyId = user.linkedPrimaryUserId
            if (familyId == null) {
                familyRepository.createFamily(user.userId).onSuccess { id ->
                    familyId = id
                }.onFailure {
                    setEffect { FamilyEffect.ShowError("Failed to create family group") }
                    return@launch
                }
            }

            // 2. Create Invite Code
            inviteRepository.createInvite(familyId!!, user.userId).onSuccess { code ->
                val inviteLink = "https://hello-mom-6e500.web.app/invite/$code"
                val inviteText = "Hey ${intent.name}, I am inviting you to join my pregnancy journey on Hello Mom+ app as my ${intent.role.lowercase()}. " +
                        "Install the app using this link and track our baby's progress : $inviteLink"
                setEffect { FamilyEffect.ShareInviteLink(inviteText) }
            }.onFailure { e ->
                setEffect { FamilyEffect.ShowError(e.message ?: "Failed to generate invite code") }
            }
        }
    }
}
