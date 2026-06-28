package com.adarsh.hellomom.presentation.appointment

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.voice.rememberVoicePrefillStore
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
    var deletingAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var detailedAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<AppointmentEntity?>(null) }

    val voicePrefill = rememberVoicePrefillStore()
    LaunchedEffect(Unit) {
        if (voicePrefill.consumeAutoOpenAdd(VoiceIntentType.APPOINTMENT)) showAddDialog = true
    }
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val listToExport = if (pendingDownload != null) listOf(pendingDownload!!) else state.filteredAppointments
            val content = listToExport.map { 
                PdfExporter.PdfRow(
                    date = sdf.format(Date(it.appointmentTime)),
                    description = "Dr. ${it.doctorName}",
                    details = it.notes ?: ""
                )
            }
            PdfExporter.exportModernToPdf(
                context = context,
                uri = it,
                title = if (pendingDownload != null) "Appointment Details" else "Doctor Appointments Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content,
                userHospital = state.userHospitalName
            )
        }
        pendingDownload = null
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

    detailedAppointment?.let { appointment ->
        AlertDialog(
            onDismissRequest = { detailedAppointment = null },
            title = { Text("Dr. ${appointment.doctorName}") },
            text = {
                Column {
                    Text("Hospital: ${appointment.hospitalName}")
                    Text("Time: ${sdf.format(Date(appointment.appointmentTime))}")
                    if (!appointment.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes: ${appointment.notes}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedAppointment = null }) { Text("Close") }
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
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Appointments_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
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
                            onOpen = { detailedAppointment = appointment },
                            onShare = { shareAppointment(context, appointment, sdf) },
                            onDownload = {
                                pendingDownload = appointment
                                pdfLauncher.launch("Appointment_${appointment.doctorName.replace(" ", "_")}.pdf")
                            },
                            onEdit = { editingAppointment = appointment },
                            onDelete = { deletingAppointment = appointment }
                        )
                    }
                    
                    item { AppFooter() }
                }
            }
        }
    }

    deletingAppointment?.let { appointment ->
        AlertDialog(
            onDismissRequest = { deletingAppointment = null },
            title = { Text("Delete Appointment") },
            text = { Text("Are you sure you want to delete the appointment with Dr. ${appointment.doctorName}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(AppointmentIntent.OnDeleteAppointment(appointment))
                    deletingAppointment = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingAppointment = null }) { Text("Cancel") }
            }
        )
    }
}

private fun shareAppointment(context: android.content.Context, appointment: AppointmentEntity, sdf: SimpleDateFormat) {
    val text = "Appointment with Dr. ${appointment.doctorName}\nHospital: ${appointment.hospitalName}\nTime: ${sdf.format(Date(appointment.appointmentTime))}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Appointment Details")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Appointment"))
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
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(appointment.appointmentTime))
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Dr. ${appointment.doctorName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text = appointment.hospitalName, style = MaterialTheme.typography.bodyMedium)
                if (!appointment.notes.isNullOrBlank()) {
                    Text(
                        text = appointment.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = dateStr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = { menuExpanded = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = { menuExpanded = false; onDownload() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
