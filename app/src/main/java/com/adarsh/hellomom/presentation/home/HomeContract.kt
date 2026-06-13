package com.adarsh.hellomom.presentation.home

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.data.local.entity.MedicineEntity

import com.adarsh.hellomom.data.local.entity.PregnancyTip

sealed class HomeIntent : UiIntent {
    object LoadData : HomeIntent()
    object OnLogoutClicked : HomeIntent()
    data class OnProfilePictureChanged(val uri: String) : HomeIntent()
}

data class HomeState(
    val user: UserEntity? = null,
    val medicinesToday: List<MedicineEntity> = emptyList(),
    val pregnancyWeek: Int = 0,
    val pregnancyTip: PregnancyTip? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class HomeEffect : UiEffect {
    object NavigateToLogin : HomeEffect()
}
