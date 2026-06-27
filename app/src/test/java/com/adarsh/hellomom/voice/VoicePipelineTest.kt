package com.adarsh.hellomom.voice

import com.adarsh.hellomom.core.voice.ActionDetector
import com.adarsh.hellomom.core.voice.EntityExtractor
import com.adarsh.hellomom.core.voice.IntentDetector
import com.adarsh.hellomom.core.voice.TextNormalizer
import com.adarsh.hellomom.core.voice.VoiceActionType
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.core.voice.VoiceSlot
import com.adarsh.hellomom.core.voice.VoiceSlotRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pure-JVM tests for the offline voice pipeline — no Android, no network. */
class VoicePipelineTest {

    private val normalizer = TextNormalizer()
    private val intents = IntentDetector()
    private val actions = ActionDetector()
    private val entities = EntityExtractor()

    private fun norm(s: String) = normalizer.normalize(s)

    @Test fun normalize_lowercases_and_strips_punctuation() {
        assertEquals("doctor se appointment book karni hai", norm("Doctor Se Appointment Book Karni Hai!"))
    }

    @Test fun intent_english_hindi_hinglish() {
        assertEquals(VoiceIntentType.APPOINTMENT, intents.detect(norm("doctor appointment book karni hai")).intent)
        assertEquals(VoiceIntentType.REPORTS, intents.detect(norm("meri report dikhao")).intent)
        assertEquals(VoiceIntentType.MEDICINE, intents.detect(norm("calcium dawa reminder lagao")).intent)
        assertEquals(VoiceIntentType.REPORTS, intents.detect(norm("मेरी रिपोर्ट")).intent)
        assertTrue(intents.detect(norm("doctor appointment")).confidence >= 0.45f)
    }

    @Test fun reported_commands_resolve_to_their_feature() {
        // Regression for the "always samajh nahi aaya" report: these clean commands score 1.0 at the
        // matcher — the failure was never here, it was the recognizer never delivering the transcript.
        assertEquals(VoiceIntentType.REPORTS, intents.detect(norm("report dikhao")).intent)
        assertEquals(VoiceIntentType.BILLING, intents.detect(norm("mujhe bills batao")).intent)
        // With the default now Hindi (hi-IN), a verb may come back in Devanagari ("दिखाओ"); the new
        // normalizer mappings collapse it onto the same latin token so the phrase still matches.
        assertTrue(norm("रिपोर्ट दिखाओ").contains("dikha"))
        assertEquals(VoiceIntentType.REPORTS, intents.detect(norm("रिपोर्ट दिखाओ")).intent)
    }

    @Test fun action_detection() {
        assertEquals(VoiceActionType.CREATE, actions.detect(norm("30 june appointment add karo")))
        assertEquals(VoiceActionType.SEARCH, actions.detect(norm("blood report dikhao")))
        assertEquals(VoiceActionType.OPEN, actions.detect(norm("profile kholo")))
        assertEquals(VoiceActionType.DELETE, actions.detect(norm("reminder delete karo")))
    }

    @Test fun time_parsing_english_and_hinglish() {
        assertEquals(8 * 60, entities.parseTime(norm("subah 8 baje"))?.first)
        assertEquals(22 * 60, entities.parseTime(norm("raat 10 baje"))?.first)
        assertEquals(20 * 60, entities.parseTime(norm("8 pm"))?.first)
        assertEquals(9 * 60, entities.parseTime(norm("9 am"))?.first)
    }

    @Test fun date_parsing_is_dynamic_year() {
        val today = LocalDate.now()
        val tomorrowMillis = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(tomorrowMillis, entities.parseDate(norm("tomorrow"))?.first)
        assertEquals(tomorrowMillis, entities.parseDate(norm("kal"))?.first)

        // "30 june" resolves within the current or next year — never a hard-coded year.
        val d = entities.parseDate(norm("meri 30 june ki appointment"))
        assertTrue(d != null)
    }

    @Test fun medicine_create_requires_name_and_time() {
        assertEquals(
            listOf(VoiceSlot.MEDICINE_NAME, VoiceSlot.TIME),
            VoiceSlotRules.requiredSlots(VoiceIntentType.MEDICINE, VoiceActionType.CREATE)
        )
        // OPEN never needs slots.
        assertTrue(VoiceSlotRules.requiredSlots(VoiceIntentType.MEDICINE, VoiceActionType.OPEN).isEmpty())
    }
}
