package com.adarsh.hellomom.presentation.billing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.data.local.entity.BillingEntity
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.components.ListShimmer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillingScreen(
    navController: NavController,
    viewModel: BillingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<BillingEntity?>(null) }
    var deletingBill by remember { mutableStateOf<BillingEntity?>(null) }
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val content = state.filteredBills.map {
                PdfExporter.PdfRow(
                    date = sdf.format(Date(it.date)),
                    description = it.title,
                    details = "₹${it.amount} (${it.category})"
                )
            }
            PdfExporter.exportToPdf(
                context = context,
                uri = it,
                title = "Billing & Expenses Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
    }

    if (showAddDialog) {
        AddBillDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, amount, category, date ->
                viewModel.sendIntent(BillingIntent.OnAddBill(title, amount, category, date))
                showAddDialog = false
            }
        )
    }

    editingBill?.let { bill ->
        AddBillDialog(
            existing = bill,
            onDismiss = { editingBill = null },
            onSave = { title, amount, category, date ->
                viewModel.sendIntent(
                    BillingIntent.OnUpdateBill(
                        bill.copy(title = title, amount = amount, category = category, date = date)
                    )
                )
                editingBill = null
            }
        )
    }

    deletingBill?.let { bill ->
        AlertDialog(
            onDismissRequest = { deletingBill = null },
            title = { Text("Delete Expense") },
            text = { Text("Delete \"${bill.title}\" (₹${bill.amount})?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(BillingIntent.OnDeleteBill(bill))
                    deletingBill = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingBill = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Billing & Expenses") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Billing_Report_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
            // Read-only family members cannot add expenses.
            if (state.isOwner) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Bill")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            DateFilterRow(
                selectedDate = state.selectedDate,
                onDateSelected = { viewModel.sendIntent(BillingIntent.OnDateFilterChanged(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TotalExpenseCard(total = state.totalExpense)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Recent Expenses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (state.isLoading) {
                // Family members load the owner's bills over the network — shimmer until they arrive.
                ListShimmer(modifier = Modifier.weight(1f))
            } else if (state.filteredBills.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No expenses recorded.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredBills) { bill ->
                        BillItem(
                            bill = bill,
                            isOwner = state.isOwner,
                            onEdit = { editingBill = bill },
                            onDelete = { deletingBill = bill }
                        )
                    }
                    
                    item { AppFooter() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBillDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String, Long) -> Unit,
    existing: BillingEntity? = null
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Medicine") }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = existing?.date ?: System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }
    
    val selectedDateText = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Expense" else "Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount, 
                    onValueChange = { amount = it }, 
                    label = { Text("Amount") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                
                OutlinedTextField(
                    value = selectedDateText,
                    onValueChange = {},
                    label = { Text("Date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Text(text = "Category", style = MaterialTheme.typography.labelLarge)
                val categories = listOf("Medicine", "Doctor Fees", "Lab Tests", "Hospital Bills", "Other")
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onSave(title, amount.toDoubleOrNull() ?: 0.0, category, datePickerState.selectedDateMillis ?: System.currentTimeMillis()) 
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TotalExpenseCard(total: Double) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Total Monthly Expense", style = MaterialTheme.typography.titleMedium)
            Text(text = "₹$total", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}



@Composable
fun BillItem(bill: BillingEntity, isOwner: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                Text(text = bill.title, fontWeight = FontWeight.Bold)
                Text(text = bill.category, style = MaterialTheme.typography.bodySmall)
                Text(text = sdf.format(Date(bill.date)), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "₹${bill.amount}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = if (isOwner) Modifier else Modifier.padding(end = 16.dp)
            )
            // Edit / delete controls are hidden for read-only family members.
            if (isOwner) {
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


