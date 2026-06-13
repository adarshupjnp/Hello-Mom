package com.adarsh.hellomom.presentation.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.presentation.components.DateFilterRow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    navController: NavController,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val familyReminders by viewModel.displayedReminders.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    
    var selectedReminderForTime by remember { mutableStateOf<ReminderEntity?>(null) }
    val timePickerState = rememberTimePickerState()
    var showTimePicker by remember { mutableStateOf(false) }

    var showSnoozeOptions by remember { mutableStateOf<ReminderEntity?>(null) }

    if (showSnoozeOptions != null) {
        val snoozeTimes = listOf(5, 10, 15, 30, 60)
        AlertDialog(
            onDismissRequest = { showSnoozeOptions = null },
            title = { Text("Select Snooze Duration") },
            text = {
                Column {
                    snoozeTimes.forEach { mins ->
                        TextButton(
                            onClick = {
                                viewModel.snooze(showSnoozeOptions!!.id, mins)
                                showSnoozeOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins Minutes")
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { showSnoozeOptions = null }) { Text("Cancel") }
            }
        )
    }

    if (showTimePicker && selectedReminderForTime != null) {
        AlertDialog(
            onDismissRequest = { 
                showTimePicker = false 
                selectedReminderForTime = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    viewModel.updateReminderTime(selectedReminderForTime!!, cal.timeInMillis)
                    showTimePicker = false
                    selectedReminderForTime = null
                }) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showTimePicker = false 
                    selectedReminderForTime = null
                }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Reminder Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("notification_history") }) {
                        Icon(Icons.Default.Notifications, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isOwner) {
                FloatingActionButton(onClick = { navController.navigate("add_reminder") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            DateFilterRow(
                selectedDate = selectedDate,
                onDateSelected = { viewModel.setDateFilter(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (familyReminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No reminders found for this date.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Show Pending first
                items(familyReminders.filter { it.status == ReminderStatus.PENDING || it.status == ReminderStatus.SNOOZED }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        isOwner = isOwner,
                        onDone = { viewModel.markAsDone(reminder.id) },
                        onSnooze = { showSnoozeOptions = reminder },
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onEditTime = {
                            selectedReminderForTime = reminder
                            showTimePicker = true
                        }
                    )
                }
                
                // Show Completed and Expired at bottom
                items(familyReminders.filter { it.status == ReminderStatus.COMPLETED || it.status == ReminderStatus.EXPIRED }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        isOwner = isOwner,
                        onDone = { },
                        onSnooze = { },
                        onDelete = { viewModel.deleteReminder(reminder) },
                        onEditTime = { }
                    )
                }
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    isOwner: Boolean,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    onEditTime: () -> Unit
) {
    val statusColor = when (reminder.status) {
        ReminderStatus.COMPLETED -> Color(0xFF4CAF50)
        ReminderStatus.SNOOZED -> Color(0xFFFFC107)
        ReminderStatus.EXPIRED -> Color(0xFFF44336)
        ReminderStatus.PENDING -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = reminder.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Created by: ${reminder.userName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = reminder.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Alarm Style Time Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (isOwner && (reminder.status == ReminderStatus.PENDING || reminder.status == ReminderStatus.SNOOZED)) onEditTime() }
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val sdf = SimpleDateFormat("hh:mm", Locale.getDefault())
                val amPmSdf = SimpleDateFormat("a", Locale.getDefault())
                val time = Date(reminder.time)
                
                Text(
                    text = sdf.format(time),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = amPmSdf.format(time),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = reminder.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwner) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
                
                if (reminder.status == ReminderStatus.PENDING || reminder.status == ReminderStatus.SNOOZED) {
                    if (isOwner) {
                        // Snooze / Mark Done mutate the reminder, so they are owner-only.
                        Row {
                            TextButton(onClick = onSnooze) {
                                Text("Snooze")
                            }
                            Button(
                                onClick = onDone,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Mark Done")
                            }
                        }
                    } else {
                        // Family members can view reminders but cannot change them.
                        Text(
                            text = "View only",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (reminder.status == ReminderStatus.COMPLETED) {
                    Text(
                        text = "Acknowledge ✅",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
