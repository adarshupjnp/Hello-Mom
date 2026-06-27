package com.adarsh.hellomom.presentation.voice

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.core.utils.VoiceAssistant
import com.adarsh.hellomom.core.voice.ActionDetector
import com.adarsh.hellomom.core.voice.EntityExtractor
import com.adarsh.hellomom.core.voice.IntentDetector
import com.adarsh.hellomom.core.voice.TextNormalizer
import com.adarsh.hellomom.core.voice.VoiceActionType
import com.adarsh.hellomom.core.voice.VoiceCommandResult
import com.adarsh.hellomom.core.voice.VoiceInputException
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.core.voice.VoiceSlot
import com.adarsh.hellomom.core.voice.VoiceSlotRules
import com.adarsh.hellomom.core.voice.SpeechRecognizerManager
import com.adarsh.hellomom.core.voice.VoicePrefillStore
import com.adarsh.hellomom.core.voice.MicVisibilityController
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.PregnancyDataEngine
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.presentation.components.AppTab
import com.adarsh.hellomom.data.local.SyncStatus
import com.adarsh.hellomom.data.local.entity.*
import com.adarsh.hellomom.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.inject.Inject

/**
 * Heart of the offline voice assistant. Runs the pipeline, enforces the owner-only RBAC write gate,
 * drives multi-turn slot-filling (asks for missing required fields before any create), and speaks
 * every response through the existing [VoiceAssistant]. Navigation + prefill are emitted as effects
 * / pushed into [VoicePrefillStore]; this never writes to Room/Firestore itself.
 */
