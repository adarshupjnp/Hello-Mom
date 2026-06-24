package com.adarsh.hellomom.presentation.food

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.DateFilterRow
import com.adarsh.hellomom.presentation.components.ListShimmer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    navController: NavController,
    viewModel: FoodViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddMealDialog by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealEntity?>(null) }
    var deletingMeal by remember { mutableStateOf<MealEntity?>(null) }
    var detailedMeal by remember { mutableStateOf<MealEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<MealEntity?>(null) }

    val voicePrefill = rememberVoicePrefillStore()
    LaunchedEffect(Unit) {
        if (voicePrefill.consumeAutoOpenAdd(VoiceIntentType.FOOD)) showAddMealDialog = true
    }
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val content = mutableListOf<PdfExporter.PdfRow>()
            
            if (pendingDownload == null) {
                // Export full report including water
                content.add(PdfExporter.PdfRow(
                    date = "Today",
                    description = "Water Intake",
                    details = "${state.waterIntake?.glassesDrank ?: 0} Glasses"
                ))

                state.filteredMeals.forEach {
                    content.add(PdfExporter.PdfRow(
                        date = it.timing,
                        description = it.mealType,
                        details = it.foodItems
                    ))
                }
            } else {
                // Export single meal
                content.add(PdfExporter.PdfRow(
                    date = pendingDownload!!.timing,
                    description = pendingDownload!!.mealType,
                    details = pendingDownload!!.foodItems
                ))
            }

            PdfExporter.exportToPdf(
                context = context,
                uri = it,
                title = if (pendingDownload != null) "Meal Details" else "Nutrition & Water Report",
                userName = state.userName,
                week = state.pregnancyWeek,
                content = content
            )
        }
        pendingDownload = null
    }

    if (showAddMealDialog) {
        AddMealDialog(
            onDismiss = { showAddMealDialog = false },
            onSave = { type, items, time, days ->
                viewModel.sendIntent(FoodIntent.OnAddMeal(type, items, time, days))
                showAddMealDialog = false
            }
        )
    }

    editingMeal?.let { meal ->
        EditMealDialog(
            meal = meal,
            onDismiss = { editingMeal = null },
            onSave = { updated ->
                viewModel.sendIntent(FoodIntent.OnUpdateMeal(updated))
                editingMeal = null
            }
        )
    }

    detailedMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { detailedMeal = null },
            title = { Text(meal.mealType) },
            text = {
                Column {
                    Text("Food Items: ${meal.foodItems}")
                    Text("Timing: ${meal.timing}")
                    if (meal.daysOfWeek.isNotBlank()) {
                        Text("Days: ${meal.daysOfWeek}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailedMeal = null }) { Text("Close") }
            }
        )
    }

    deletingMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { deletingMeal = null },
            title = { Text("Delete Meal") },
            text = { Text("Delete \"${meal.mealType}\" (${meal.foodItems})?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendIntent(FoodIntent.OnDeleteMeal(meal))
                    deletingMeal = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMeal = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Nutrition & Water") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pendingDownload = null
                        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                        pdfLauncher.launch("Nutrition_Report_$date.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    if (state.isOwner) {
                        IconButton(onClick = { showAddMealDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Meal")
                        }
                    }
                }
            ) 
        }
    ) { paddingValues ->
        if (state.isLoading) {
            ListShimmer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateFilterRow(
                    selectedDate = state.selectedDate,
                    onDateSelected = { viewModel.sendIntent(FoodIntent.OnDateFilterChanged(it)) }
                )
            }

            item {
                WaterTrackerCard(
                    waterIntake = state.waterIntake,
                    canEdit = state.isOwner,
                    onAdd = { viewModel.sendIntent(FoodIntent.OnAddGlassWater) },
                    onRemove = { viewModel.sendIntent(FoodIntent.OnRemoveGlassWater) }
                )
            }

            item {
                Text(text = "Recommended for Week ${state.pregnancyWeek}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            item {
                RecommendedFoodSection(week = state.pregnancyWeek)
            }

            if (state.aiRecommendation != null) {
                item {
                    AiNutritionCard(recommendation = state.aiRecommendation!!)
                }
            }

            item {
                Text(text = "Meals", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            items(state.filteredMeals) { meal ->
                MealItem(
                    meal = meal,
                    canEdit = state.isOwner,
                    onOpen = { detailedMeal = meal },
                    onShare = { shareMeal(context, meal) },
                    onDownload = {
                        pendingDownload = meal
                        pdfLauncher.launch("Meal_${meal.mealType.replace(" ", "_")}.pdf")
                    },
                    onToggle = { viewModel.sendIntent(FoodIntent.OnMealToggle(meal)) },
                    onEdit = { editingMeal = meal },
                    onDelete = { deletingMeal = meal }
                )
            }

            item { AppFooter() }
        }
    }
}

private fun shareMeal(context: android.content.Context, meal: MealEntity) {
    val text = "Meal: ${meal.mealType}\nItems: ${meal.foodItems}\nTiming: ${meal.timing}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Meal Details")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Meal"))
}

