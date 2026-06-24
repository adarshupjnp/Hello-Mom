package com.adarsh.hellomom.presentation.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.AppBottomNavBar
import com.adarsh.hellomom.presentation.components.AppTab
import com.adarsh.hellomom.presentation.components.NAV_SELECTED_TAB_KEY
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.ListShimmer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.adarsh.hellomom.data.local.SyncStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendIntent(ProfileIntent.UpdateProfilePicture(uri.toString()))
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileEffect.NavigateToLogin -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                ProfileEffect.SyncSuccess -> {
                    snackbarHostState.showSnackbar("Synced! You're seeing the latest data.")
                }
                is ProfileEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ProfileEffect.ShareAppLink -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Hello Mom+")
                        putExtra(Intent.EXTRA_TEXT, effect.shareMessage)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Hello Mom+"))
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.sendIntent(ProfileIntent.Logout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No, Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    } else {
                        IconButton(onClick = { viewModel.sendIntent(ProfileIntent.SyncData) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Data")
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = null,
                onSelect = { tab ->
                    if (tab == AppTab.BABY) {
                        navController.navigate(Screen.BabyProgress.route)
                    } else {
                        navigateToDashboardTab(navController, tab.ordinal)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                ListShimmer(modifier = Modifier.padding(padding))
            } else {
                state.user?.let { user ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            ProfileHeader(
                                user = user,
                                onImageClick = { imagePickerLauncher.launch("image/*") },
                                onEditClick = { showEditDialog = true }
                            )
                        }

                        item {
                            UserInfoSection(user, state.isOwner, state.pregnancyWeek, state.pregnancyDay)
                        }

                        item {
                            LanguageSettingsSection(
                                currentLanguage = state.currentLanguage,
                                onLanguageChange = { viewModel.sendIntent(ProfileIntent.ChangeLanguage(it)) },
                                voiceReminderEnabled = state.isVoiceReminderEnabled,
                                onVoiceReminderChange = { viewModel.sendIntent(ProfileIntent.ToggleVoiceReminder(it)) }
                            )
                        }

                        item {
                            SettingsSection(
                                navController = navController,
                                onShareApp = { viewModel.sendIntent(ProfileIntent.ShareApp) }
                            )
                        }

                        item { AppFooter() }
                    }
                }
            }

            SyncingOverlay(visible = state.isSyncing)

            if (showEditDialog && state.user != null) {
                EditProfileDialog(
                    user = state.user!!,
                    isOwner = state.isOwner,
                    onDismiss = { showEditDialog = false },
                    onSave = { updated ->
                        viewModel.sendIntent(ProfileIntent.UpdateProfile(updated))
                        showEditDialog = false
                    }
                )
            }
        }
    }
}

private fun navigateToDashboardTab(navController: NavController, tabIndex: Int) {
    runCatching {
        val homeEntry = navController.getBackStackEntry(Screen.Home.route)
        homeEntry.savedStateHandle[NAV_SELECTED_TAB_KEY] = tabIndex
        navController.popBackStack(Screen.Home.route, inclusive = false)
    }.onFailure {
        navController.navigate(Screen.Home.route)
    }
}

