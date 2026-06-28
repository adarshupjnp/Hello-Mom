package com.adarsh.hellomom.presentation.billing

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.PdfExporter
import com.adarsh.hellomom.core.voice.VoiceIntentType
import com.adarsh.hellomom.presentation.voice.rememberVoicePrefillStore
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
    var detailedBill by remember { mutableStateOf<BillingEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<BillingEntity?>(null) }

    val voicePrefill = rememberVoicePrefillStore()
    LaunchedEffect(Unit) {
        if (voicePrefill.consumeAutoOpenAdd(VoiceIntentType.BILLING)) showAddDialog = true
    }
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val listToExport = if (pendingDownload != null) listOf(pendingDownload!!) else state.filteredBills
            val content = listToExport.map {
                PdfExporter.BillingPdfRow(
                    date = sdf.format(Date(it.date)),
                    description = it.title,
                    category = it.category,
                    amount = it.amount
                )
            }
            PdfExporter.exportBillingToPdf(
                context = context,
                uri = it,
                userName = state.userName,
                week = state.pregnancyWeek,
                totalAmount = if (pendingDownload != null) pendingDownload!!.amount else state.totalExpense,
                rows = content,
                userHospital = state.userHospitalName,
                userDoctor = state.userDoctorName
            )
        }
        pendingDownload = null
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

    detailedBill?.let { bill ->
        AlertDialog(
            onDismissRequest = { detailedBill = null },
            title = { Text(bill.title) },
            text = {
                Column {
                    Text("Amount: ₹${bill.amount}")
                    Text("Category: ${bill.category}")
                    Text("Date: ${sdf.format(Date(bill.date))}")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedBill = null }) { Text("Close") }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Billing_Report_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            ) 
        },
        floatingActionButton = {
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
                            onOpen = { detailedBill = bill },
                            onShare = { shareBill(context, bill, sdf) },
                            onDownload = {
                                pendingDownload = bill
                                pdfLauncher.launch("Expense_${bill.title.replace(" ", "_")}.pdf")
                            },
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

private fun shareBill(context: android.content.Context, bill: BillingEntity, sdf: SimpleDateFormat) {
    val text = "Expense: ${bill.title}\nAmount: ₹${bill.amount}\nCategory: ${bill.category}\nDate: ${sdf.format(Date(bill.date))}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Expense Details")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Expense"))
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
    
    val predefinedCategories = listOf("Medicine", "Doctor Fees", "Lab Tests", "Ultrasound Scan", "Baby Shopping", "Supplements", "Hospital Bills", "Other")
    
    // If editing and category isn't in predefined, it was a "Custom" one
    val initialIsOther = existing != null && existing.category !in predefinedCategories.filter { it != "Other" }
    var category by remember { mutableStateOf(if (initialIsOther) "Other" else existing?.category ?: "Medicine") }
    var customCategory by remember { mutableStateOf(if (initialIsOther) existing?.category ?: "Other" else "Other") }

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
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedCategories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { 
                                category = c
                                if (c == "Other" && customCategory.isBlank()) {
                                    customCategory = "Other"
                                }
                            },
                            label = { Text(c) }
                        )
                    }
                }

                if (category == "Other") {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = { Text("Enter Custom Category") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customCategory.isBlank(),
                        supportingText = if (customCategory.isBlank()) {
                            { Text("Custom category cannot be empty") }
                        } else null,
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            val finalCategory = if (category == "Other") customCategory.trim() else category
            val isFormValid = title.isNotBlank() && 
                             amount.toDoubleOrNull() != null && 
                             (category != "Other" || customCategory.isNotBlank())

            Button(
                onClick = { 
                    onSave(title, amount.toDoubleOrNull() ?: 0.0, finalCategory, datePickerState.selectedDateMillis ?: System.currentTimeMillis()) 
                },
                enabled = isFormValid
            ) { Text("Save") }
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
fun BillItem(
    bill: BillingEntity, 
    isOwner: Boolean, 
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onEdit: () -> Unit, 
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                Text(text = bill.title, fontWeight = FontWeight.Bold)
                Text(text = bill.category, style = MaterialTheme.typography.bodySmall)
                Text(text = sdf.format(Date(bill.date)), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "₹${bill.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
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
