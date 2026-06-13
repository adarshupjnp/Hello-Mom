package com.adarsh.hellomom.presentation.dashboard

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.JournalEntity
import com.adarsh.hellomom.domain.repository.JournalRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<JournalIntent, JournalState, JournalEffect>() {

    override fun createInitialState(): JournalState = JournalState()

    init {
        handleIntent(JournalIntent.Load)
    }

    override fun handleIntent(intent: JournalIntent) {
        when (intent) {
            JournalIntent.Load -> load()
            is JournalIntent.OnAdd -> add(intent.content, intent.mood)
            is JournalIntent.OnUpdate -> update(intent.entry)
            is JournalIntent.OnDelete -> delete(intent.entry)
            is JournalIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun load() {
        // Throttled background sync so family members see the owner's latest journal entries on
        // navigation — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // Family members read the owner's journal (activeUserId); owners read their own.
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Journal resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            if (access.user == null) {
                setState { copy(isLoading = false) }
                return@launch
            }
            // userName/pregnancyWeek are only used for the PDF export header.
            // The week always comes from the owner's start date (family has none of its own).
            val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: access.user?.pregnancyStartDate)
            journalRepository.getEntries(access.activeUserId)
                .catch { e ->
                    SyncLogger.error("Journal flow failed", e)
                    setState { copy(isLoading = false) }
                }
                .collectLatest { list ->
                    setState {
                        copy(
                            entries = list,
                            isOwner = access.isOwner,
                            userName = access.user?.fullName ?: "",
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
        val all = uiState.value.entries
        val filtered = if (date == null) {
            all
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val target = sdf.format(java.util.Date(date))
            all.filter { sdf.format(java.util.Date(it.date)) == target }
        }
        setState { copy(filtered = filtered) }
    }

    private fun add(content: String, mood: String) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            // Read-only family members must never write.
            if (!access.isOwner) return@launch
            val entry = JournalEntity(
                entryId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                title = "",
                content = content,
                mood = mood,
                date = System.currentTimeMillis()
            )
            journalRepository.insertEntry(entry)
        }
    }

    private fun update(entry: JournalEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            journalRepository.updateEntry(
                entry.copy(
                    syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun delete(entry: JournalEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            journalRepository.deleteEntry(entry)
        }
    }
}