@Composable
fun WaterTrackerCard(
    waterIntake: WaterIntakeEntity?,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val drank = waterIntake?.glassesDrank ?: 0
    val goal = waterIntake?.goalGlasses ?: 8

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Water Intake", fontWeight = FontWeight.Bold)
            Text(text = "$drank / $goal Glasses", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)

            LinearProgressIndicator(
                progress = { drank.toFloat() / goal.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onRemove) { Text("-") }
                    Button(onClick = onAdd) { Text("+ Add Glass") }
                }
            }
        }
    }
}

private data class MealCategory(val emoji: String, val name: String, val items: List<String>)

private fun mealPlanForWeek(week: Int): Pair<String, List<MealCategory>> = when {
    week <= 12 -> "First trimester · focus on folic acid, hydration & easing nausea" to listOf(
        MealCategory("🌅", "Morning Breakfast", listOf(
            "Spinach & cheese omelet (folic acid)",
            "Fortified oats with babyana",
            "Whole-grain toast with peanut butter",
            "Glass of milk or fresh orange juice",
            "Soaked almonds & walnuts",
            "Ginger tea to ease morning sickness"
        )),
        MealCategory("🍲", "Lunch", listOf(
            "Dal (lentils) with brown rice",
            "Mixed vegetable curry with roti",
            "Bowl of curd / yogurt",
            "Paneer or boiled-egg salad",
            "Seasonal fruit (apple, pear)"
        )),
        MealCategory("☕", "Evening Snack", listOf(
            "Coconut water",
            "Roasted chana / makhana",
            "Vegetable sandwich",
            "Handful of dry fruits",
            "Buttermilk (chaas)"
        )),
        MealCategory("🌙", "Dinner", listOf(
            "Light khichdi with a little ghee",
            "Warm vegetable soup",
            "Grilled fish or tofu",
            "Steamed greens",
            "Warm milk with a pinch of turmeric"
        ))
    )
    week <= 24 -> "Second trimester · build calcium, iron & protein" to listOf(
        MealCategory("🌅", "Morning Breakfast", listOf(
            "Vegetable poha with peanuts",
            "Paneer paratha with curd",
            "Ragi porridge (calcium + iron)",
            "Two boiled eggs",
            "Fresh fruit with a glass of milk"
        )),
        MealCategory("🍲", "Lunch", listOf(
            "Rajma or chickpea curry with rice",
            "Palak (spinach) dal for iron",
            "Two multigrain rotis",
            "Curd / raita",
            "Carrot & beetroot salad"
        )),
        MealCategory("☕", "Evening Snack", listOf(
            "Sprouts chaat",
            "Fruit yogurt or smoothie",
            "Roasted nuts & seeds",
            "Whole-grain crackers with hummus",
            "Pomegranate or sugarcane juice"
        )),
        MealCategory("🌙", "Dinner", listOf(
            "Grilled chicken or paneer with veggies",
            "Mixed vegetable pulao",
            "Lentil soup",
            "Sautéed methi / spinach",
            "Glass of milk before bed"
        ))
    )
    else -> "Third trimester · steady energy, fiber & lighter dinners" to listOf(
        MealCategory("🌅", "Morning Breakfast", listOf(
            "Idli / dosa with sambar",
            "Stuffed vegetable paratha (light oil)",
            "Muesli with milk & berries",
            "Babyana with dates",
            "Almond milk or warm milk"
        )),
        MealCategory("🍲", "Lunch", listOf(
            "Rice with dal & a little ghee",
            "Vegetable curry with two rotis",
            "Curd or buttermilk",
            "Green salad with lemon",
            "Fish curry or paneer"
        )),
        MealCategory("☕", "Evening Snack", listOf(
            "Fruit bowl (apple, orange, papaya in moderation)",
            "Vegetable upma",
            "Roasted makhana",
            "Date & nut ladoo",
            "Coconut water"
        )),
        MealCategory("🌙", "Dinner (light & early)", listOf(
            "Soft khichdi or daliya",
            "Clear vegetable soup",
            "Steamed vegetables",
            "Curd rice",
            "Warm turmeric milk"
        ))
    )
}

