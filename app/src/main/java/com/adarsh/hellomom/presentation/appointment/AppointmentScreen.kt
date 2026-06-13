package com.adarsh.hellomom.presentation.appointment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.components.ListShimmer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentScreen(
    navController: NavController,
    viewModel: AppointmentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val content = state.filteredAppointments.map { 
                PdfExporter.PdfRow(
                    date = sdf.format(Date(it.appointmentTime)),
                    description = "Dr. ${it.doctorName}",
                    details = it.hospitalName
                )
            }
            PdfExporter.exportToPdf(
                context = context,
                uri = it,
                title = "Doctor Appointments Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
    }

    if (showAddDialog || editingAppointment != null) {
        AddAppointmentDialog(
            appointment = editingAppointment,
            onDismiss = { 
                showAddDialog = false
                editingAppointment = null
            },
            onSave = { dr, hosp, time, notes ->
                if (editingAppointment != null) {
                    viewModel.sendIntent(AppointmentIntent.OnUpdateAppointment(
                        editingAppointment!!.copy(
                            doctorName = dr,
                            hospitalName = hosp,
                            appointmentTime = time,
                            notes = notes
                        )
                    ))
                } else {
                    viewModel.sendIntent(AppointmentIntent.OnAddAppointment(dr, hosp, time, "", notes))
                }
                showAddDialog = false
                editingAppointment = null
            }
        )
    }
 
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Doctor Appointments") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Appointments_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
            // Read-only family members cannot add appointments.
            if (state.isOwner) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            DateFilterRow(
                selectedDate = state.selectedDate,
                onDateSelected = { viewModel.sendIntent(AppointmentIntent.OnDateFilterChanged(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                ListShimmer(modifier = Modifier.weight(1f))
            } else if (state.filteredAppointments.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No appointments found.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredAppointments) { appointment ->
                        AppointmentItem(
                            appointment = appointment,
                            canEdit = state.isOwner,
                            onEdit = { editingAppointment = appointment },
                            onDelete = { viewModel.sendIntent(AppointmentIntent.OnDeleteAppointment(appointment)) }
                        )
                    }
                    
                    item { AppFooter() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppointmentDialog(
    appointment: AppointmentEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String) -> Unit
) {
    var dr by remember { mutableStateOf(appointment?.doctorName ?: "") }
    var hosp by remember { mutableStateOf(appointment?.hospitalName ?: "") }
    var notes by remember { mutableStateOf(appointment?.notes ?: "") }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = appointment?.appointmentTime ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    val initialTime = Calendar.getInstance().apply {
        appointment?.let { timeInMillis = it.appointmentTime }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialTime.get(Calendar.MINUTE)
    )
    var showTimePicker by remember { mutableStateOf(false) }
    
    val selectedDateText = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""
    }

    val selectedTimeText = remember(timePickerState.hour, timePickerState.minute) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
        cal.set(Calendar.MINUTE, timePickerState.minute)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (appointment != null) "Edit Appointment" else "Add Appointment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = dr, onValueChange = { dr = it }, label = { Text("Doctor Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hosp, onValueChange = { hosp = it }, label = { Text("Hospital Name") }, modifier = Modifier.fillMaxWidth())
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selectedDateText,
                        onValueChange = {},
                        label = { Text("Date") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = selectedTimeText,
                        onValueChange = {},
                        label = { Text("Time") },
                        modifier = Modifier
                            .weight(0.8f)
                            .clickable { showTimePicker = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val finalCal = Calendar.getInstance()
                    datePickerState.selectedDateMillis?.let { finalCal.timeInMillis = it }
                    finalCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    finalCal.set(Calendar.MINUTE, timePickerState.minute)
                    finalCal.set(Calendar.SECOND, 0)
                    
                    onSave(dr, hosp, finalCal.timeInMillis, notes) 
                },
                enabled = dr.isNotBlank() && hosp.isNotBlank() && datePickerState.selectedDateMillis != null
            ) { 
                Text("Save") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AppointmentItem(
    appointment: AppointmentEntity,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(appointment.appointmentTime))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Dr. ${appointment.doctorName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = appointment.hospitalName, style = MaterialTheme.typography.bodyMedium)
                }
                // Edit / delete controls are hidden for read-only family members.
                if (canEdit) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = dateStr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            if (!appointment.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = appointment.notes!!, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
