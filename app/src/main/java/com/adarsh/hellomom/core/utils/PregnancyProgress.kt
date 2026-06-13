package com.adarsh.hellomom.core.utils

/**
 * Single source of truth for pregnancy week / day / trimester math.
 *
 * Every screen MUST use this object instead of re-implementing the diff math, so the whole app
 * agrees on the same numbers (the old per-ViewModel copies disagreed on null defaults and on the
 * 40 vs 42 week cap). All inputs are null-safe and future-date-safe: a null or future start date
 * yields week 1 / day 1 instead of a crash or a negative number.
 *
 * Note: the UI week can reach 42 (post-term), while [PregnancyDataEngine.getWeekData] clamps its
 * content lookup to 1..40 internally — that is intentional.
 */
object PregnancyProgress {

    const val MAX_WEEK = 42
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Whole days elapsed since the pregnancy start (LMP). Never negative; 0 when unknown. */
    fun totalDays(startDate: Long?, now: Long = System.currentTimeMillis()): Int {
        if (startDate == null || startDate <= 0) return 0
        val diff = now - startDate
        if (diff <= 0) return 0
        return (diff / DAY_MILLIS).toInt()
    }

    /** Current pregnancy week, always within 1..[MAX_WEEK]. */
    fun week(startDate: Long?, now: Long = System.currentTimeMillis()): Int =
        ((totalDays(startDate, now) / 7) + 1).coerceIn(1, MAX_WEEK)

    /** Day inside the current week, 1..7. */
    fun dayOfWeek(startDate: Long?, now: Long = System.currentTimeMillis()): Int =
        (totalDays(startDate, now) % 7) + 1

    /** Trimester for a given week: 1 (≤13), 2 (≤27), 3 (28+). */
    fun trimester(week: Int): Int = when {
        week <= 13 -> 1
        week <= 27 -> 2
        else -> 3
    }
}
