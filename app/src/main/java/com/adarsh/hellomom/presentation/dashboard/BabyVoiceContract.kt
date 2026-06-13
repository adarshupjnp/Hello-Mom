package com.adarsh.hellomom.presentation.dashboard

import com.adarsh.hellomom.core.UiState

data class BabyVoiceState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val message: String = "",
    val faceEmoji: String = "😊",
    val isSpeaking: Boolean = false
) : UiState

enum class VoiceStatus {
    Idle, Generating, Speaking, PlayingSound, Error
}
