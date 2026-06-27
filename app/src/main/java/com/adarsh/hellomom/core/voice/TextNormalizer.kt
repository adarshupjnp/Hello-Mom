package com.adarsh.hellomom.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Normalises a raw spoken transcript into a clean, lower-cased token string for the detectors.
 * Handles English, Hindi (Devanagari) and Hinglish. No network, no Android dependency.
 */
@Singleton
class TextNormalizer @Inject constructor() {

    fun normalize(raw: String): String {
        var text = raw.lowercase().trim()
        // Collapse common Hinglish spelling variants so the dictionaries can stay small.
        VARIANTS.forEach { (from, to) -> text = text.replace(from, to) }
        // Drop punctuation but keep Devanagari, latin letters, digits and spaces.
        text = text.replace(PUNCTUATION, " ")
        // Collapse whitespace.
        return text.replace(MULTISPACE, " ").trim()
    }

    /** Detect the language family of a normalized string so we can degrade gracefully. */
    fun detectLanguage(normalized: String): VoiceLanguage {
        val hasDevanagari = normalized.any { it in 'ऀ'..'ॿ' }
        val hasLatin = normalized.any { it in 'a'..'z' }
        // Gujarati (U+0A80–U+0AFF) / Marathi uses Devanagari but with markers we don't parse;
        // we only positively support Devanagari-Hindi + latin. Detection here is best-effort and
        // the ViewModel separately blocks Gujarati/Marathi via the selected_language preference.
        return when {
            hasDevanagari && hasLatin -> VoiceLanguage.HINGLISH
            hasDevanagari -> VoiceLanguage.HINDI
            hasLatin -> VoiceLanguage.ENGLISH
            else -> VoiceLanguage.UNSUPPORTED
        }
    }

    companion object {
        private val PUNCTUATION = Regex("[.,!?;:()\\[\\]{}\"'`/\\\\|@#%^&*=+~<>]")
        private val MULTISPACE = Regex("\\s+")

        // Frequent ASR/Hinglish variants → canonical form used in the dictionaries.
        private val VARIANTS = listOf(
            "appoinment" to "appointment",
            "apointment" to "appointment",
            "medecine" to "medicine",
            "medicin" to "medicine",
            "remainder" to "reminder",
            "reminda" to "reminder",
            "dava" to "dawa",
            "dvai" to "dawa",
            "dawai" to "dawa",
            "kholo" to "kholo",
            "dikhaa" to "dikha",
            "dikhao" to "dikha",
            "lagaa" to "laga",
            "lagao" to "laga",
            "banaa" to "bana",
            "banao" to "bana",
            // Devanagari verbs → canonical latin tokens, so Hindi (hi-IN) transcriptions match the
            // same dictionary phrases as their Hinglish spellings (e.g. "रिपोर्ट दिखाओ" → "… dikha").
            "दिखाओ" to "dikha",
            "बताओ" to "batao",
            "खोलो" to "kholo",
            "लगाओ" to "laga",
            "बनाओ" to "bana"
        )
    }
}
