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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.data.local.entity.JournalEntity
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.voice.rememberVoicePrefillStore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: NavController,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntity?>(null) }
    var deletingEntry by remember { mutableStateOf<JournalEntity?>(null) }
    var detailedEntry by remember { mutableStateOf<JournalEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<JournalEntity?>(null) }

    val voicePrefill = rememberVoicePrefillStore()
    LaunchedEffect(Unit) {
        if (voicePrefill.consumeAutoOpenAdd(VoiceIntentType.JOURNAL)) showDialog = true
    }
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val listToExport = if (pendingDownload != null) listOf(pendingDownload!!) else state.filtered
            val content = listToExport.map { entry ->
                PdfExporter.PdfRow(
                    date = sdf.format(Date(entry.date)),
                    description = if (entry.mood.isNotBlank()) entry.mood else "Note",
                    details = entry.content
                )
            }
            PdfExporter.exportModernToPdf(
                context = context,
                uri = it,
                title = if (pendingDownload != null) "Journal Entry" else "Pregnancy Journal Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
        pendingDownload = null
    }

    if (showDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Daily Note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("How are you feeling?") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendIntent(JournalIntent.OnAdd(text))
                        showDialog = false
                    },
                    enabled = text.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingEntry?.let { entry ->
        var text by remember(entry.entryId) { mutableStateOf(entry.content) }
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Edit Note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("How are you feeling?") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendIntent(JournalIntent.OnUpdate(entry.copy(content = text)))
                        editingEntry = null
                    },
                    enabled = text.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Cancel") }
            }
        )
    }

    detailedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { detailedEntry = null },
            title = { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entry.date))) },
            text = {
                Column {
                    Text(entry.content)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedEntry = null }) { Text("Close") }
            }
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this journal entry?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(JournalIntent.OnDelete(entry))
                    deletingEntry = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pregnancy Journal") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Journal_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isOwner) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
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
            Spacer(modifier = Modifier.height(16.dp))

            DateFilterRow(
                selectedDate = state.selectedDate,
                onDateSelected = { viewModel.sendIntent(JournalIntent.OnDateFilterChanged(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No journal entries found for this date.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filtered, key = { it.entryId }) { entry ->
                        JournalItem(
                            entry = entry,
                            isOwner = state.isOwner,
                            onOpen = { detailedEntry = entry },
                            onShare = { shareJournal(context, entry) },
                            onDownload = {
                                pendingDownload = entry
                                val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date(entry.date))
                                pdfLauncher.launch("Journal_Entry_$date.pdf")
                            },
                            onEdit = { editingEntry = entry },
                            onDelete = { deletingEntry = entry }
                        )
                    }
                }
            }
        }
    }
}

private fun shareJournal(context: android.content.Context, entry: JournalEntity) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val text = "Journal entry from ${sdf.format(Date(entry.date))}:\n\n${entry.content}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Pregnancy Journal Entry")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Journal Entry"))
}

@Composable
fun JournalItem(
    entry: JournalEntity,
    isOwner: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(text = sdf.format(Date(entry.date)), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = entry.content, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
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
