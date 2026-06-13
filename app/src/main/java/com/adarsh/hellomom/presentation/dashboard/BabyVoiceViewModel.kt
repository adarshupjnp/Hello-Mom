package com.adarsh.hellomom.presentation.dashboard

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.R
import com.adarsh.hellomom.domain.usecase.GenerateBabyMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BabyVoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generateBabyMessageUseCase: GenerateBabyMessageUseCase
) : ViewModel(), TextToSpeech.OnInitListener {

    private val _uiState = MutableStateFlow(BabyVoiceState())
    val uiState = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var animationJob: Job? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setSpeechRate(0.85f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _uiState.update { it.copy(status = VoiceStatus.Speaking, isSpeaking = true) }
                    startAnimation()
                }

                override fun onDone(utteranceId: String?) {
                    stopAnimation()
                    _uiState.update { it.copy(status = VoiceStatus.PlayingSound, isSpeaking = false, faceEmoji = "😊") }
                    playRandomLaugh()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _uiState.update { it.copy(status = VoiceStatus.Error, isSpeaking = false) }
                    stopAnimation()
                }
            })
        }
    }

    fun onHearBabyClicked(week: Int, weight: String) {
        if (_uiState.value.isSpeaking) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = VoiceStatus.Generating) }
            val message = generateBabyMessageUseCase.execute(week, weight)
            _uiState.update { it.copy(message = message) }
            
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "BABY_VOICE")
        }
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            val faces = listOf("😐", "😮", "😊")
            var index = 0
            while (true) {
                _uiState.update { it.copy(faceEmoji = faces[index]) }
                index = (index + 1) % faces.size
                delay(400)
            }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun playRandomLaugh() {
        val laughs = listOf(
            R.raw.baby_laugh_soft,
            R.raw.baby_giggle,
            R.raw.baby_happy_chuckle
        )
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, laughs.random())
        mediaPlayer?.setOnCompletionListener { mp ->
            mp.release()
            mediaPlayer = null
            _uiState.update { it.copy(status = VoiceStatus.Idle) }
        }
        mediaPlayer?.start()
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        stopAnimation()
        super.onCleared()
    }
}
