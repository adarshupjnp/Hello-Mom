package com.adarsh.hellomom.core.voice

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts dates, times, doctor/medicine names, frequency and free-text queries from a normalized
 * command. English + Hindi + Hinglish. The year is always derived from the current date — never
 * hard-coded. Exposes slot-scoped parsers so the slot-filling dialogue can read one field at a time.
 */
@Singleton
class EntityExtractor @Inject constructor() {

    data class Entities(
        val dateMillis: Long? = null,
        val dateLabel: String? = null,
        val timeMinutes: Int? = null,
        val timeLabel: String? = null,
        val doctorName: String? = null,
        val medicineName: String? = null,
        val frequency: String? = null,
        val query: String? = null
    )

    fun extractAll(normalized: String): Entities {
        val date = parseDate(normalized)
        val time = parseTime(normalized)
        return Entities(
            dateMillis = date?.first,
            dateLabel = date?.second,
            timeMinutes = time?.first,
            timeLabel = time?.second,
            doctorName = parseDoctor(normalized),
            medicineName = parseMedicine(normalized),
            frequency = parseFrequency(normalized),
            query = parseQuery(normalized)
        )
    }

    // ---- Slot-scoped parsers (used mid-dialogue so "next friday" is read as a date, not a command) ----

    /** @return midnight-millis to human label, or null. */
    fun parseDate(normalized: String): Pair<Long, String>? {
        val today = LocalDate.now()
        val resolved: LocalDate? = when {
            contains(normalized, "today", "aaj", "आज") -> today
            contains(normalized, "day after tomorrow", "parso", "parson", "परसों") -> today.plusDays(2)
            contains(normalized, "tomorrow", "kal", "कल") -> today.plusDays(1)
            else -> parseWeekday(normalized, today) ?: parseDayMonth(normalized, today)
        }
        return resolved?.let { it.toMillis() to it.format(DATE_LABEL) }
    }

    /** @return minutes-since-midnight to human label, or null. */
    fun parseTime(normalized: String): Pair<Int, String>? {
        // Period words shift a bare hour into AM/PM.
        val period = when {
            contains(normalized, "subah", "morning", "सुबह") -> Period.AM
            contains(normalized, "dopahar", "noon", "afternoon", "दोपहर") -> Period.NOON
            contains(normalized, "sham", "shaam", "evening", "शाम") -> Period.PM
            contains(normalized, "raat", "night", "रात") -> Period.NIGHT
            else -> Period.NONE
        }

        // English "8 am" / "8 pm" / "8:30 pm"
        AMPM.find(normalized)?.let { m ->
            var hour = m.groupValues[1].toInt() % 12
            val minute = m.groupValues[2].ifBlank { "0" }.toInt()
            if (m.groupValues[3].startsWith("p")) hour += 12
            return timeOf(hour, minute)
        }
        // "8 baje" / "8:30 baje" / a bare hour with a period word
        (BAJE.find(normalized) ?: BARE_HOUR.find(normalized))?.let { m ->
            val rawHour = m.groupValues[1].toInt()
            val minute = m.groupValues.getOrNull(2)?.ifBlank { "0" }?.toInt() ?: 0
            val hour = applyPeriod(rawHour, period)
            if (hour in 0..23) return timeOf(hour, minute)
        }
        return null
    }

    /** Generic name parse for a slot (medicine / reminder title / doctor). Strips filler words. */
    fun parseName(normalized: String): String? {
        val cleaned = normalized.split(' ')
            .filter { it.isNotBlank() && it !in NAME_STOPWORDS }
            .joinToString(" ")
            .trim()
        return cleaned.ifBlank { null }?.let { titleCase(it) }
    }

    // ---- field parsers used by extractAll ----

    private fun parseDoctor(normalized: String): String? {
        val tokens = normalized.split(' ')
        val idx = tokens.indexOfFirst { it == "dr" || it == "doctor" || it == "डॉ" || it == "डॉक्टर" }
        if (idx == -1 || idx + 1 >= tokens.size) return null
        val name = tokens.drop(idx + 1)
            .takeWhile { it !in DOCTOR_STOPWORDS && it.isNotBlank() }
            .take(2)
            .joinToString(" ")
        return name.ifBlank { null }?.let { titleCase(it) }
    }

    private fun parseMedicine(normalized: String): String? {
        KNOWN_MEDICINES.firstOrNull { normalized.contains(it) }?.let { return titleCase(it) }
        val tokens = normalized.split(' ')
        val anchor = tokens.indexOfFirst { it == "medicine" || it == "dawa" || it == "tablet" || it == "pill" || it == "दवा" || it == "गोली" }
        if (anchor == -1) return null
        // Name often precedes the anchor ("calcium dawa") or follows ("dawa calcium").
        val before = tokens.getOrNull(anchor - 1)?.takeIf { it.isNotBlank() && it !in NAME_STOPWORDS }
        val after = tokens.getOrNull(anchor + 1)?.takeIf { it.isNotBlank() && it !in NAME_STOPWORDS && it !in ACTION_WORDS }
        return (before ?: after)?.let { titleCase(it) }
    }

