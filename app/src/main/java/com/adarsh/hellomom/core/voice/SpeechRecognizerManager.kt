package com.adarsh.hellomom.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.adarsh.hellomom.data.local.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Thrown when a single recognition attempt fails; [reason] is a short, user-presentable cause. */
class VoiceInputException(val reason: String) : Exception(reason)

/**
 * Owns COMMAND speech input for the voice assistant. Deliberately separate from
 * [com.adarsh.hellomom.core.utils.VoiceAssistant], which owns speech OUTPUT and its own
 * `startListening`. Only one [SpeechRecognizer] may be live at a time on Android, so each call here
 * creates and destroys its own recognizer; callers must not run this while VoiceAssistant is
 * listening. [listenOnce] is re-armable, which is what powers multi-turn slot-filling.
 */
@Singleton
class SpeechRecognizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Listen for a single utterance and return the best transcript. Runs the recognizer on the main
     * thread (Android requirement) and always destroys it before returning, so it can be called
     * again immediately for the next slot-filling turn.
     */
    suspend fun listenOnce(): Result<String> = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return@withContext Result.failure(VoiceInputException("unavailable"))
        }
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Give the user time to start/finish speaking (≈5s of silence before timeout) — hints
                // the engine may honour; benefits the welcome's "wait then say goodbye" and slot-filling.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            }

            var done = false
            fun finish(result: Result<String>) {
                if (done) return
                done = true
                runCatching { recognizer.destroy() }
                if (cont.isActive) cont.resume(result)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (text.isBlank()) finish(Result.failure(VoiceInputException("no_match")))
                    else finish(Result.success(text))
                }

                override fun onError(error: Int) {
                    finish(Result.failure(VoiceInputException(errorReason(error))))
                }
            })

            cont.invokeOnCancellation {
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { finish(Result.failure(VoiceInputException("start_failed"))) }
        }
    }

    /** BCP-47 tag for the recognizer, from the user's selected language (default Hindi). */
    private fun languageTag(): String = when (preferenceManager.selectedLanguage) {
        "Hindi" -> "hi-IN"
        "Gujarati" -> "gu-IN"
        "Marathi" -> "mr-IN"
        else -> "en-IN"
    }

    private fun errorReason(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
        else -> "error"
    }
}
