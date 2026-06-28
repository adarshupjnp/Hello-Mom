package com.adarsh.hellomom.presentation.dashboard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.data.local.entity.ContractionEntity
import com.adarsh.hellomom.presentation.components.DateFilterRow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractionTimerScreen(
    navController: NavController,
    viewModel: ContractionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var isRunning by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var timerText by remember { mutableStateOf("00:00") }
    var editingRecord by remember { mutableStateOf<ContractionEntity?>(null) }
    var deletingRecord by remember { mutableStateOf<ContractionEntity?>(null) }
    var detailedRecord by remember { mutableStateOf<ContractionEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<ContractionEntity?>(null) }

    val context = LocalContext.current
    val sdfFull = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val listToExport = if (pendingDownload != null) listOf(pendingDownload!!) else state.filtered
            val content = listToExport.map { 
                PdfExporter.PdfRow(
                    date = sdfFull.format(Date(it.startTime)),
                    description = "Contraction",
                    details = "Duration: ${it.durationMillis / 1000}s"
                )
            }
            PdfExporter.exportModernToPdf(
                context = context,
                uri = it,
                title = if (pendingDownload != null) "Contraction Record" else "Contraction History Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
        pendingDownload = null
    }

    val voicePrefill = com.adarsh.hellomom.presentation.voice.rememberVoicePrefillStore()
    LaunchedEffect(Unit) {
        if (voicePrefill.consumeAutoOpenAdd(com.adarsh.hellomom.core.voice.VoiceIntentType.CONTRACTION_TIMER)) {
            if (state.isOwner) isRunning = true
        }
    }

    editingRecord?.let { record ->
        EditContractionDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { updated ->
                viewModel.sendIntent(ContractionIntent.OnUpdate(updated))
                editingRecord = null
            }
        )
    }

    detailedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { detailedRecord = null },
            title = { Text("Contraction Details") },
            text = {
                Column {
                    Text("Start Time: ${sdfFull.format(Date(record.startTime))}")
                    Text("Duration: ${record.durationMillis / 1000} seconds")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedRecord = null }) { Text("Close") }
            }
        )
    }

    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("Delete Contraction") },
            text = { Text("Are you sure you want to delete this contraction record?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(ContractionIntent.OnDelete(record))
                    deletingRecord = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            startTime = System.currentTimeMillis()
            while (isRunning) {
                val elapsed = System.currentTimeMillis() - startTime
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / (1000 * 60)) % 60
                timerText = String.format("%02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contraction Timer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Contraction_History_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = timerText, fontSize = 64.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (state.isOwner) {
                        Button(
                            onClick = {
                                if (isRunning) {
                                    val duration = System.currentTimeMillis() - startTime
                                    viewModel.sendIntent(ContractionIntent.OnRecord(startTime, duration))
                                    isRunning = false
                                    timerText = "00:00"
                                } else {
                                    isRunning = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRunning) "STOP" else "START")
                        }
                    } else {
                        Text(
                            text = "View only",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            DateFilterRow(
                selectedDate = state.selectedDate,
                onDateSelected = { viewModel.sendIntent(ContractionIntent.OnDateFilterChanged(it)) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.filtered, key = { it.contractionId }) { record ->
                    ContractionItem(
                        record = record,
                        isOwner = state.isOwner,
                        onOpen = { detailedRecord = record },
                        onShare = { shareContraction(navController.context, record) },
                        onDownload = {
                            pendingDownload = record
                            val date = SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date(record.startTime))
                            pdfLauncher.launch("Contraction_$date.pdf")
                        },
                        onEdit = { editingRecord = record },
                        onDelete = { deletingRecord = record }
                    )
                }
            }
        }
    }
}

private fun shareContraction(context: android.content.Context, record: ContractionEntity) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
    val text = "Contraction started at: ${sdf.format(Date(record.startTime))}\nDuration: ${record.durationMillis / 1000} seconds"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Contraction Data")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Contraction"))
}

@Composable
fun ContractionItem(
    record: ContractionEntity,
    isOwner: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                Text(text = sdf.format(Date(record.startTime)), fontWeight = FontWeight.Bold)
                Text(text = "Duration: ${record.durationMillis / 1000}s", style = MaterialTheme.typography.bodySmall)
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
                    if (isOwner) {
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

@Composable
fun EditContractionDialog(
    record: ContractionEntity,
    onDismiss: () -> Unit,
    onSave: (ContractionEntity) -> Unit
) {
    var durationSeconds by remember { mutableStateOf((record.durationMillis / 1000).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contraction") },
        text = {
            OutlinedTextField(
                value = durationSeconds,
                onValueChange = { durationSeconds = it.filter { c -> c.isDigit() } },
                label = { Text("Duration (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val secs = durationSeconds.toLongOrNull() ?: 0L
                    onSave(record.copy(durationMillis = secs * 1000))
                },
                enabled = durationSeconds.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
