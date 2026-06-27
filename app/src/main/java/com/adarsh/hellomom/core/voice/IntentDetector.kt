package com.adarsh.hellomom.core.voice

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Dictionary + synonym + fuzzy (Levenshtein) intent detection across English, Hindi and Hinglish.
 * Returns the best [VoiceIntentType] and a confidence in [0,1]. Fully offline and deterministic.
 */
@Singleton
class IntentDetector @Inject constructor() {

    data class Detection(val intent: VoiceIntentType, val confidence: Float)

    fun detect(normalized: String): Detection {
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Detection(VoiceIntentType.UNKNOWN, 0f)

        var best = VoiceIntentType.UNKNOWN
        var bestScore = 0f
        for ((intent, phrases) in DICTIONARY) {
            val score = phrases.maxOf { phrase -> phraseScore(phrase, normalized, tokens) }
            if (score > bestScore) {
                bestScore = score
                best = intent
            }
        }
        return Detection(best, bestScore)
    }

    /** Score one dictionary phrase against the input. Contiguous-substring match scores highest. */
    private fun phraseScore(phrase: String, normalized: String, inputTokens: List<String>): Float {
        if (phrase.contains(' ') && normalized.contains(phrase)) return 1f
        val phraseTokens = phrase.split(' ').filter { it.isNotBlank() }
        if (phraseTokens.isEmpty()) return 0f

        var matched = 0
        var strongMatched = false
        for (pt in phraseTokens) {
            val hit = inputTokens.any { fuzzyEquals(pt, it) }
            if (hit) {
                matched++
                if (pt.length >= 4) strongMatched = true
            }
        }
        val coverage = matched.toFloat() / phraseTokens.size
        // Require at least one "content" token to match so stopword-only overlaps don't win.
        return if (strongMatched || phraseTokens.all { it.length < 4 }) coverage else coverage * 0.4f
    }

