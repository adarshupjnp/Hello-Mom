package com.adarsh.hellomom.presentation.home

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.PregnancyTipsProvider
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val medicineRepository: MedicineRepository,
    private val syncRepository: SyncRepository
) : BaseViewModel<HomeIntent, HomeState, HomeEffect>() {

    override fun createInitialState(): HomeState = HomeState()

    init {
        handleIntent(HomeIntent.LoadData)
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadData -> loadData()
            HomeIntent.OnLogoutClicked -> logout()
            is HomeIntent.OnProfilePictureChanged -> updateProfilePicture(intent.uri)
        }
    }

    private fun updateProfilePicture(uri: String) {
        viewModelScope.launch {
            val user = uiState.value.user ?: return@launch
            val updatedUser = user.copy(profilePicture = uri)
            userRepository.updateUser(updatedUser)
        }
    }

    private fun loadData() {
        // Throttled background sync so family members get the owner's latest data on navigation.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            authRepository.getCurrentUser().collectLatest { user ->
                if (user != null) {
                    val targetUserId = user.linkedPrimaryUserId ?: user.userId
                    // Reactive (not .first()): the week and today's medicines refresh on screen
                    // as soon as a sync writes fresh owner data into Room.
                    combine(
                        userRepository.getUser(targetUserId),
                        medicineRepository.getMedicines(targetUserId)
                    ) { targetUser, medicines ->
                        val week = PregnancyProgress.week((targetUser ?: user).pregnancyStartDate)
                        val tip = PregnancyTipsProvider.tips.find { it.week <= week }
                            ?: PregnancyTipsProvider.tips.firstOrNull()
                        setState {
                            copy(
                                user = user,
                                medicinesToday = medicines,
                                pregnancyWeek = week,
                                pregnancyTip = tip,
                                isLoading = false
                            )
                        }
                    }.catch { e ->
                        SyncLogger.error("Home flow failed", e)
                        setState { copy(isLoading = false) }
                    }.collect()
                } else {
                    setState { copy(isLoading = false) }
                    setEffect { HomeEffect.NavigateToLogin }
                }
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            setEffect { HomeEffect.NavigateToLogin }
        }
    }

}
