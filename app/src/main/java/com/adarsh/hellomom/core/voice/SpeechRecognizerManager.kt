package com.adarsh.hellomom.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
     * Listen for one utterance and return the recognizer's alternatives (best first, blanks dropped).
     * Returning several candidates lets the caller pick the one that best matches a known command —
     * far more robust in noisy surroundings than trusting only the top guess. Runs on the main thread
     * (Android requirement) and always destroys the recognizer before returning, so it is re-armable.
     */
    suspend fun listenOnce(): Result<List<String>> = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return@withContext Result.failure(VoiceInputException("unavailable"))
        }
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
                // Capture partial hypotheses. The Google recognizer sometimes fires a FINAL onResults
                // that is EMPTY even though it clearly heard speech (onBeginningOfSpeech fired) — in
                // that case we fall back to the last partial. This was why clearly-spoken commands
                // came back blank → "no_match" → goodbye.
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Ask for several alternatives → the caller picks the best-matching one (noise robustness).
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Deliberately NOT setting EXTRA_PREFER_OFFLINE: on devices where the offline language
                // model isn't downloaded it makes recognition return NOTHING every time. Let the system
                // choose offline/online — it's the same free, on-device Google recognizer either way.
                // Finalise after ~2.5s of silence — the user's "stop for 2-3s, then reply" — long enough
                // not to cut someone off mid-sentence. No MINIMUM_LENGTH floor (it only added latency).
                // (Hints the engine may honour.)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
            }

            var done = false
            var lastPartial: List<String> = emptyList()
            fun finish(result: Result<List<String>>) {
                if (done) return
                done = true
                runCatching { recognizer.destroy() }
                if (cont.isActive) cont.resume(result)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { Log.d("VoiceAssistant", "STT onReadyForSpeech") }
                override fun onBeginningOfSpeech() { Log.d("VoiceAssistant", "STT onBeginningOfSpeech") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { Log.d("VoiceAssistant", "STT onEndOfSpeech") }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        lastPartial = partial
                        Log.d("VoiceAssistant", "STT onPartialResults: $partial")
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val candidates = (results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty())
                        .ifEmpty { lastPartial } // recognizer sometimes finalises EMPTY → keep last partial
                    Log.d("VoiceAssistant", "STT onResults → $candidates")
                    if (candidates.isEmpty()) finish(Result.failure(VoiceInputException("no_match")))
                    else finish(Result.success(candidates))
                }

                override fun onError(error: Int) {
                    // Some engines error out (NO_MATCH/TIMEOUT) AFTER delivering good partials — keep
                    // what we heard rather than throwing it away.
                    if (lastPartial.isNotEmpty()) {
                        Log.d("VoiceAssistant", "STT onError code=$error → using last partial: $lastPartial")
                        finish(Result.success(lastPartial))
                    } else {
                        Log.d("VoiceAssistant", "STT onError: code=$error (${errorReason(error)})")
                        finish(Result.failure(VoiceInputException(errorReason(error))))
                    }
                }
            })

            cont.invokeOnCancellation {
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }

            Log.d("VoiceAssistant", "STT startListening lang=${languageTag()} available=${SpeechRecognizer.isRecognitionAvailable(context)}")
            runCatching { recognizer.startListening(intent) }
                .onFailure {
                    Log.d("VoiceAssistant", "STT startListening threw: ${it.message}")
                    finish(Result.failure(VoiceInputException("start_failed")))
                }
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
