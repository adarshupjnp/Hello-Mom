package com.adarsh.hellomom.presentation.medicine

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.components.ListShimmer
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineScreen(
    navController: NavController,
    viewModel: MedicineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editingMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var deletingMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var detailedMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<MedicineEntity?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val listToExport = if (pendingDownload != null) listOf(pendingDownload!!) else state.filteredMedicines
            val content = listToExport.map {
                PdfExporter.PdfRow(
                    date = if (pendingDownload != null) "Record" else "Daily",
                    description = it.name,
                    details = "${it.dosage} (${it.timing})"
                )
            }
            PdfExporter.exportModernToPdf(
                context = context,
                uri = it,
                title = if (pendingDownload != null) "Medicine Details" else "Medicine History Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
        pendingDownload = null
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MedicineEffect.NavigateToAddMedicine -> {
                    navController.navigate(Screen.AddMedicine.route)
                }
                is MedicineEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { 
            TopAppBar(
                title = { Text("Your Medicines") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Medicine_History_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
            if (state.isOwner) {
                FloatingActionButton(onClick = { viewModel.sendIntent(MedicineIntent.OnAddMedicineClicked) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Medicine")
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
                onDateSelected = { viewModel.sendIntent(MedicineIntent.OnDateFilterChanged(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                ListShimmer(modifier = Modifier.weight(1f))
            } else if (state.filteredMedicines.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No medicines found for this date.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredMedicines) { medicine ->
                        MedicineItem(
                            medicine = medicine,
                            canEdit = state.isOwner,
                            onOpen = { detailedMedicine = medicine },
                            onShare = { shareMedicine(context, medicine) },
                            onDownload = {
                                pendingDownload = medicine
                                pdfLauncher.launch("Medicine_${medicine.name.replace(" ", "_")}.pdf")
                            },
                            onDelete = { deletingMedicine = medicine },
                            onEdit = { editingMedicine = medicine },
                            onToggle = { viewModel.sendIntent(MedicineIntent.OnToggleMedicineStatus(medicine)) }
                        )
                    }

                    item { AppFooter() }
                }
            }
        }
    }

    editingMedicine?.let { medicine ->
        EditMedicineDialog(
            medicine = medicine,
            onDismiss = { editingMedicine = null },
            onSave = { updated ->
                viewModel.sendIntent(MedicineIntent.OnUpdateMedicine(updated))
                editingMedicine = null
            }
        )
    }

    detailedMedicine?.let { medicine ->
        AlertDialog(
            onDismissRequest = { detailedMedicine = null },
            title = { Text(medicine.name) },
            text = {
                Column {
                    Text("Dosage: ${medicine.dosage}")
                    Text("Timing: ${medicine.timing}")
                    Text("Relation to meal: ${medicine.beforeAfterMeal}")
                    if (!medicine.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes: ${medicine.notes}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedMedicine = null }) { Text("Close") }
            }
        )
    }

    deletingMedicine?.let { medicine ->
        AlertDialog(
            onDismissRequest = { deletingMedicine = null },
            title = { Text("Delete Medicine") },
            text = { Text("Are you sure you want to delete \"${medicine.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(MedicineIntent.OnDeleteMedicine(medicine.medicineId))
                    deletingMedicine = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMedicine = null }) { Text("Cancel") }
            }
        )
    }
}

private fun shareMedicine(context: android.content.Context, medicine: MedicineEntity) {
    val text = "Medicine: ${medicine.name}\nDosage: ${medicine.dosage}\nTiming: ${medicine.timing}\n${medicine.beforeAfterMeal}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Medicine Details")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Medicine"))
}

@Composable
fun EditMedicineDialog(
    medicine: MedicineEntity,
    onDismiss: () -> Unit,
    onSave: (MedicineEntity) -> Unit
) {
    var name by remember { mutableStateOf(medicine.name) }
    var dosage by remember { mutableStateOf(medicine.dosage) }
    var timing by remember { mutableStateOf(medicine.timing) }
    var beforeAfter by remember { mutableStateOf(medicine.beforeAfterMeal) }
    var notes by remember { mutableStateOf(medicine.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Medicine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timing,
                    onValueChange = { timing = it },
                    label = { Text("Timing (e.g. 08:00 AM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = beforeAfter,
                    onValueChange = { beforeAfter = it },
                    label = { Text("Before/After Meal") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        medicine.copy(
                            name = name.trim(),
                            dosage = dosage.trim(),
                            timing = timing.trim(),
                            beforeAfterMeal = beforeAfter.trim(),
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MedicineItem(
    medicine: MedicineEntity,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = medicine.isCompleted,
                onCheckedChange = { onToggle() },
                enabled = canEdit
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = medicine.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(text = "${medicine.dosage} - ${medicine.timing}", style = MaterialTheme.typography.bodyMedium)
                Text(text = medicine.beforeAfterMeal, style = MaterialTheme.typography.bodySmall)
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
