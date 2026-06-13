package com.adarsh.hellomom.presentation.symptoms

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.SymptomDao
import com.adarsh.hellomom.data.local.entity.SymptomLogEntity
import com.adarsh.hellomom.domain.repository.AiRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SymptomViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val symptomDao: SymptomDao,
    private val firestore: FirebaseFirestore,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<SymptomIntent, SymptomState, SymptomEffect>() {

    override fun createInitialState(): SymptomState = SymptomState()

    init {
        handleIntent(SymptomIntent.LoadLogs)
    }

    override fun handleIntent(intent: SymptomIntent) {
        when (intent) {
            SymptomIntent.LoadLogs -> loadLogs()
            is SymptomIntent.OnAddSymptom -> addSymptom(intent.name, intent.severity)
        }
    }

    private fun loadLogs() {
        // Throttled background sync so family members see the owner's latest symptoms on
        // navigation — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Symptom resolveAccess failed", e)
                    return@launch
                }
            if (access.user != null) {
                setState { copy(isOwner = access.isOwner) }
                // Family members view the owner's symptom logs (activeUserId).
                symptomDao.getSymptomLogs(access.activeUserId)
                    .catch { e -> SyncLogger.error("Symptom flow failed", e) }
                    .collectLatest { list ->
                        setState { copy(logs = list, isOwner = access.isOwner) }
                    }
            }
        }
    }

    private fun addSymptom(name: String, severity: Int) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val access = roleManager.resolveAccess()
            val user = access.user ?: run { setState { copy(isLoading = false) }; return@launch }
            // Only owners may log symptoms.
            if (!access.isOwner) {
                setState { copy(isLoading = false) }
                setEffect { SymptomEffect.ShowError("Read-only access: you cannot log symptoms.") }
                return@launch
            }

            // Only owners reach this point, so their own start date is the owner start date.
            val week = PregnancyProgress.week(user.pregnancyStartDate)

            val analysis = aiRepository.analyzeSymptomRisk(name, week)
            analysis.onSuccess { risk ->
                val log = SymptomLogEntity(
                    logId = UUID.randomUUID().toString(),
                    userId = access.activeUserId,
                    symptomName = name,
                    severity = severity,
                    riskLevel = risk.severity,
                    recommendation = risk.recommendation
                )
                symptomDao.insertSymptomLog(log)
                SyncLogger.local("ADD symptom", "symptoms", "id=${log.logId} userId=${log.userId} name=${log.symptomName} severity=${log.severity} risk=${log.riskLevel}")
                // Push to Firestore so family members can see the latest symptoms on their dashboard.
                try {
                    firestore.collection("users").document(access.activeUserId)
                        .collection("symptoms").document(log.logId).set(log).await()
                    SyncLogger.firebaseWrite("ADD symptom", "users/${access.activeUserId}/symptoms/${log.logId}", "name=${log.symptomName} severity=${log.severity} risk=${log.riskLevel}")
                } catch (e: Exception) {
                    // Offline: the periodic SyncWorker / manual sync will push it later.
                    SyncLogger.warn("ADD symptom push failed (offline?) id=${log.logId}", e)
                }
                setState { copy(isLoading = false) }
                setEffect { SymptomEffect.OnSymptomAdded }
            }.onFailure { e ->
                setState { copy(isLoading = false) }
                setEffect { SymptomEffect.ShowError(e.message ?: "Analysis failed") }
            }
        }
    }
}