@HiltViewModel
class VoiceAssistantViewModel @Inject constructor(
    private val speech: SpeechRecognizerManager,
    private val voice: VoiceAssistant,
    private val normalizer: TextNormalizer,
    private val intentDetector: IntentDetector,
    private val actionDetector: ActionDetector,
    private val entityExtractor: EntityExtractor,
    private val prefillStore: VoicePrefillStore,
    private val roleManager: RoleManager,
    private val preferenceManager: PreferenceManager,
    private val medicineRepository: MedicineRepository,
    private val foodRepository: FoodRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val micVisibilityController: MicVisibilityController
) : BaseViewModel<VoiceAssistantIntent, VoiceAssistantState, VoiceAssistantEffect>() {

    private data class Dialogue(
        val intent: VoiceIntentType,
        val action: VoiceActionType,
        var result: VoiceCommandResult,
        var followUps: Int = 0
    )

    private var dialogue: Dialogue? = null
    private var listenJob: Job? = null
    private var retriedOnce = false
    private var cachedIsOwner: Boolean? = null
    // The greeting plays once per app launch. While [welcomeSession] is active, a no-response from
    // the user ends with a polite goodbye instead of the generic "didn't understand" fallback.
    private var welcomePlayed = false
    private var welcomeSession = false
    // When the assistant can't confidently match a command it offers its best guess ("did you mean
    // X?") and waits for a yes/no. [pendingSuggestion] is what it would open on "yes".
    private var awaitingConfirmation = false
    private var pendingSuggestion: VoiceIntentType? = null
    // Consecutive misses in the current session ("no speech" or "couldn't match"). Caps the
    // clarify-and-listen-again loop so it gives one extra ~10s try, then stops.
    private var attempts = 0

    init {
        // Mirror the shared mic-visibility switch into THIS instance's state. The overlay reads its
        // own (Activity-scoped) instance, while screens flip the switch through their own
        // (back-stack-scoped) instance — the singleton controller bridges the two.
        viewModelScope.launch {
            micVisibilityController.visible.collect { v -> setState { copy(micVisible = v) } }
        }
    }

    override fun createInitialState() = VoiceAssistantState(hindi = preferenceManager.selectedLanguage != "English")

    override fun handleIntent(intent: VoiceAssistantIntent) {
        Log.d("VoiceAssistant", "handleIntent: ${intent::class.simpleName}  status=${uiState.value.status}")
        when (intent) {
            VoiceAssistantIntent.OpenAndListen -> toggleMic()
            VoiceAssistantIntent.StartListening -> beginListening()
            VoiceAssistantIntent.Cancel -> cancelByUser()
            VoiceAssistantIntent.Dismiss -> dismiss()
            VoiceAssistantIntent.PermissionDenied -> say(P.micPermission(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
            is VoiceAssistantIntent.QuickPick -> { resetDialogue(); openOrSearch(intent.intent, VoiceActionType.OPEN, VoiceCommandResult()) }
            is VoiceAssistantIntent.SubmitTranscript -> onTranscript(intent.text)
            is VoiceAssistantIntent.SetMicVisibility -> micVisibilityController.setVisible(intent.visible)
            VoiceAssistantIntent.Welcome -> playWelcomeMessage()
        }
    }

    /**
     * Greeting on app open (fires once per process, when the mic first becomes visible). The LONG
     * welcome plays only the very first time — i.e. right after the user registers. On every later
     * launch (2nd, 3rd, …) it's the short "Hey {name}, how can I help" greeting.
     */
    private fun playWelcomeMessage() {
        if (welcomePlayed) return
        welcomePlayed = true
        val firstEver = !preferenceManager.hasShownVoiceWelcome
        Log.d("VoiceAssistant", "playWelcomeMessage firstEver=$firstEver")
        viewModelScope.launch {
            val name = currentUserName()
            val msg = if (firstEver) {
                preferenceManager.hasShownVoiceWelcome = true
                P.welcome(name, lang())
            } else {
                P.quickGreeting(name, lang())
            }
            greet(msg, silenceEndsSession = true)
        }
    }

    /**
     * Mic tapped. If the assistant is busy (speaking or listening) → stop at once with a short
     * confirmation, cutting off whatever it's saying. Otherwise → a quick "how can I help" greeting,
     * then listen. The long welcome plays only on app open, never on a tap.
     */
    private fun toggleMic() {
        if (uiState.value.status in ACTIVE_STATUSES) {
            Log.d("VoiceAssistant", "toggleMic: active → stop")
            resetDialogue()
            say(P.stopped(lang()), VoiceStatus.IDLE) // QUEUE_FLUSH cuts current speech + cancels listen
            return
        }
        Log.d("VoiceAssistant", "toggleMic: idle → quick greeting + listen")
        resetDialogue()
        viewModelScope.launch { greet(P.quickGreeting(currentUserName(), lang()), silenceEndsSession = false) }
    }

    /**
     * Speak a greeting, show the assistant as active, then listen. [silenceEndsSession] = true (the
     * app-open greeting) closes politely if the user stays silent. For a deliberate mic tap it's
     * false, so a miss leads to "didn't catch that — try again" instead of an abrupt goodbye.
     */
    private fun greet(message: String, silenceEndsSession: Boolean) {
        welcomeSession = silenceEndsSession
        retriedOnce = false
        attempts = 0
        setState { copy(expanded = true, status = VoiceStatus.SPEAKING, message = message) }
        speakThenListen(message)
    }

    private suspend fun currentUserName(): String {
        val user = runCatching { roleManager.resolveAccess().user }.getOrNull()
        return user?.fullName?.split(" ")?.firstOrNull().orEmpty()
    }

    // ---- listening ----

    private fun beginListening() {
        Log.d("VoiceAssistant", "beginListening: lang=${lang()} unsupported=${unsupportedLanguage()} available=${speech.isAvailable()}")
        if (unsupportedLanguage()) {
            say(P.unsupportedLanguage(), VoiceStatus.IDLE)
            return
        }
        if (!speech.isAvailable()) {
            say(P.noRecognizer(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
            return
        }
        listenJob?.cancel()
        setState { copy(status = VoiceStatus.LISTENING, message = P.listening(lang())) }
        listenJob = viewModelScope.launch {
            voice.stop() // never let the mic capture our own TTS
            listenWindow()
                .onSuccess { candidates -> onTranscripts(candidates) }
                .onFailure { onNoResponse((it as? VoiceInputException)?.reason ?: "no_match") }
        }
    }

    /**
     * Listen patiently for one utterance: keep re-arming the recognizer on transient no-speech until
     * the user actually speaks or the ~10s [LISTEN_WINDOW_MS] window elapses — so we wait for the user
     * instead of giving up after the engine's own ~2-3s silence timeout. Cancellation propagates
     * through the suspend points, so cancelling [listenJob] stops it cleanly.
     */
    private suspend fun listenWindow(): Result<List<String>> {
        val deadline = System.currentTimeMillis() + LISTEN_WINDOW_MS
        var busyBackoff = REARM_DELAY_MS
        while (coroutineContext.isActive) {
            val res = speech.listenOnce()
            val candidates = res.getOrNull()?.filter { it.isNotBlank() }
            // Heard something → return at once; the reply fires right after the user's ~2.5s pause,
            // never after a flat 10s wait.
            if (!candidates.isNullOrEmpty()) {
                Log.d("VoiceAssistant", "listenWindow: got ${candidates.size} candidate(s)")
                return Result.success(candidates)
            }

            val reason = (res.exceptionOrNull() as? VoiceInputException)?.reason ?: "no_match"
            Log.d("VoiceAssistant", "listenWindow: miss=$reason remaining=${deadline - System.currentTimeMillis()}ms")
            if (reason in TERMINAL_LISTEN_REASONS) return Result.failure(VoiceInputException(reason))

            // Out of time → stop and report it. We don't keep the user past the ~10s window.
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= REARM_DELAY_MS) return Result.failure(VoiceInputException(reason))

            // Back off before re-arming. A fresh recognizer right after destroy() commonly returns
            // ERROR_RECOGNIZER_BUSY; the old 150ms gap busy-spun ~6x/sec and captured NOTHING — the
            // real reason clean commands always came back "samajh nahi aaya". Give the engine room,
            // and back off further while it stays busy.
            val wait = if (reason == "busy") {
                busyBackoff.also { busyBackoff = (busyBackoff * 2).coerceAtMost(BUSY_BACKOFF_MAX_MS) }
            } else {
                REARM_DELAY_MS
            }
            delay(wait.coerceAtMost(remaining))
        }
        return Result.failure(VoiceInputException("no_match"))
    }

    /**
     * Pick the recognizer alternative that best matches a known command. In noise the top result is
     * often wrong while a lower-ranked alternative is exactly right, so we score all of them and take
     * the strongest interpretation — the main reason clean commands stopped being understood.
     */
    private fun onTranscripts(candidates: List<String>) {
        Log.d("VoiceAssistant", "VOICE_RECEIVED: ${candidates.joinToString(", ")}")
        // Short answers (slot-filling / yes-no) → trust the top result; otherwise pick the best match.
        val chosen = if (dialogue != null || awaitingConfirmation) {
            candidates.first()
        } else {
            candidates.maxByOrNull { c ->
                val n = normalizer.normalize(c)
                if (detectStatusQuery(n) != null) 1f else intentDetector.detect(n).confidence
            } ?: candidates.first()
        }
        onTranscript(chosen)
    }

    /** No usable speech within the listen window (or a hard recognizer error). */
    private fun onNoResponse(reason: String) {
        Log.d("VoiceAssistant", "onNoResponse: $reason welcomeSession=$welcomeSession awaitingConfirmation=$awaitingConfirmation")
        // After the welcome greeting, silence → a polite goodbye (not the generic fallback).
        if (welcomeSession) {
            welcomeSession = false
            if (reason == "permission") say(P.micPermission(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
            else say(P.goodbye(lang()), VoiceStatus.IDLE)
            return
        }
        // No answer to a "did you mean X?" suggestion → treat as declined.
        if (awaitingConfirmation) {
            awaitingConfirmation = false
            pendingSuggestion = null
            say(P.suggestionDeclined(lang()), VoiceStatus.IDLE)
            return
        }
        when (reason) {
            "permission" -> say(P.micPermission(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
            "unavailable", "start_failed" -> say(P.noRecognizer(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
            else -> handleMiss()
        }
    }

    /**
     * A miss — either no speech in the window, or speech we couldn't match. Speak a short
     * clarification and give the user one more full ~10s window; after that, stop. Capped by
     * [MAX_LISTEN_ATTEMPTS] so it can never loop forever.
     */
    private fun handleMiss() {
        if (attempts < MAX_LISTEN_ATTEMPTS) {
            attempts++
            speakThenListen(P.didntUnderstand(lang()))
        } else {
            attempts = 0
            say(P.didntUnderstand(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
        }
    }

    // ---- processing ----

    private fun onTranscript(text: String) {
        Log.d("VoiceAssistant", "USER_SAYS: $text")
        welcomeSession = false // the user responded — no goodbye needed
        setState { copy(transcript = text, status = VoiceStatus.PROCESSING) }
        val normalized = normalizer.normalize(text)
        if (normalized.isBlank()) { handleMiss(); return }

        if (isCancelPhrase(normalized)) { cancelByUser(); return }

        if (awaitingConfirmation) { handleConfirmation(normalized); return }

        val active = dialogue
        if (active != null) continueDialogue(active, normalized) else newCommand(normalized)
    }

    private fun newCommand(normalized: String) {
        retriedOnce = false
        // Info questions ("baby size/weight batao", "kaun sa week", "delivery date kya hai",
        // "aaj ka schedule batao") must be ANSWERED aloud — they'd otherwise be shadowed by the
        // generic BABY_PROGRESS navigation intent, whose greedy phrases ("baby"/"baby size") tie or
        // beat the specific value intents. Handle them first when the user is clearly asking.
        detectStatusQuery(normalized)?.let { handleStatusIntent(it); return }
        val detection = intentDetector.detect(normalized)
        val action = actionDetector.detect(normalized)
        val e = entityExtractor.extractAll(normalized)
        val result = VoiceCommandResult(
            intentType = detection.intent,
            action = action,
            confidence = detection.confidence,
            date = e.dateLabel, dateMillis = e.dateMillis,
            time = e.timeLabel, timeOfDayMinutes = e.timeMinutes,
            doctorName = e.doctorName, medicineName = e.medicineName,
            frequency = e.frequency, query = e.query,
            rawText = normalized
        )
        SyncLogger.info("VOICE intent=${detection.intent} action=$action conf=${"%.2f".format(detection.confidence)}")

        if (detection.intent == VoiceIntentType.UNKNOWN || !result.isConfident) {
            // We have a plausible-but-unsure guess → offer it ("did you mean X?"). Truly nothing
            // matched → generic guidance listing what the app can do.
            if (detection.intent != VoiceIntentType.UNKNOWN && detection.confidence >= SUGGEST_FLOOR) {
                suggestFeature(detection.intent)
            } else {
                handleMiss()
            }
            return
        }
        route(detection.intent, action, result)
    }

    private fun route(intent: VoiceIntentType, rawAction: VoiceActionType, result: VoiceCommandResult) {
        attempts = 0 // a confident command was understood — clear the miss counter
        if (intent in STATUS_INTENTS) {
            handleStatusIntent(intent)
            return
        }
        val action = coerceAction(intent, rawAction)
        if (action == VoiceActionType.CREATE || action == VoiceActionType.UPDATE || action == VoiceActionType.DELETE) {
            viewModelScope.launch {
                if (!resolveIsOwner()) { say(P.notAuthorized(lang()), VoiceStatus.IDLE); return@launch }
                if (action == VoiceActionType.CREATE) startCreate(intent, result)
                else openOrSearch(intent, VoiceActionType.OPEN, result) // update/delete handled on-screen
            }
        } else {
            openOrSearch(intent, action, result)
        }
    }

    private fun startCreate(intent: VoiceIntentType, result: VoiceCommandResult) {
        val required = VoiceSlotRules.requiredSlots(intent, VoiceActionType.CREATE)
        val missing = required - VoiceSlotRules.filledSlots(result)
        if (missing.isEmpty()) {
            completeCreate(intent, result)
        } else {
            dialogue = Dialogue(intent, VoiceActionType.CREATE, result)
            askSlot(missing.first())
        }
    }

    private fun continueDialogue(active: Dialogue, normalized: String) {
        val slot = uiState.value.awaitingSlot ?: VoiceSlotRules.requiredSlots(active.intent, active.action)
            .firstOrNull { it !in VoiceSlotRules.filledSlots(active.result) }
        if (slot == null) { completeCreate(active.intent, active.result); dialogue = null; return }

        val merged = mergeSlot(active.result, slot, normalized)
        active.result = merged

        val stillMissing = VoiceSlotRules.requiredSlots(active.intent, active.action) - VoiceSlotRules.filledSlots(merged)
        if (stillMissing.isEmpty()) {
            dialogue = null
            completeCreate(active.intent, merged)
        } else {
            active.followUps++
            if (active.followUps > MAX_FOLLOW_UPS) { resetDialogue(); fallback() }
            else askSlot(stillMissing.first())
        }
    }

    private fun mergeSlot(result: VoiceCommandResult, slot: VoiceSlot, normalized: String): VoiceCommandResult =
        when (slot) {
            VoiceSlot.DATE -> entityExtractor.parseDate(normalized)
                ?.let { result.copy(dateMillis = it.first, date = it.second) } ?: result
            VoiceSlot.TIME -> entityExtractor.parseTime(normalized)
                ?.let { result.copy(timeOfDayMinutes = it.first, time = it.second) } ?: result
            VoiceSlot.MEDICINE_NAME -> result.copy(medicineName = entityExtractor.parseName(normalized) ?: result.medicineName)
            VoiceSlot.TITLE -> result.copy(query = entityExtractor.parseName(normalized) ?: result.query)
            VoiceSlot.DOCTOR_NAME -> result.copy(doctorName = entityExtractor.parseName(normalized) ?: result.doctorName)
            VoiceSlot.FREQUENCY, VoiceSlot.QUERY -> result
        }

    private fun askSlot(slot: VoiceSlot) {
        setState { copy(status = VoiceStatus.AWAITING_SLOT, awaitingSlot = slot, suggestions = emptyList()) }
        speakThenListen(P.askSlot(slot, lang()))
    }

    // ---- completion ----

    private fun completeCreate(intent: VoiceIntentType, result: VoiceCommandResult) {
        resetDialogue()
        when (intent) {
            VoiceIntentType.MEDICINE -> {
                prefillStore.putMedicine(VoicePrefillStore.MedicinePrefill(result.medicineName, result.frequency))
                navigate("add_medicine")
                say(P.medicineCreated(result.medicineName, result.time, lang()), VoiceStatus.IDLE)
            }
            VoiceIntentType.REMINDERS -> {
                prefillStore.putReminder(VoicePrefillStore.ReminderPrefill(result.query, fireTimeMillis(result)))
                navigate("add_reminder")
                say(P.reminderCreated(result.query, result.time, lang()), VoiceStatus.IDLE)
            }
            VoiceIntentType.APPOINTMENT -> {
                prefillStore.requestAutoOpenAdd(VoiceIntentType.APPOINTMENT)
                navigate("appointment")
                say(P.appointmentCreated(result.date, result.doctorName, lang()), VoiceStatus.IDLE)
            }
            else -> {
                prefillStore.requestAutoOpenAdd(intent)
                navigate(listRoute(intent))
                say(P.genericAddOpened(featureName(intent, lang()), lang()), VoiceStatus.IDLE)
            }
        }
    }

    private fun openOrSearch(intent: VoiceIntentType, action: VoiceActionType, result: VoiceCommandResult) {
        resetDialogue()
        // Health / Quick are dashboard TABS, not routes — open Home and select the tab.
        val tab = tabIndexFor(intent)
        if (tab != null) setEffect { VoiceAssistantEffect.NavigateToTab(tab) }
        else navigate(listRoute(intent))
        val name = featureName(intent, lang())
        val msg = if (action == VoiceActionType.SEARCH) P.searching(name, result.query, lang())
        else P.opening(name, lang())
        say(msg, VoiceStatus.IDLE)
    }

    private fun tabIndexFor(intent: VoiceIntentType): Int? = when (intent) {
        VoiceIntentType.HEALTH -> AppTab.HEALTH.ordinal
        VoiceIntentType.QUICK_ACTIONS -> AppTab.ACTIONS.ordinal
        else -> null
    }

    private fun handleStatusIntent(intent: VoiceIntentType) {
        resetDialogue()
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            val user = access.owner
            val start = user?.pregnancyStartDate
            val week = PregnancyProgress.week(start)
            val day = PregnancyProgress.dayOfWeek(start)
            val data = PregnancyDataEngine.getWeekData(week)

            val msg = when (intent) {
                VoiceIntentType.BABY_WEIGHT -> P.babyWeight(data.babyWeight, lang())
                VoiceIntentType.BABY_SIZE -> P.babySize(data.babySize, lang())
                VoiceIntentType.PREGNANCY_WEEK -> P.pregnancyWeek(week, day, lang())
                VoiceIntentType.DELIVERY_DATE -> {
                    val due = user?.dueDate
                    val dateStr = due?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "unknown"
                    val days = PregnancyProgress.daysToGo(due)
                    P.deliveryDate(dateStr, days, lang())
                }
                VoiceIntentType.TODAY_SCHEDULE -> {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val targetUserId = access.activeUserId.ifEmpty { user?.userId.orEmpty() }
                    val meds = medicineRepository.getMedicines(targetUserId).first()
                    val meals = foodRepository.getMeals(targetUserId).first()
                    val statuses = scheduleRepository.getDailyStatuses(targetUserId, today).first()
                    val reminders = reminderRepository.getAllReminders().first()
                        .filter { it.userId == targetUserId && it.date == today }
                    
                    val pendingItems = buildPendingScheduleList(meds, meals, statuses, reminders)
                    P.todaySchedule(pendingItems, lang())
                }
                else -> ""
            }

            if (msg.isNotBlank()) {
                // For status checks, we often want to see the relevant screen.
                if (intent == VoiceIntentType.TODAY_SCHEDULE) navigate("home") // Or specific tab if supported
                else navigate("baby_progress")
                say(msg, VoiceStatus.IDLE)
            } else {
                fallback()
            }
        }
    }

    private fun buildPendingScheduleList(
        meds: List<MedicineEntity>,
        meals: List<MealEntity>,
        statuses: List<DailyScheduleStatusEntity>,
        reminders: List<ReminderEntity>
    ): List<String> {
        val doneByKey = statuses.associate { "${it.type}:${it.refId}" to it.isDone }
        val pending = mutableListOf<String>()

        // Check medicines
        meds.filter { isMedicineActiveToday(it) }.forEach { m ->
            if (doneByKey["MEDICINE:${m.medicineId}"] != true) pending.add(m.name)
        }
        // Check meals
        meals.filter { isToday(it.updatedAt) }.forEach { m ->
            if (doneByKey["MEAL:${m.mealId}"] != true) pending.add(m.mealType)
        }
        // Check reminders
        reminders.forEach { r ->
            if (doneByKey["REMINDER:${r.id}"] != true) pending.add(r.title)
        }
        return pending
    }

    private fun isMedicineActiveToday(m: MedicineEntity): Boolean {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + 24L * 60 * 60 * 1000 - 1
        if (m.startDate > 0L && m.startDate > endOfToday) return false
        if (m.endDate > 0L && m.endDate < startOfToday) return false
        return true
    }

    private fun isToday(epochMillis: Long): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis)) == today
    }

    private fun fallback() {
        resetDialogue()
        say(P.didntUnderstand(lang()), VoiceStatus.FALLBACK, suggestions = TOP)
    }

    /** Offer the closest available feature and listen for a yes/no confirmation. */
    private fun suggestFeature(intent: VoiceIntentType) {
        dialogue = null
        pendingSuggestion = intent
        awaitingConfirmation = true
        setState { copy(status = VoiceStatus.SPEAKING, suggestions = listOf(intent)) }
        speakThenListen(P.didYouMean(featureName(intent, lang()), lang()))
    }

    /** Resolve a "did you mean X?" answer: yes → open it, anything else → polite decline. */
    private fun handleConfirmation(normalized: String) {
        attempts = 0
        val intent = pendingSuggestion
        awaitingConfirmation = false
        pendingSuggestion = null
        if (isYes(normalized) && intent != null) {
            openOrSearch(intent, VoiceActionType.OPEN, VoiceCommandResult())
        } else {
            say(P.suggestionDeclined(lang()), VoiceStatus.IDLE)
        }
    }

    private fun isYes(n: String): Boolean {
        val padded = " $n "
        return YES_WORDS.any { padded.contains(" $it ") }
    }

    /**
     * Maps a data question to the value the assistant should speak, or null if the utterance isn't
     * an info query. "baby"/value keywords answer even without a verb (no dedicated screen for them);
     * week/schedule require an explicit asking word so "schedule kholo" still navigates.
     */
    private fun detectStatusQuery(n: String): VoiceIntentType? {
        val asks = STATUS_VERBS.any { n.contains(it) }
        val baby = n.contains("baby") || n.contains("baccha") || n.contains("बच्च")
        return when {
            (n.contains("weight") || n.contains("vajan") || n.contains("वजन")) && (asks || baby) -> VoiceIntentType.BABY_WEIGHT
            (n.contains("size") || n.contains("साइज") || n.contains("aakar") || n.contains("kitna bada")) && (asks || baby) -> VoiceIntentType.BABY_SIZE
            n.contains("delivery") || n.contains("due date") || n.contains("डिलीवरी") -> VoiceIntentType.DELIVERY_DATE
            n.contains("trimester") || n.contains("ट्राइमेस्टर") -> VoiceIntentType.PREGNANCY_WEEK
            (n.contains("week") || n.contains("hafta") || n.contains("हफ्ता") || n.contains("सप्ताह")) && asks -> VoiceIntentType.PREGNANCY_WEEK
            (n.contains("schedule") || n.contains("shedyul") || n.contains("शेड्यूल")) && asks -> VoiceIntentType.TODAY_SCHEDULE
            else -> null
        }
    }

    // ---- helpers ----

    private fun coerceAction(intent: VoiceIntentType, action: VoiceActionType): VoiceActionType = when (action) {
        VoiceActionType.CREATE -> if (intent in CREATE_CAPABLE) VoiceActionType.CREATE else VoiceActionType.OPEN
        VoiceActionType.SEARCH -> if (intent in SEARCH_CAPABLE) VoiceActionType.SEARCH else VoiceActionType.OPEN
        VoiceActionType.UPDATE, VoiceActionType.DELETE -> action
        else -> VoiceActionType.OPEN
    }

    private suspend fun resolveIsOwner(): Boolean {
        cachedIsOwner?.let { return it }
        val owner = runCatching { roleManager.resolveAccess().isOwner }.getOrDefault(false)
        cachedIsOwner = owner
        return owner
    }

    private fun fireTimeMillis(result: VoiceCommandResult): Long? {
        val minutes = result.timeOfDayMinutes ?: return null
        val baseMidnight = result.dateMillis
            ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return baseMidnight + minutes * 60_000L
    }

    private fun navigate(route: String) = setEffect { VoiceAssistantEffect.Navigate(route) }

    /**
     * Speak [text], then start listening. Listening is chained to TTS's onDone for precise timing,
     * but a watchdog also fires after the estimated speech duration — so if TTS is slow or never
     * initialises (engine missing), the mic still opens instead of being left dead.
     */
    private fun speakThenListen(text: String) {
        setState { copy(message = text) }
        listenJob?.cancel()
        var started = false
        fun begin() {
            if (started) return
            started = true
            beginListening()
        }
        voice.speak(text) { begin() } // precise: listen the moment the greeting/prompt finishes
        listenJob = viewModelScope.launch {
            delay(ttsDurationMs(text) + LISTEN_WATCHDOG_MS)
            if (!started) Log.d("VoiceAssistant", "speakThenListen: watchdog opening mic (TTS onDone never fired)")
            begin()
        }
    }

    private fun say(text: String, status: VoiceStatus, suggestions: List<VoiceIntentType> = emptyList()) {
        listenJob?.cancel()
        voice.speak(text)
        setState { copy(message = text, status = status, suggestions = suggestions, awaitingSlot = null) }
    }

    private fun cancelByUser() {
        resetDialogue()
        say(P.cancelled(lang()), VoiceStatus.IDLE)
    }

    private fun dismiss() {
        listenJob?.cancel()
        voice.stop()
        resetDialogue()
        setState { VoiceAssistantState() }
    }

    private fun resetDialogue() {
        dialogue = null
        retriedOnce = false
        welcomeSession = false
        awaitingConfirmation = false
        pendingSuggestion = null
        attempts = 0
        setState { copy(awaitingSlot = null, suggestions = emptyList()) }
    }

    private fun lang(): String = preferenceManager.selectedLanguage
    private fun unsupportedLanguage(): Boolean = preferenceManager.selectedLanguage in setOf("Gujarati", "Marathi")
    private fun isCancelPhrase(n: String): Boolean = CANCELS.any { n.contains(it) }
    private fun ttsDurationMs(text: String): Long = (700L + text.length * 55L).coerceAtMost(6000L)

    private fun listRoute(intent: VoiceIntentType): String = when (intent) {
        VoiceIntentType.HOME -> "home"
        VoiceIntentType.APPOINTMENT -> "appointment"
        VoiceIntentType.REPORTS -> "reports"
        VoiceIntentType.MEDICINE -> "medicine"
        VoiceIntentType.FOOD -> "food"
        VoiceIntentType.SYMPTOM -> "symptom"
        VoiceIntentType.CHAT -> "chat"
        VoiceIntentType.FAMILY -> "family"
        VoiceIntentType.BILLING -> "billing"
        VoiceIntentType.PROFILE -> "profile"
        VoiceIntentType.SETTINGS -> "settings"
        VoiceIntentType.REMINDERS -> "reminders"
        VoiceIntentType.NOTIFICATION_HISTORY -> "notification_history"
        VoiceIntentType.JOURNAL -> "journal"
        VoiceIntentType.CONTRACTION_TIMER -> "contraction_timer"
        VoiceIntentType.BABY_PROGRESS -> "baby_progress"
        VoiceIntentType.BABY_WEIGHT -> "baby_progress"
        VoiceIntentType.BABY_SIZE -> "baby_progress"
        VoiceIntentType.PREGNANCY_WEEK -> "baby_progress"
        VoiceIntentType.DELIVERY_DATE -> "baby_progress"
        VoiceIntentType.TODAY_SCHEDULE -> "home"
        VoiceIntentType.HEALTH -> "home"
        VoiceIntentType.QUICK_ACTIONS -> "home"
        VoiceIntentType.HELP_SUPPORT -> "help_support"
        VoiceIntentType.UNKNOWN -> "home"
    }

    private fun featureName(intent: VoiceIntentType, lang: String): String = P.featureName(intent, lang)

    companion object {
        private const val MAX_FOLLOW_UPS = 3
        // Below the confidence threshold but at/above this, we still offer the guess as "did you mean…".
        private const val SUGGEST_FLOOR = 0.2f
        // How long to keep listening for the user before treating it as no-response (≈10s patience).
        private const val LISTEN_WINDOW_MS = 10_000L
        // Gap before re-arming the recognizer after a transient miss. Must be long enough that a fresh
        // recognizer doesn't immediately hit ERROR_RECOGNIZER_BUSY (the old 150ms gap busy-spun and
        // caught nothing). A "busy" result backs off further, up to [BUSY_BACKOFF_MAX_MS].
        private const val REARM_DELAY_MS = 500L
        private const val BUSY_BACKOFF_MAX_MS = 1500L
        // After a miss, clarify and give this many more full windows before stopping (1 → two windows).
        private const val MAX_LISTEN_ATTEMPTS = 1
        // Extra grace beyond the estimated TTS duration before the watchdog opens the mic anyway.
        private const val LISTEN_WATCHDOG_MS = 2500L
        // Statuses where a mic tap means "stop now", not "start a new greeting + listen".
        private val ACTIVE_STATUSES = setOf(
            VoiceStatus.LISTENING, VoiceStatus.PROCESSING, VoiceStatus.SPEAKING, VoiceStatus.AWAITING_SLOT
        )
        // Recognizer failures that mean "stop now" rather than "keep waiting for the user to speak".
        private val TERMINAL_LISTEN_REASONS = setOf("permission", "unavailable", "start_failed")
        // Affirmatives for the "did you mean X?" confirmation (English + Hindi + Hinglish).
        private val YES_WORDS = listOf(
            "yes", "yeah", "yep", "yup", "ok", "okay", "sure", "correct", "right",
            "haan", "han", "ha", "haanji", "ji", "ji haan", "theek", "theek hai", "thik", "thik hai", "sahi", "bilkul",
            "हाँ", "हां", "जी", "ठीक", "सही", "बिल्कुल"
        )
        private val CREATE_CAPABLE = setOf(
            VoiceIntentType.APPOINTMENT, VoiceIntentType.MEDICINE, VoiceIntentType.REMINDERS,
            VoiceIntentType.SYMPTOM, VoiceIntentType.JOURNAL, VoiceIntentType.BILLING, VoiceIntentType.FOOD
        )
        private val SEARCH_CAPABLE = setOf(
            VoiceIntentType.APPOINTMENT, VoiceIntentType.REPORTS, VoiceIntentType.MEDICINE, VoiceIntentType.BILLING
        )
        private val STATUS_INTENTS = setOf(
            VoiceIntentType.BABY_WEIGHT, VoiceIntentType.BABY_SIZE,
            VoiceIntentType.PREGNANCY_WEEK, VoiceIntentType.DELIVERY_DATE,
            VoiceIntentType.TODAY_SCHEDULE
        )
        private val TOP = listOf(VoiceIntentType.APPOINTMENT, VoiceIntentType.REMINDERS, VoiceIntentType.MEDICINE, VoiceIntentType.REPORTS)
        private val CANCELS = listOf("cancel", "rehne do", "rehne de", "chodo", "cancel karo", "रहने दो", "रद्द")
        // Asking words that signal the user wants a value spoken (English + Hindi + Hinglish).
        private val STATUS_VERBS = listOf(
            "batao", "bato", "bata do", "bata", "kya", "kya hai", "kitna", "kitni", "kitne",
            "kaisa", "kaisi", "kab", "kaun sa", "kon sa", "konsa", "chal raha", "baaki", "bache",
            "tell", "what", "how", "when", "which",
            "बताओ", "क्या", "कितना", "कब", "कौन"
        )
    }
}