@Composable
fun SyncingOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val transition = rememberInfiniteTransition(label = "sync")
                    val angle by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "sync-rotation"
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(40.dp)
                                .rotate(angle)
                        )
                    }
                    Text(
                        text = "Syncing in progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Please wait while we fetch the latest data…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(
    user: UserEntity,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (user.profilePicture != null) {
                    AsyncImage(
                        model = user.profilePicture,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = user.fullName.take(1).uppercase()
                    Text(
                        text = initial,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(CircleShape)
                    .clickable { onEditClick() },
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = user.fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun UserInfoSection(user: UserEntity, isOwner: Boolean, week: Int, day: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            InfoRow(icon = Icons.Default.CalendarToday, label = "Pregnancy Progress", value = "Week $week Day $day")
            
            user.dueDate?.let {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                InfoRow(icon = Icons.Default.Event, label = "Due Date", value = sdf.format(Date(it)))
            }

            if (user.mobileNumber.isNotBlank()) {
                InfoRow(icon = Icons.Default.Phone, label = "Mobile Number", value = user.mobileNumber)
            }
            
            if (isOwner) {
                user.bloodGroup?.let { InfoRow(icon = Icons.Default.Bloodtype, label = "Blood Group", value = it) }
                user.emergencyContact?.let { InfoRow(icon = Icons.Default.Phone, label = "Emergency Contact", value = it) }
                user.doctorName?.let { InfoRow(icon = Icons.Default.MedicalServices, label = "Doctor", value = "Dr. $it") }
                user.hospitalName?.let { InfoRow(icon = Icons.Default.LocalHospital, label = "Hospital", value = it) }
            } else {
                user.emergencyContact?.let { InfoRow(icon = Icons.Default.Phone, label = "Emergency Contact", value = it) }
                user.doctorName?.let { InfoRow(icon = Icons.Default.MedicalServices, label = "Doctor Info", value = "Dr. $it") }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LanguageSettingsSection(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    voiceReminderEnabled: Boolean,
    onVoiceReminderChange: (Boolean) -> Unit
) {
    val languages = listOf("Hindi", "Hinglish", "English", "Gujarati", "Marathi")
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "App Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Language")
                }
                Text(text = currentLanguage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = {
                            onLanguageChange(lang)
                            expanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVoiceReminderChange(!voiceReminderEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Allow Voice Reminder")
                        Text(
                            text = "Speak reminders aloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(
                    checked = voiceReminderEnabled,
                    onCheckedChange = onVoiceReminderChange
                )
            }
        }
    }
}

@Composable
fun SettingsSection(navController: NavController, onShareApp: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsItem(
                icon = Icons.Default.Share,
                title = "Share App",
                onClick = onShareApp
            )
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                onClick = { navController.navigate(Screen.NotificationHistory.route) }
            )
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Privacy",
                onClick = { navController.navigate(Screen.PrivacyPolicy.route) }
            )
            SettingsItem(
                icon = Icons.Default.Info, 
                title = "About Hello Mom",
                onClick = { navController.navigate(Screen.About.route) }
            )
            SettingsItem(
                icon = Icons.Default.Help, 
                title = "Help & Support",
                onClick = { navController.navigate(Screen.HelpSupport.route) }
            )
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    user: UserEntity,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserEntity) -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var mobileNumber by remember { mutableStateOf(user.mobileNumber) }
    var dob by remember { mutableLongStateOf(user.dob) }
    var bloodGroup by remember { mutableStateOf(user.bloodGroup ?: "") }
    var emergencyContact by remember { mutableStateOf(user.emergencyContact ?: "") }
    var doctorName by remember { mutableStateOf(user.doctorName ?: "") }
    var hospitalName by remember { mutableStateOf(user.hospitalName ?: "") }
    var weight by remember { mutableStateOf(user.weight?.toString() ?: "") }
    var height by remember { mutableStateOf(user.height?.toString() ?: "") }
    
    val pregnancyStartDate = user.pregnancyStartDate
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var showDobPicker by remember { mutableStateOf(false) }

    if (showDobPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dob.takeIf { it > 0 } ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDobPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dob = it }
                    showDobPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDobPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = if (dob > 0) sdf.format(Date(dob)) else "",
                    onValueChange = {},
                    label = { Text("Date of Birth") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDobPicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (isOwner && pregnancyStartDate != null) {
                    OutlinedTextField(
                        value = sdf.format(Date(pregnancyStartDate)),
                        onValueChange = {},
                        label = { Text("Pregnancy Start Date (Not Editable)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        supportingText = { Text("This date is used to track your pregnancy progress.") }
                    )
                }

                OutlinedTextField(
                    value = bloodGroup,
                    onValueChange = { bloodGroup = it },
                    label = { Text("Blood Group") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text("Emergency Contact") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = { Text("Doctor Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hospitalName,
                    onValueChange = { hospitalName = it },
                    label = { Text("Hospital Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        user.copy(
                            fullName = fullName,
                            mobileNumber = mobileNumber,
                            dob = dob,
                            bloodGroup = bloodGroup,
                            emergencyContact = emergencyContact,
                            doctorName = doctorName,
                            hospitalName = hospitalName,
                            weight = weight.toFloatOrNull(),
                            height = height.toFloatOrNull(),
                            syncStatus = SyncStatus.PENDING,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                enabled = fullName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
