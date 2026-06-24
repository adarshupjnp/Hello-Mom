package com.adarsh.hellomom.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the [VoiceActionType] from a normalized command. Precedence: DELETE > UPDATE > CREATE >
 * SEARCH > OPEN (default). English + Hindi + Hinglish verbs. Offline and deterministic.
 */
@Singleton
class ActionDetector @Inject constructor() {

    fun detect(normalized: String): VoiceActionType {
        val text = " $normalized "
        return when {
            DELETE.any { text.contains(" $it ") } -> VoiceActionType.DELETE
            UPDATE.any { text.contains(" $it ") } -> VoiceActionType.UPDATE
            CREATE.any { text.contains(" $it ") } -> VoiceActionType.CREATE
            SEARCH.any { text.contains(" $it ") } -> VoiceActionType.SEARCH
            OPEN.any { text.contains(" $it ") } -> VoiceActionType.OPEN
            else -> VoiceActionType.OPEN
        }
    }

    companion object {
        private val CREATE = listOf(
            "add", "create", "book", "set", "new", "schedule",
            "laga", "lagao", "lagani", "bana", "banao", "banani", "jodo", "add karo", "naya",
            "जोड़ो", "लगाओ", "बनाओ", "सेट"
        )
        private val DELETE = listOf(
            "delete", "remove", "cancel",
            "hatao", "hata", "delete karo", "remove karo",
            "हटाओ", "हटा", "रद्द"
        )
        private val UPDATE = listOf(
            "update", "change", "edit", "reschedule", "modify",
            "badlo", "badal", "change karo",
            "बदलो", "बदल", "अपडेट"
        )
        private val SEARCH = listOf(
            "search", "find", "show", "dikha", "dhundo", "dhund", "khojo", "khoj",
            "दिखा", "दिखाओ", "खोजो", "ढूंढो"
        )
        private val OPEN = listOf(
            "open", "kholo", "khol", "jao", "go", "go to", "dekho", "dekh",
            "खोलो", "खोल", "जाओ", "देखो"
        )
    }
}
