package com.adarsh.hellomom.presentation.medicine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val content = state.filteredMedicines.map {
                PdfExporter.PdfRow(
                    date = "Daily",
                    description = it.name,
                    details = "${it.dosage} (${it.timing})"
                )
            }
            PdfExporter.exportToPdf(
                context = context,
                uri = it,
                title = "Medicine History Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
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
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Medicine_History_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
            // Read-only family members cannot add medicines.
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
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The "taken" checkbox is a write action, so it is disabled for family members.
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
                if (medicine.daysOfWeek.isNotBlank()) {
                    val days = medicine.daysOfWeek.split(",")
                    Text(
                        text = if (days.size == 7) "Every day" else days.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
