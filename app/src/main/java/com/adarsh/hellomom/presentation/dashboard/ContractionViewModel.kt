package com.adarsh.hellomom.presentation.dashboard

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.data.local.entity.ContractionEntity
import com.adarsh.hellomom.domain.repository.ContractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ContractionViewModel @Inject constructor(
    private val contractionRepository: ContractionRepository,
    private val roleManager: RoleManager
) : BaseViewModel<ContractionIntent, ContractionState, ContractionEffect>() {

    override fun createInitialState(): ContractionState = ContractionState()

    init {
        handleIntent(ContractionIntent.Load)
    }

    override fun handleIntent(intent: ContractionIntent) {
        when (intent) {
            ContractionIntent.Load -> load()
            is ContractionIntent.OnRecord -> record(intent.startTime, intent.durationMillis)
            is ContractionIntent.OnUpdate -> update(intent.contraction)
            is ContractionIntent.OnDelete -> delete(intent.contraction)
            is ContractionIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // Family members view the owner's contractions (activeUserId) read-only.
            val access = roleManager.resolveAccess()
            val user = access.user
            if (user == null) {
                setState { copy(isLoading = false) }
                return@launch
            }
            
            val owner = access.owner ?: user
            val week = PregnancyProgress.week(owner.pregnancyStartDate)
            
            contractionRepository.getContractions(access.activeUserId).collectLatest { list ->
                setState { 
                    copy(
                        contractions = list, 
                        isOwner = access.isOwner,
                        userName = user.fullName,
                        pregnancyWeek = week,
                        isLoading = false 
                    ) 
                }
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val date = uiState.value.selectedDate
        val all = uiState.value.contractions
        val filtered = if (date == null) {
            all
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val target = sdf.format(java.util.Date(date))
            all.filter { sdf.format(java.util.Date(it.startTime)) == target }
        }
        setState { copy(filtered = filtered) }
    }

    private fun record(startTime: Long, durationMillis: Long) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            // Read-only family members must never write.
            if (!access.isOwner) return@launch
            // Interval = gap since the previous contraction started (0 for the first one).
            val previousStart = contractionRepository.getLatestContraction(access.activeUserId)?.startTime
            val interval = if (previousStart != null && startTime > previousStart) startTime - previousStart else 0L
            val entity = ContractionEntity(
                contractionId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                startTime = startTime,
                durationMillis = durationMillis,
                intervalMillis = interval,
                timestamp = System.currentTimeMillis()
            )
            contractionRepository.insertContraction(entity)
        }
    }

    private fun update(contraction: ContractionEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            contractionRepository.insertContraction(
                contraction.copy(
                    syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun delete(contraction: ContractionEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            contractionRepository.deleteContraction(contraction)
        }
    }
}
