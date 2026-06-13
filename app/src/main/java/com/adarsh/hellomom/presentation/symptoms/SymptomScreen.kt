package com.adarsh.hellomom.presentation.symptoms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
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
import com.adarsh.hellomom.data.local.entity.SymptomLogEntity
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(
    navController: NavController,
    viewModel: SymptomViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SymptomEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                SymptomEffect.OnSymptomAdded -> showAddDialog = false
            }
        }
    }

    if (showAddDialog) {
        AddSymptomDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, sev -> viewModel.sendIntent(SymptomIntent.OnAddSymptom(name, sev)) },
            isLoading = state.isLoading
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Symptom Analyzer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Read-only family members cannot log symptoms.
            if (state.isOwner) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Symptom")
                }
            }
        }
    ) { paddingValues ->
        if (state.logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No symptoms logged yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.logs) { log ->
                    SymptomItem(log = log)
                }
            }
        }
    }
}

@Composable
fun SymptomItem(log: SymptomLogEntity) {
    val riskColor = when (log.riskLevel.lowercase()) {
        "emergency" -> Color.Red
        "high" -> Color.Magenta
        "medium" -> Color(0xFFFFA500)
        else -> Color.Green
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = log.symptomName, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Badge(containerColor = riskColor) { Text(log.riskLevel, color = Color.White) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Severity: ${log.severity}/10", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Icon(Icons.Default.Warning, contentDescription = null, tint = riskColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = log.recommendation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AddSymptomDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    isLoading: Boolean
) {
    var name by remember { mutableStateOf("") }
    var severity by remember { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Symptom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("What are you feeling?") }, modifier = Modifier.fillMaxWidth())
                Text("Severity: ${severity.toInt()}")
                Slider(value = severity, onValueChange = { severity = it }, valueRange = 1f..10f, steps = 8)
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("AI is analyzing...", modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, severity.toInt()) }, enabled = !isLoading && name.isNotBlank()) { Text("Analyze & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}
