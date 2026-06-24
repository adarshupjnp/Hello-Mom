package com.adarsh.hellomom.core.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceAssistant @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    // Runs once (on the main thread) when the current utterance finishes or errors, then clears.
    // Lets callers chain listening to the exact end of a spoken prompt instead of a fixed delay.
    @Volatile private var onUtteranceDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyVoiceProfile()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = fireUtteranceDone()
                override fun onError(utteranceId: String?) = fireUtteranceDone()
            })
            isTtsReady = true
        }
    }

    private fun fireUtteranceDone() {
        val cb = onUtteranceDone ?: return
        onUtteranceDone = null
        mainHandler.post(cb)
    }

    /**
     * Set the locale to the user's selected language and tune the voice to sound like a clear,
     * young (~20–25) female speaker: a female engine voice for that locale when one is installed,
     * with a slightly raised pitch and a natural rate. Re-applied before every utterance so a
     * language change on the Login/Profile screen takes effect immediately.
     */
    private fun applyVoiceProfile() {
        val locale = getLocaleFromPreferences()
        tts?.language = locale
        tts?.setPitch(VOICE_PITCH)
        tts?.setSpeechRate(VOICE_SPEECH_RATE)
        selectFemaleVoice(locale)
    }

    /**
     * Best-effort female-voice selection. Android exposes no gender flag, so we match the common
     * "female" markers in engine voice names for the locale; if none match we fall back to the
     * lowest-latency installed voice for that language. The raised pitch keeps the young-female
     * feel even on engines that only ship a neutral voice.
     */
    private fun selectFemaleVoice(locale: Locale) {
        val engine = tts ?: return
        val voices = runCatching { engine.voices }.getOrNull() ?: return
        val sameLanguage = voices.filter { v ->
            v.locale?.language == locale.language &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        if (sameLanguage.isEmpty()) return
        val femaleMarkers = listOf("female", "#female", "-f-", "_f_", "fem")
        val chosen = sameLanguage.firstOrNull { v ->
            val n = v.name.lowercase()
            femaleMarkers.any { n.contains(it) }
        } ?: sameLanguage.minByOrNull { it.latency }
        chosen?.let { runCatching { engine.voice = it } }
    }

    private fun getLocaleFromPreferences(): Locale {
        val prefs = context.getSharedPreferences("hello_mom_prefs", Context.MODE_PRIVATE)
        // Default Hinglish (matches PreferenceManager). Hinglish prompts are romanized, so they are
        // spoken by the Indian-English (en-IN) voice — a Hindi voice can't read Latin script well.
        val language = prefs.getString("selected_language", "Hinglish") ?: "Hinglish"
        return when (language) {
            "Hindi" -> Locale("hi")
            "Hinglish" -> Locale("en", "IN")
            "Gujarati" -> Locale("gu")
            "Marathi" -> Locale("mr")
            else -> Locale.ENGLISH
        }
    }

    /**
     * Speak [text] in the selected language's young-female voice. If [onDone] is given, it runs on
     * the main thread once the utterance finishes — used to start listening exactly when the
     * greeting/prompt ends.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isTtsReady) {
            applyVoiceProfile() // Ensure language + young-female voice are correct before speaking.
            onUtteranceDone = onDone
            // Drop emojis/icons so TTS doesn't read them aloud (e.g. ❤️ as "dil").
            tts?.speak(
                sanitizeForSpeech(text),
                TextToSpeech.QUEUE_FLUSH,
                null,
                if (onDone != null) UTTERANCE_ID else null
            )
        }
    }

    companion object {
        // Slightly raised pitch + natural rate → clear, young-adult female delivery.
        private const val VOICE_PITCH = 1.2f
        private const val VOICE_SPEECH_RATE = 1.0f
        private const val UTTERANCE_ID = "hm_voice_utt"
    }

    fun stop() {
        // Don't fire a pending completion callback when we deliberately stop (e.g. before listening).
        onUtteranceDone = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    fun startListening(onResult: (String) -> Unit) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocaleFromPreferences())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                recognizer.destroy()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }
}