    /** Exact for short tokens; Levenshtein-tolerant for longer ones (handles ASR typos). */
    private fun fuzzyEquals(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = min(a.length, b.length)
        if (shorter < 4) return false
        val tolerance = if (max(a.length, b.length) >= 7) 2 else 1
        return levenshtein(a, b) <= tolerance
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    companion object {
        // Phrases are pre-normalized (lower-case, no punctuation). Devanagari + Hinglish included.
        private val DICTIONARY: Map<VoiceIntentType, List<String>> = mapOf(
            VoiceIntentType.APPOINTMENT to listOf(
                "appointment", "doctor appointment", "doctor se milna", "doctor se appointment",
                "book appointment", "appointment book", "appointment laga", "डॉक्टर", "अपॉइंटमेंट",
                "डॉक्टर की अपॉइंटमेंट", "doctor"
            ),
            VoiceIntentType.REPORTS to listOf(
                "report", "reports", "blood report", "lab report", "medical report", "report dikha",
                "document", "documents", "रिपोर्ट", "मेरी रिपोर्ट"
            ),
            VoiceIntentType.MEDICINE to listOf(
                "medicine", "medicine reminder", "dawa", "dawa reminder", "tablet", "medicine laga",
                "dawa laga", "pill", "दवा", "दवा रिमाइंडर", "गोली"
            ),
            VoiceIntentType.FOOD to listOf(
                "food", "diet", "meal", "nutrition", "khana", "khana add", "diet plan", "meal add",
                "खाना", "डाइट", "भोजन", "water", "pani", "पानी"
            ),
            VoiceIntentType.SYMPTOM to listOf(
                "symptom", "symptoms", "lakshan", "symptom add", "log symptom", "tabiyat",
                "लक्षण", "तबियत", "symptom check"
            ),
            VoiceIntentType.CHAT to listOf(
                "chat", "ai chat", "assistant", "doctor chat", "ask doctor", "baat karo",
                "सहायक", "चैट"
            ),
            VoiceIntentType.FAMILY to listOf(
                "family", "family members", "parivar", "family dashboard", "add family",
                "परिवार", "घरवाले"
            ),
            VoiceIntentType.BILLING to listOf(
                "billing", "bill", "expense", "expenses", "payment", "kharcha", "bill add",
                "kharcha add", "बिल", "खर्चा", "भुगतान", "bill dikhao", "kharcha dikhao"
            ),
            VoiceIntentType.PROFILE to listOf(
                "profile", "my profile", "meri profile", "account", "प्रोफाइल", "प्रोफ़ाइल",
                "profile dikhao", "profile kholo"
            ),
            VoiceIntentType.SETTINGS to listOf(
                "settings", "setting", "settings kholo", "सेटिंग", "सेटिंग्स", "setting dikhao"
            ),
            VoiceIntentType.REMINDERS to listOf(
                "reminder", "reminders", "yaad dilao", "reminder laga", "set reminder",
                "reminder lagao", "रिमाइंडर", "याद", "reminder dikhao"
            ),
            VoiceIntentType.NOTIFICATION_HISTORY to listOf(
                "notification history", "notifications", "purane reminder", "reminder history",
                "सूचनाएं", "नोटिफिकेशन", "purane notification"
            ),
            VoiceIntentType.JOURNAL to listOf(
                "journal", "diary", "note", "mood", "journal likho", "diary likho",
                "जर्नल", "डायरी", "नोट", "diary dikhao", "journal dikhao"
            ),
            VoiceIntentType.CONTRACTION_TIMER to listOf(
                "contraction", "contraction timer", "labour timer", "contraction timer kholo",
                "संकुचन", "contractions", "timer chalao"
            ),
            VoiceIntentType.BABY_PROGRESS to listOf(
                "baby", "baby progress", "baby growth", "baccha", "baby size", "shishu",
                "बच्चा", "शिशु", "baby ki growth", "baby progress dikhao", "baby screen",
                "baby progress batao", "baby progress bato"
            ),
            VoiceIntentType.BABY_WEIGHT to listOf(
                "baby weight", "baby ka vajan", "baby weight batao", "weight of baby",
                "वजन", "बच्चे का वजन", "baby weight bato", "baby ka weight"
            ),
            VoiceIntentType.BABY_SIZE to listOf(
                "baby size", "baby kitna bada hai", "baby size batao", "size of baby",
                "साइज", "बच्चे का साइज", "baby size bato"
            ),
            VoiceIntentType.BABY_LENGTH to listOf(
                "baby length", "baby lambai", "baby height", "baby kitna lamba hai",
                "lambai", "length", "height", "बच्चे की लंबाई", "लंबाई", "baby length batao"
            ),
            VoiceIntentType.PREGNANCY_WEEK to listOf(
                "week", "current week", "kaun sa week", "kon sa week", "week progress",
                "week batao", "pregnancy week", "सप्ताह", "कौन सा हफ्ता", "week bato"
            ),
            VoiceIntentType.DELIVERY_DATE to listOf(
                "delivery date", "due date", "delivery kab hogi", "delivery date batao",
                "बच्चा कब होगा", "डिलीवरी डेट", "delivery date bato"
            ),
            VoiceIntentType.TODAY_SCHEDULE to listOf(
                "schedule", "today's schedule", "todays schedule", "aj ka schedule",
                "aaj ka schedule", "schedule batao", "schedule bato", "दिनचर्या", "शेड्यूल"
            ),
            VoiceIntentType.HELP_SUPPORT to listOf(
                "help", "support", "help and support", "madad", "सहायता", "मदद"
            ),
            VoiceIntentType.KICK_COUNT to listOf(
                "kick", "log kick", "felt a kick", "kick record karo", "bacche ne laat mari",
                "kik", "kicks", "किक", "बच्चे की हलचल", "किक रिकॉर्ड करो"
            ),
            VoiceIntentType.WATER_INTAKE to listOf(
                "water", "log water", "drank water", "pani piya", "ek glass pani",
                "pani add karo", "पानी", "एक गिलास पानी", "पानी रिकॉर्ड करो"
            ),
            VoiceIntentType.EMERGENCY to listOf(
                "emergency", "sos", "call ambulance", "ambulance ko phone karo",
                "102", "इमरजेंसी", "एम्बुलेंस", "मदद चाहिए"
            ),
            VoiceIntentType.MOTIVATION to listOf(
                "motivation", "quote", "daily quote", "vichar", "suvichar", "motivate me",
                "आज का विचार", "प्रेरणा", "quote sunao"
            ),
            VoiceIntentType.HOME to listOf(
                "home", "dashboard", "main screen", "ghar", "home kholo", "होम", "डैशबोर्ड"
            ),
            VoiceIntentType.HEALTH to listOf(
                "health", "my health", "health screen", "health dikhao", "health tab",
                "mera health", "health batao", "sehat", "सेहत", "स्वास्थ्य", "health par jao"
            ),
            VoiceIntentType.QUICK_ACTIONS to listOf(
                "quick", "quick actions", "quick action", "actions", "shortcuts", "quick menu",
                "क्विक", "tools"
            )
        )
    }
}
