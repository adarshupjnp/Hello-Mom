package com.adarsh.hellomom.presentation.medicine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.presentation.components.LoadingButton
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMedicineScreen(
    navController: NavController,
    viewModel: AddMedicineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val currentTime = java.util.Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(java.util.Calendar.MINUTE),
        is24Hour = false
    )
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedTime = remember(timePickerState.hour, timePickerState.minute) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
        cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(cal.time)
    }

    LaunchedEffect(selectedTime) {
        viewModel.sendIntent(AddMedicineIntent.OnTimingChanged(selectedTime))
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

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendIntent(AddMedicineIntent.OnScanPrescription(uri))
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AddMedicineEffect.NavigateBack -> {
                    navController.popBackStack()
                }
                is AddMedicineEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Add Medicine") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = { photoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Medicine Picture (Auto-fill)")
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.sendIntent(AddMedicineIntent.OnNameChanged(it)) },
                label = { Text("Medicine Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dosage — pick a predefined amount, or choose "Other" to type a custom one.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Dosage",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddMedicineViewModel.DOSAGE_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = !state.isCustomDosage && state.dosage == option,
                            onClick = { viewModel.sendIntent(AddMedicineIntent.OnDosageOptionSelected(option)) },
                            label = { Text(option) }
                        )
                    }
                    FilterChip(
                        selected = state.isCustomDosage,
                        onClick = { viewModel.sendIntent(AddMedicineIntent.OnCustomDosageSelected) },
                        label = { Text("Other") }
                    )
                }
                if (state.isCustomDosage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.dosage,
                        onValueChange = { viewModel.sendIntent(AddMedicineIntent.OnDosageChanged(it)) },
                        label = { Text("Enter dosage (e.g. 500mg, 1 tablet)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // When to take it, relative to meals.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "When to take",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddMedicineViewModel.MEAL_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = state.beforeAfterMeal == option,
                            onClick = { viewModel.sendIntent(AddMedicineIntent.OnBeforeAfterMealChanged(option)) },
                            label = { Text(option) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.timing,
                onValueChange = {},
                label = { Text("Timing") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Weekday selection — at least one day is mandatory before saving.
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Days of the week",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val allSelected = state.selectedDays.containsAll(AddMedicineViewModel.WEEK_DAYS)
                    TextButton(onClick = { viewModel.sendIntent(AddMedicineIntent.OnToggleAllDays) }) {
                        Text(if (allSelected) "Clear all" else "All days")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddMedicineViewModel.WEEK_DAYS.forEach { day ->
                        FilterChip(
                            selected = day in state.selectedDays,
                            onClick = { viewModel.sendIntent(AddMedicineIntent.OnToggleDay(day)) },
                            label = { Text(day) }
                        )
                    }
                }
                Text(
                    text = if (state.selectedDays.isEmpty())
                        "Select at least one day"
                    else
                        "${state.selectedDays.size} day(s) selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.selectedDays.isEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.sendIntent(AddMedicineIntent.OnNotesChanged(it)) },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(32.dp))

            LoadingButton(
                text = "Save Medicine",
                isLoading = state.isLoading,
                onClick = { viewModel.sendIntent(AddMedicineIntent.OnSaveClicked) }
            )
        }
    }
}
