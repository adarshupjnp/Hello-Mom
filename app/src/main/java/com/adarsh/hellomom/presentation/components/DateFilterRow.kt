package com.adarsh.hellomom.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Quick history filter offering Today / Yesterday / Custom date / All.
 *
 * Kept the original `(selectedDate, onDateSelected)` signature so every existing screen picks up
 * the richer UI with no other changes. `selectedDate` is the representative millis of the day to
 * show (any time within the day works — callers match on yyyy-MM-dd); `null` means "All".
 *
 * [maxPastDays], when set, limits the Custom date picker to the last N days (today + the previous
 * N-1) and blocks future dates. Left `null` by default so every other screen keeps an unrestricted
 * picker — only callers with a bounded data window (e.g. the 7-day reminder history) pass a value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterRow(
    selectedDate: Long?,
    onDateSelected: (Long?) -> Unit,
    maxPastDays: Int? = null
) {
    val selectableDates = remember(maxPastDays) {
        if (maxPastDays != null) LastNDaysSelectableDates(maxPastDays) else AllDatesSelectable
    }
    val datePickerState = rememberDatePickerState(selectableDates = selectableDates)
    var showDatePicker by remember { mutableStateOf(false) }

    val todayMillis = remember { startOfDay(System.currentTimeMillis()) }
    val yesterdayMillis = remember { startOfDay(System.currentTimeMillis() - DAY_MILLIS) }

    val selectedDay = selectedDate?.let { startOfDay(it) }
    val isToday = selectedDay == todayMillis
    val isYesterday = selectedDay == yesterdayMillis
    val isCustom = selectedDate != null && !isToday && !isYesterday

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Filter") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedDate == null,
            onClick = { onDateSelected(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = isToday,
            onClick = { onDateSelected(todayMillis) },
            label = { Text("Today") }
        )
        FilterChip(
            selected = isYesterday,
            onClick = { onDateSelected(yesterdayMillis) },
            label = { Text("Yesterday") }
        )
        FilterChip(
            selected = isCustom,
            onClick = { showDatePicker = true },
            label = {
                Text(
                    if (isCustom) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDate!!))
                    else "Custom"
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Pick a date",
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun startOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

// The Material3 DatePicker evaluates dates in UTC, so the selectable-window math is done in UTC too.
private fun startOfDayUtc(millis: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun yearOfUtc(millis: Long): Int =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }.get(Calendar.YEAR)

/** Default behaviour: every date is selectable (matches the picker's stock behaviour). */
@OptIn(ExperimentalMaterial3Api::class)
private val AllDatesSelectable = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
    override fun isSelectableYear(year: Int): Boolean = true
}

/** Allows only the last [daysInclusive] days (today + the previous N-1); blocks older and future dates. */
@OptIn(ExperimentalMaterial3Api::class)
private class LastNDaysSelectableDates(daysInclusive: Int) : SelectableDates {
    private val todayUtc = startOfDayUtc(System.currentTimeMillis())
    private val earliestUtc = todayUtc - (daysInclusive - 1).coerceAtLeast(0) * DAY_MILLIS

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis in earliestUtc..todayUtc

    override fun isSelectableYear(year: Int): Boolean =
        year in yearOfUtc(earliestUtc)..yearOfUtc(todayUtc)
}
