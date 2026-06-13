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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterRow(
    selectedDate: Long?,
    onDateSelected: (Long?) -> Unit
) {
    val datePickerState = rememberDatePickerState()
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