@Composable
fun RecommendedFoodSection(week: Int) {
    val (subtitle, categories) = remember(week) { mealPlanForWeek(week) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            categories.forEach { category ->
                CollapsibleFoodCategory(category)
            }
        }
    }
}

@Composable
private fun CollapsibleFoodCategory(category: MealCategory) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse ${category.name}" else "Expand ${category.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                category.items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun AiNutritionCard(recommendation: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Nutrition Tip", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = recommendation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MealItem(
    meal: MealEntity,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = meal.isTaken, onCheckedChange = { onToggle() }, enabled = canEdit)
            Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp, horizontal = 8.dp)) {
                Text(text = meal.mealType, fontWeight = FontWeight.Bold)
                Text(text = meal.foodItems, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(text = meal.timing, style = MaterialTheme.typography.bodySmall)
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

@Composable
fun EditMealDialog(
    meal: MealEntity,
    onDismiss: () -> Unit,
    onSave: (MealEntity) -> Unit
) {
    var type by remember { mutableStateOf(meal.mealType) }
    var items by remember { mutableStateOf(meal.foodItems) }
    var timing by remember { mutableStateOf(meal.timing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val types = listOf("Breakfast", "Lunch", "Dinner", "Snack")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text("Food Items") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timing,
                    onValueChange = { timing = it },
                    label = { Text("Timing") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(meal.copy(mealType = type, foodItems = items.trim(), timing = timing.trim())) },
                enabled = items.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var type by remember { mutableStateOf("Breakfast") }
    var items by remember { mutableStateOf("") }
    val weekDays = remember { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }
    var selectedDays by remember { mutableStateOf(emptySet<String>()) }

    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = false
    )
    var showTimePicker by remember { mutableStateOf(false) }
    
    val selectedTime = remember(timePickerState.hour, timePickerState.minute) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
        cal.set(Calendar.MINUTE, timePickerState.minute)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Select Category", style = MaterialTheme.typography.labelLarge)
                val types = listOf("Breakfast", "Lunch", "Dinner", "Snack")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }

                OutlinedTextField(
                    value = items, 
                    onValueChange = { items = it }, 
                    label = { Text("Food Items") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = selectedTime,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Days of the week", style = MaterialTheme.typography.labelLarge)
                    val allSelected = selectedDays.containsAll(weekDays)
                    TextButton(onClick = {
                        selectedDays = if (allSelected) emptySet() else weekDays.toSet()
                    }) {
                        Text(if (allSelected) "Clear all" else "All days")
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weekDays.forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = selectedDays.toMutableSet().apply {
                                    if (!add(day)) remove(day)
                                }
                            },
                            label = { Text(day) }
                        )
                    }
                }
                Text(
                    text = if (selectedDays.isEmpty())
                        "Select at least one day"
                    else
                        "${selectedDays.size} day(s) selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedDays.isEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = weekDays.filter { it in selectedDays }.joinToString(",")
                    onSave(type, items, selectedTime, days)
                },
                enabled = items.isNotBlank() && selectedDays.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
