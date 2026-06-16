package com.adarsh.hellomom.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adarsh.hellomom.presentation.components.ShimmerPlaceholder
import com.adarsh.hellomom.ui.theme.cardBG
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DoneGreen = Color(0xFF2E7D32)
private val PendingAmber = Color(0xFFB26A00)

/**
 * Today's Schedule — a daily, time-ordered checklist (wake-up → medicines → meals → sleep) shown
 * inside the Your Health tab. Marks reset each day and sync via Firestore + Room; family members
 * see the owner's marks but can't change them (the toggle is inert unless [TodayScheduleState.isOwner]).
 */
@Composable
fun TodayScheduleSection(
    modifier: Modifier = Modifier,
    viewModel: TodayScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRoutineDialog by remember { mutableStateOf(false) }

    if (showRoutineDialog && state.isOwner) {
        RoutineTimesDialog(
            initialWake = state.wakeUpTime,
            initialSleep = state.sleepTime,
            onDismiss = { showRoutineDialog = false },
            onSave = { wake, sleep ->
                viewModel.sendIntent(TodayScheduleIntent.UpdateRoutineTimes(wake, sleep))
                showRoutineDialog = false
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: title + today's progress + (owner) edit routine times.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's Schedule",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = formatToday(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.isLoading && state.totalCount > 0) {
                    Text(
                        text = "${state.doneCount}/${state.totalCount} done",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.isOwner) {
                    IconButton(onClick = { showRoutineDialog = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit wake-up and sleep times",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    repeat(4) {
                        ShimmerPlaceholder(height = 56.dp, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                }
                else -> {
                    state.items.forEachIndexed { index, item ->
                        ScheduleRow(
                            item = item,
                            canMark = state.isOwner,
                            onToggle = { viewModel.sendIntent(TodayScheduleIntent.ToggleDone(item)) }
                        )
                        if (index < state.items.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                    if (!state.isOwner) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Only the mom can mark items as done.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduleItem,
    canMark: Boolean,
    onToggle: () -> Unit
) {
    val accent = accentFor(item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type icon in a tinted circle.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(item), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(isDone = item.isDone)
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (item.time.isNotBlank()) {
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Owner toggles; family sees a static (non-clickable) status icon.
        val checkIcon = if (item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
        val checkTint = if (item.isDone) DoneGreen else MaterialTheme.colorScheme.outline
        if (canMark) {
            IconButton(onClick = onToggle) {
                Icon(checkIcon, contentDescription = if (item.isDone) "Mark as pending" else "Mark as done", tint = checkTint)
            }
        } else {
            Icon(
                checkIcon,
                contentDescription = if (item.isDone) "Done" else "Pending",
                tint = checkTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(isDone: Boolean) {
    val color = if (isDone) DoneGreen else PendingAmber
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isDone) "Done" else "Pending",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTimesDialog(
    initialWake: String,
    initialSleep: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var wake by remember { mutableStateOf(initialWake) }
    var sleep by remember { mutableStateOf(initialSleep) }
    var picking by remember { mutableStateOf<String?>(null) } // "wake" | "sleep" | null

    if (picking != null) {
        TimePickerDialog(
            initial = if (picking == "wake") wake else sleep,
            onConfirm = { formatted ->
                if (picking == "wake") wake = formatted else sleep = formatted
                picking = null
            },
            onDismiss = { picking = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Routine") },
        text = {
            Column {
                Text(
                    text = "Set the wake-up and sleep times shown on Today's Schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TimeField(label = "Wake up", value = wake, onClick = { picking = "wake" })
                Spacer(Modifier.height(10.dp))
                TimeField(label = "Sleep", value = sleep, onClick = { picking = "sleep" })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(wake, sleep) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TimeField(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = remember(initial) { parseToCalendar(initial) }
    val state = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, state.hour)
                c.set(Calendar.MINUTE, state.minute)
                onConfirm(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.time))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

/* ---- small helpers ---- */

private fun iconFor(item: ScheduleItem): ImageVector = when (item.type) {
    ScheduleItemType.MEDICINE -> Icons.Default.Medication
    ScheduleItemType.MEAL -> Icons.Default.Restaurant
    ScheduleItemType.ROUTINE -> if (item.refId == "sleep") Icons.Default.Bedtime else Icons.Default.WbSunny
}

private fun accentFor(item: ScheduleItem): Color = when (item.type) {
    ScheduleItemType.MEDICINE -> Color(0xFF5C6BC0)
    ScheduleItemType.MEAL -> Color(0xFF66BB6A)
    ScheduleItemType.ROUTINE -> if (item.refId == "sleep") Color(0xFF9575CD) else Color(0xFFFFB300)
}

private fun formatToday(): String =
    SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(Date())

private fun parseToCalendar(time: String): Calendar {
    val cal = Calendar.getInstance()
    for (pattern in listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(time.trim())
        }.getOrNull()
        if (parsed != null) {
            cal.time = parsed
            return cal
        }
    }
    return cal
}
