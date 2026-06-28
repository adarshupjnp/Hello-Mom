package com.adarsh.hellomom.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.data.local.entity.ReportEntity
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.components.ListShimmer
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf<ReportEntity?>(null) }
    var editingReport by remember { mutableStateOf<ReportEntity?>(null) }
    var deletingReport by remember { mutableStateOf<ReportEntity?>(null) }
    val sdf = remember { java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val content = state.filteredReports.map { 
                PdfExporter.PdfRow(
                    date = sdf.format(Date(it.date)),
                    description = it.title,
                    details = it.category
                )
            }
            PdfExporter.exportModernToPdf(
                context = context,
                uri = it,
                title = "Medical Reports Summary",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
    }

    if (selectedReport != null) {
        ReportViewerDialog(report = selectedReport!!, onDismiss = { selectedReport = null })
    }

    editingReport?.let { report ->
        EditReportDialog(
            report = report,
            onDismiss = { editingReport = null },
            onSave = { updated ->
                viewModel.sendIntent(ReportsIntent.OnUpdateReport(updated))
                editingReport = null
            }
        )
    }

    deletingReport?.let { report ->
        AlertDialog(
            onDismissRequest = { deletingReport = null },
            title = { Text("Delete Report") },
            text = { Text("Delete \"${report.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(ReportsIntent.OnDeleteReport(report))
                    deletingReport = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingReport = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Medical Reports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val date = java.text.SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Medical_Reports_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
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
                onDateSelected = { viewModel.sendIntent(ReportsIntent.OnDateFilterChanged(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                ListShimmer(modifier = Modifier.weight(1f))
            } else if (state.filteredReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No reports found.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredReports) { report ->
                        ReportItem(
                            report = report,
                            canEdit = state.isOwner,
                            onClick = { selectedReport = report },
                            onEdit = { editingReport = report },
                            onDelete = { deletingReport = report }
                        )
                    }
                    
                    item { AppFooter() }
                }
            }
        }
    }
}

@Composable
fun ReportItem(report: ReportEntity, canEdit: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                Text(text = report.title, fontWeight = FontWeight.Bold)
                Text(text = report.category, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Uploaded on: ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(report.date))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Edit / delete controls are hidden for read-only family members.
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun EditReportDialog(
    report: ReportEntity,
    onDismiss: () -> Unit,
    onSave: (ReportEntity) -> Unit
) {
    var title by remember { mutableStateOf(report.title) }
    var category by remember { mutableStateOf(report.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Report Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Sonography, Blood Test)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(report.copy(title = title.trim(), category = category.trim())) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportViewerDialog(report: ReportEntity, onDismiss: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(report.title) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val path = report.localPath ?: ""
                    if (path.contains("content") || path.contains("image") || path.endsWith(".jpg") || path.endsWith(".png")) {
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FilePresent, modifier = Modifier.size(64.dp), contentDescription = null)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Viewer for this file type is not available in-app.")
                            Text("Path: $path", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
