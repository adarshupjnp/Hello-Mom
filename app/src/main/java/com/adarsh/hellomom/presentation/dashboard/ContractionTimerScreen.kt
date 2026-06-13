package com.adarsh.hellomom.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
                    // Read-only family members can view contractions but not record them.
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
                    val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                                Text(text = sdf.format(Date(record.startTime)), fontWeight = FontWeight.Bold)
                                Text(text = "Duration: ${record.durationMillis / 1000}s", style = MaterialTheme.typography.bodySmall)
                            }
                            // Edit / delete are owner-only.
                            if (state.isOwner) {
                                IconButton(onClick = { editingRecord = record }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.sendIntent(ContractionIntent.OnDelete(record)) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
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
