package com.adarsh.hellomom.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adarsh.hellomom.data.local.entity.PregnancyTip
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.ListShimmer
import com.adarsh.hellomom.ui.theme.cardBG
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendIntent(HomeIntent.OnProfilePictureChanged(uri.toString()))
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HomeEffect.NavigateToLogin -> {
                    // Logout: clear the entire authenticated stack so back from Login exits the app.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.user?.fullName ?: "Mom",
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TitleAnimation"
                    ) { name ->
                        Text("Hello $name")
                    }
                },
                actions = {
                    Box(modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") }
                    ) {
                        if (state.user?.profilePicture != null) {
                            AsyncImage(
                                model = state.user?.profilePicture,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Chat.route) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Face, contentDescription = "AI Assistant")
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            ListShimmer(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StaggeredAnimatedItem(index = 0) {
                        WelcomeCard(userName = state.user?.fullName ?: "Mom")
                    }
                }

                item {
                    StaggeredAnimatedItem(index = 1) {
                        PregnancyProgressCard(week = state.pregnancyWeek, tip = state.pregnancyTip)
                    }
                }

                item {
                    StaggeredAnimatedItem(index = 2) {
                        QuickActionsRow(
                            onMedicineClick = { navController.navigate(Screen.Medicine.route) },
                            onFoodClick = { navController.navigate(Screen.Food.route) },
                            onAppointmentClick = { navController.navigate(Screen.Appointment.route) },
                            onReportsClick = { navController.navigate(Screen.Reports.route) },
                            onBillingClick = { navController.navigate(Screen.Billing.route) },
                            onFamilyClick = { navController.navigate(Screen.Family.route) },
                            onSymptomClick = { navController.navigate(Screen.Symptom.route) }
                        )
                    }
                }

                item {
                    StaggeredAnimatedItem(index = 3) {
                        EmergencyHelpSection(
                            onCallAmbulance = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                                intent.data = android.net.Uri.parse("tel:102")
                                context.startActivity(intent)
                            },
                            onCallPolice = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                                intent.data = android.net.Uri.parse("tel:100")
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaggeredAnimatedItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(500, delayMillis = index * 150)) + 
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500, delayMillis = index * 150)
                ),
        label = "StaggeredAnimation"
    ) {
        content()
    }
}


@Composable
fun WelcomeCard(userName: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Hello, $userName!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "How are you feeling today?")
        }
    }
}

@Composable
fun PregnancyProgressCard(week: Int, tip: PregnancyTip?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Pregnancy Progress", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Week $week", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { week / 40f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            
            if (tip != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Baby is size of a ${tip.babySize}", fontWeight = FontWeight.Bold)
                Text(text = tip.babyInfo, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Tip: ${tip.healthTip}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun EmergencyHelpSection(onCallAmbulance: () -> Unit, onCallPolice: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Emergency Help", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCallAmbulance,
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ambulance", maxLines = 1, fontSize = 14.sp)
                }
                Button(
                    onClick = onCallPolice,
                    modifier = Modifier.weight(0.7f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Police", maxLines = 1, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onMedicineClick: () -> Unit,
    onFoodClick: () -> Unit,
    onAppointmentClick: () -> Unit,
    onReportsClick: () -> Unit,
    onBillingClick: () -> Unit,
    onFamilyClick: () -> Unit,
    onSymptomClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(title = "Medicines", icon = Icons.Default.AddCircle, modifier = Modifier.weight(1f), onActionClick = onMedicineClick)
            ActionCard(title = "Food", icon = Icons.Default.ThumbUp, modifier = Modifier.weight(1f), onActionClick = onFoodClick)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(title = "Appointments", icon = Icons.Default.DateRange, modifier = Modifier.weight(1f), onActionClick = onAppointmentClick)
            ActionCard(title = "Reports", icon = Icons.Default.Edit, modifier = Modifier.weight(1f), onActionClick = onReportsClick)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(title = "Bills", icon = Icons.Default.ShoppingCart, modifier = Modifier.weight(1f), onActionClick = onBillingClick)
            ActionCard(title = "Family", icon = Icons.Default.AccountBox, modifier = Modifier.weight(1f), onActionClick = onFamilyClick)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(title = "AI Symptom", icon = Icons.Default.Warning, modifier = Modifier.weight(1f), onActionClick = onSymptomClick)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ActionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onActionClick: () -> Unit) {
    Card(
        modifier = modifier.height(100.dp).clickable { onActionClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
