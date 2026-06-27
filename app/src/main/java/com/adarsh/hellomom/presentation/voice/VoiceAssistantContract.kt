package com.adarsh.hellomom.presentation.voice

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.core.voice.VoiceSlot

enum class VoiceStatus { IDLE, LISTENING, PROCESSING, SPEAKING, AWAITING_SLOT, FOLLOW_UP, FALLBACK }

sealed class VoiceAssistantIntent : UiIntent {
    /** Mic tapped (permission already granted) — open the panel and start listening. */
    object OpenAndListen : VoiceAssistantIntent()
    /** Listen for one more utterance (re-arm, e.g. during slot-filling or after a miss). */
    object StartListening : VoiceAssistantIntent()
    /** User cancelled the current command / dialogue. */
    object Cancel : VoiceAssistantIntent()
    /** Close the assistant panel. */
    object Dismiss : VoiceAssistantIntent()
    /** Mic permission was denied — show guidance instead of listening. */
    object PermissionDenied : VoiceAssistantIntent()
    /** User tapped one of the fallback suggestion chips. */
    data class QuickPick(val intent: VoiceIntentType) : VoiceAssistantIntent()
    /** Feed a transcript directly (used by tests and any typed-input path). */
    data class SubmitTranscript(val text: String) : VoiceAssistantIntent()
    /** Force the mic button to hide (e.g. while a full-screen AI chat is open). */
    data class SetMicVisibility(val visible: Boolean) : VoiceAssistantIntent()
    /** Speak a welcome greeting to the user. */
    object Welcome : VoiceAssistantIntent()
}

data class VoiceAssistantState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val expanded: Boolean = false,
    /** Last thing the user said (shown in the panel). */
    val transcript: String = "",
    /** Last thing the assistant said / is asking (shown + spoken). */
    val message: String = "",
    /** Quick-tap suggestions shown on a miss. */
    val suggestions: List<VoiceIntentType> = emptyList(),
    /** Non-null while the assistant is waiting for the user to supply a specific field. */
    val awaitingSlot: VoiceSlot? = null,
    /** True when the selected language is Hindi/Hinglish (drives chip labels + UI copy). */
    val hindi: Boolean = true,
    /** False if an external component (like a full-screen AI chat) has requested the mic to hide. */
    val micVisible: Boolean = true
) : UiState

sealed class VoiceAssistantEffect : UiEffect {
    /** Navigate to a real route from `navigation/Screen.kt`. */
    data class Navigate(val route: String) : VoiceAssistantEffect()
    /** Open the Home dashboard and select one of its tabs (e.g. Health, Quick). */
    data class NavigateToTab(val tabIndex: Int) : VoiceAssistantEffect()
    /** Immediately open the dialer for emergency services (102). */
    object DialEmergency : VoiceAssistantEffect()
}