    private fun parseFrequency(normalized: String): String? =
        if (contains(normalized, "roz", "daily", "everyday", "har din", "रोज", "रोज़", "हर दिन")) "Daily" else null

    private fun parseQuery(normalized: String): String? {
        val q = normalized.split(' ')
            .filter { it.isNotBlank() && it !in QUERY_STOPWORDS }
            .joinToString(" ")
            .trim()
        return q.ifBlank { null }
    }

    // ---- helpers ----

    private enum class Period { NONE, AM, NOON, PM, NIGHT }

    private fun applyPeriod(hour: Int, period: Period): Int = when (period) {
        Period.AM -> if (hour == 12) 0 else hour
        Period.NOON -> if (hour < 12) hour + 12 else hour
        Period.PM -> if (hour in 1..11) hour + 12 else hour
        Period.NIGHT -> if (hour in 1..11) hour + 12 else hour
        Period.NONE -> hour
    }

    private fun timeOf(hour: Int, minute: Int): Pair<Int, String> {
        val safeHour = hour.coerceIn(0, 23)
        val safeMin = minute.coerceIn(0, 59)
        val label = LocalTime.of(safeHour, safeMin).format(TIME_LABEL)
        return (safeHour * 60 + safeMin) to label
    }

    private fun parseWeekday(normalized: String, today: LocalDate): LocalDate? {
        val entry = WEEKDAYS.entries.firstOrNull { (kw, _) -> normalized.contains(kw) } ?: return null
        val target = entry.value
        var days = (target.value - today.dayOfWeek.value + 7) % 7
        val forceNext = contains(normalized, "next", "agle", "अगले", "अगला")
        if (days == 0 && forceNext) days = 7
        return today.plusDays(days.toLong())
    }

    private fun parseDayMonth(normalized: String, today: LocalDate): LocalDate? {
        val tokens = normalized.split(' ')
        var day: Int? = null
        var month: Int? = null
        for ((i, t) in tokens.withIndex()) {
            val asDay = t.toIntOrNull()
            if (asDay != null && asDay in 1..31) {
                day = asDay
                // month may be the neighbouring token
                month = month ?: MONTHS[tokens.getOrNull(i + 1)] ?: MONTHS[tokens.getOrNull(i - 1)]
            }
            MONTHS[t]?.let { month = it }
        }
        if (day == null || month == null) return null
        var date = runCatching { LocalDate.of(today.year, month!!, day!!) }.getOrNull() ?: return null
        // If the date already passed this year, assume next year (dynamic — never hard-coded).
        if (date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun contains(text: String, vararg keys: String): Boolean = keys.any { text.contains(it) }

    private fun titleCase(s: String): String =
        s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }

    companion object {
        private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)
        private val TIME_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

        private val AMPM = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)")
        private val BAJE = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*baje")
        private val BARE_HOUR = Regex("(?:^|\\s)(\\d{1,2})(?::(\\d{2}))?(?:\\s|$)")

        private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
            "monday" to DayOfWeek.MONDAY, "somvar" to DayOfWeek.MONDAY, "सोमवार" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY, "mangalvar" to DayOfWeek.TUESDAY, "मंगलवार" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "budhvar" to DayOfWeek.WEDNESDAY, "बुधवार" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "guruvar" to DayOfWeek.THURSDAY, "गुरुवार" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "shukravar" to DayOfWeek.FRIDAY, "शुक्रवार" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY, "shanivar" to DayOfWeek.SATURDAY, "शनिवार" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY, "ravivar" to DayOfWeek.SUNDAY, "रविवार" to DayOfWeek.SUNDAY
        )

        private val MONTHS: Map<String, Int> = mapOf(
            "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
            "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10,
            "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12
        )

        private val KNOWN_MEDICINES = listOf(
            "calcium", "iron", "folic acid", "folic", "vitamin", "vitamin d", "b12", "zinc",
            "paracetamol", "iron tablet", "prenatal"
        )

        private val ACTION_WORDS = setOf(
            "add", "set", "laga", "lagao", "book", "create", "reminder", "karo", "kar"
        )

        private val DOCTOR_STOPWORDS = setOf(
            "ke", "se", "ko", "saath", "with", "for", "at", "on", "appointment", "ki", "wali", "wala"
        )

        private val NAME_STOPWORDS = setOf(
            "add", "set", "laga", "lagao", "book", "create", "karo", "kar", "ke", "ki", "ka", "ko",
            "se", "liye", "for", "a", "an", "the", "reminder", "medicine", "dawa", "appointment",
            "naya", "nayi", "new", "please", "plz"
        )

        private val ACTION_AND_INTENT_KEYWORDS = setOf(
            "open", "kholo", "khol", "dikha", "dikhao", "show", "search", "find", "dhundo", "add",
            "set", "book", "create", "laga", "lagao", "delete", "remove", "hatao", "update", "change",
            "karo", "kar", "ke", "ki", "ka", "ko", "se", "mera", "meri", "my", "the", "a", "an", "jao",
            "go", "to", "kholo"
        )
        val QUERY_STOPWORDS = ACTION_AND_INTENT_KEYWORDS
    }
}
