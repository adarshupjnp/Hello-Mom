package com.adarsh.hellomom.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off from the voice assistant to a target screen. The assistant fills this, then
 * navigates; the destination screen consumes it once on first composition and clears it, so it
 * never re-triggers on a later manual visit. This keeps prefill additive — no screen ViewModel or
 * navigation route signature has to change.
 *
 * Each consume* method reads-and-clears atomically.
 */
@Singleton
class VoicePrefillStore @Inject constructor() {

    data class MedicinePrefill(val name: String?, val frequency: String?)
    data class ReminderPrefill(val title: String?, val timeMillis: Long?)

    @Volatile private var medicine: MedicinePrefill? = null
    @Volatile private var reminder: ReminderPrefill? = null
    @Volatile private var autoOpenAdd: VoiceIntentType? = null

    @Synchronized fun putMedicine(prefill: MedicinePrefill) { medicine = prefill }
    @Synchronized fun consumeMedicine(): MedicinePrefill? = medicine.also { medicine = null }

    @Synchronized fun putReminder(prefill: ReminderPrefill) { reminder = prefill }
    @Synchronized fun consumeReminder(): ReminderPrefill? = reminder.also { reminder = null }

    /** Ask a list screen to pop its "add" dialog open when the user lands on it. */
    @Synchronized fun requestAutoOpenAdd(intent: VoiceIntentType) { autoOpenAdd = intent }

    /** @return true exactly once if an auto-open was requested for [target]. */
    @Synchronized fun consumeAutoOpenAdd(target: VoiceIntentType): Boolean =
        if (autoOpenAdd == target) { autoOpenAdd = null; true } else false
}
