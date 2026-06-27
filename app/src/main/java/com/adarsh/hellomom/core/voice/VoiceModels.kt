package com.adarsh.hellomom.core.voice

/**
 * Models for the offline voice-command pipeline. Everything here is plain data — no Android or
 * network dependencies — so it is trivially unit-testable.
 */

/** Languages the command pipeline can parse. Gujarati/Marathi are recognised as UNSUPPORTED. */
enum class VoiceLanguage { ENGLISH, HINDI, HINGLISH, UNSUPPORTED }

/** A feature the assistant can act on. Each maps to a real destination in `navigation/Screen.kt`. */
enum class VoiceIntentType {
    HOME,
    APPOINTMENT,
    REPORTS,
    MEDICINE,
    FOOD,
    SYMPTOM,
    CHAT,
    FAMILY,
    BILLING,
    PROFILE,
    SETTINGS,
    REMINDERS,
    NOTIFICATION_HISTORY,
    JOURNAL,
    CONTRACTION_TIMER,
    BABY_PROGRESS,
    BABY_WEIGHT,
    BABY_SIZE,
    BABY_LENGTH,
    PREGNANCY_WEEK,
    DELIVERY_DATE,
    TODAY_SCHEDULE,
    HEALTH,
    QUICK_ACTIONS,
    HELP_SUPPORT,
    KICK_COUNT,
    WATER_INTAKE,
    EMERGENCY,
    MOTIVATION,
    UNKNOWN
}

/** What the user wants to do with the intent. */
enum class VoiceActionType { OPEN, SEARCH, CREATE, UPDATE, DELETE, NONE }

/** A slot the assistant may need to fill before it can complete a CREATE handoff. */
enum class VoiceSlot { DATE, TIME, DOCTOR_NAME, MEDICINE_NAME, FREQUENCY, TITLE, QUERY }

/**
 * Result of running one utterance through the pipeline.
 *
 * [dateMillis] is midnight of the resolved date; [timeOfDayMinutes] is minutes since midnight.
 * The ViewModel combines them into a concrete fire time for reminders.
 */
data class VoiceCommandResult(
    val intentType: VoiceIntentType = VoiceIntentType.UNKNOWN,
    val action: VoiceActionType = VoiceActionType.NONE,
    val confidence: Float = 0f,
    val date: String? = null,             // human-readable, e.g. "Mon, 30 Jun 2026"
    val dateMillis: Long? = null,         // midnight of the resolved date
    val time: String? = null,             // human-readable, e.g. "08:00 AM"
    val timeOfDayMinutes: Int? = null,    // minutes since midnight
    val doctorName: String? = null,
    val medicineName: String? = null,
    val frequency: String? = null,        // e.g. "Daily"
    val query: String? = null,
    val rawText: String = ""
) {
    /** Minimum confidence below which we treat the intent as a miss and ask for clarification. */
    val isConfident: Boolean get() = confidence >= CONFIDENCE_THRESHOLD

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.45f
    }
}

/** Required + optional slots per (intent, action). Drives the slot-filling dialogue. */
object VoiceSlotRules {

    /** Slots that MUST be present before a CREATE handoff. Empty list → no questions needed. */
    fun requiredSlots(intent: VoiceIntentType, action: VoiceActionType): List<VoiceSlot> {
        if (action != VoiceActionType.CREATE) return emptyList()
        return when (intent) {
            VoiceIntentType.APPOINTMENT -> listOf(VoiceSlot.DATE)
            VoiceIntentType.MEDICINE -> listOf(VoiceSlot.MEDICINE_NAME, VoiceSlot.TIME)
            VoiceIntentType.REMINDERS -> listOf(VoiceSlot.TITLE, VoiceSlot.TIME)
            // BILLING / FOOD / SYMPTOM / JOURNAL open their own add dialog and collect fields there.
            else -> emptyList()
        }
    }

    /** Which slots a value present in [result] already satisfies. */
    fun filledSlots(result: VoiceCommandResult): Set<VoiceSlot> = buildSet {
        if (result.dateMillis != null) add(VoiceSlot.DATE)
        if (result.timeOfDayMinutes != null) add(VoiceSlot.TIME)
        if (!result.doctorName.isNullOrBlank()) add(VoiceSlot.DOCTOR_NAME)
        if (!result.medicineName.isNullOrBlank()) add(VoiceSlot.MEDICINE_NAME)
        if (!result.frequency.isNullOrBlank()) add(VoiceSlot.FREQUENCY)
        if (!result.query.isNullOrBlank()) add(VoiceSlot.TITLE)
    }
}
