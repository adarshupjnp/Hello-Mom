package com.adarsh.hellomom.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.adarsh.hellomom.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adarsh.hellomom.core.utils.PregnancyDataEngine
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.data.local.entity.*
import com.adarsh.hellomom.domain.repository.MotherHealthData
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.DashboardShimmer
import com.adarsh.hellomom.presentation.components.OfflineBanner
import com.adarsh.hellomom.presentation.components.OfflineScreen
import com.adarsh.hellomom.presentation.components.rememberIsOnline
import com.adarsh.hellomom.presentation.components.AppFooter
import com.adarsh.hellomom.presentation.components.AiWebView
import com.adarsh.hellomom.presentation.components.AppBottomNavBar
import com.adarsh.hellomom.presentation.components.AppTab
import com.adarsh.hellomom.presentation.components.NAV_SELECTED_TAB_KEY
import com.adarsh.hellomom.presentation.schedule.TodayScheduleSection
import com.adarsh.hellomom.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * External AI assistants the user can open in the in-app browser.
 *
 * Where the site supports it, the current-week health prompt is passed as a URL query
 * param so the assistant auto-runs it on open. [queryParam] is null for sites that don't
 * support a launch query (the assistant simply opens, and the user can type their own).
 */
private enum class AiProvider(
    val label: String,
    private val baseUrl: String,
    private val queryParam: String?
) {
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search", "q"),
    CHATGPT("ChatGPT", "https://chatgpt.com/", "q"),
    COPILOT("Copilot", "https://copilot.microsoft.com/", "q"),
    GEMINI("Gemini", "https://gemini.google.com/app", null);

    /** URL to open. Includes the auto-search prompt when the provider supports it. */
    fun urlForPrompt(prompt: String): String {
        if (queryParam == null || prompt.isBlank()) return baseUrl
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator$queryParam=$encoded"
    }
}

/**
 * Builds the AI auto-search prompt from the CURRENT pregnancy week progress and the
 * user's selected app [language] — never a static string.
 */
private fun buildAiHealthPrompt(state: DashboardState, language: String): String = buildString {
    append("I am currently in week ${state.pregnancyWeek}, day ${state.pregnancyDay} ")
    append("of pregnancy (trimester ${state.trimester}). ")
    if (state.weekData.babySize.isNotBlank()) {
        append("My baby is about the size of a ${state.weekData.babySize}. ")
    }
    if (state.weekData.weeklyMilestone.isNotBlank()) {
        append("This week's milestone: ${state.weekData.weeklyMilestone}. ")
    }
    append("Give me practical, week-specific health tips for both mother and baby for this exact stage — ")
    append("including diet, hydration, gentle exercise, the common symptoms to expect this week, ")
    append("and warning signs I should watch for. ")
    append("Please respond in $language.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    voiceViewModel: com.adarsh.hellomom.presentation.voice.VoiceAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isOnline by rememberIsOnline()

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = AppTab.entries[selectedIndex]

    // Honor a tab change requested from another screen (e.g., the Profile screen's bottom bar).
    val homeEntry = remember { navController.getBackStackEntry(Screen.Home.route) }
    val requestedTab by homeEntry.savedStateHandle
        .getStateFlow(NAV_SELECTED_TAB_KEY, -1)
        .collectAsState()
    LaunchedEffect(requestedTab) {
        if (requestedTab in AppTab.entries.indices) {
            selectedIndex = requestedTab
            homeEntry.savedStateHandle[NAV_SELECTED_TAB_KEY] = -1
        }
    }

    // AI assistant in-app browser state.
    var aiProvider by remember { mutableStateOf<AiProvider?>(null) }
    var showAiChooser by remember { mutableStateOf(false) }

    // Voice assistant: show the mic only once the dashboard has finished loading (shimmer gone) and
    // no AI web chat is open. The welcome greeting fires when the mic first appears, so it always
    // starts after loading — never over the shimmer.
    LaunchedEffect(aiProvider, state.isLoading) {
        voiceViewModel.sendIntent(com.adarsh.hellomom.presentation.voice.VoiceAssistantIntent.SetMicVisibility(aiProvider == null && !state.isLoading))
    }
    DisposableEffect(Unit) {
        onDispose {
            voiceViewModel.sendIntent(com.adarsh.hellomom.presentation.voice.VoiceAssistantIntent.SetMicVisibility(true))
        }
    }

    // The welcome greeting is triggered by VoiceAssistantOverlay (the Activity-scoped instance that
    // drives the floating mic) so the mic shows "active" while it speaks and listens right after.

    // Read the current app language preference live so the AI prompt always reflects
    // the latest choice the user made in Profile/Settings.
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context.applicationContext) }

    if (showAiChooser) {
        AiProviderChooser(
            onSelect = { provider ->
                showAiChooser = false
                aiProvider = provider
            },
            onDismiss = { showAiChooser = false }
        )
    }

    // Device back behavior on the dashboard:
    //  - if the AI assistant is open, close it first;
    //  - else if we're on a non-Home tab (Quick Actions / Your Health / Profile), go to Home;
    //  - else (already on Home) the handler is disabled, so the system back closes the app.
    // This gives the "back returns to Home, back again exits" flow the user asked for.
    BackHandler(enabled = aiProvider != null || selectedIndex != AppTab.HOME.ordinal) {
        if (aiProvider != null) {
            aiProvider = null
        } else {
            selectedIndex = AppTab.HOME.ordinal
        }
    }

    Scaffold(
        topBar = {
            if (aiProvider != null) {
                // While the AI assistant is open, the top bar reflects it with a close action.
                TopAppBar(
                    title = { Text(aiProvider!!.label, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { aiProvider = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            } else if (selectedTab == AppTab.HOME) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Hello, Mom ❤️",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { navController.navigate(Screen.Profile.route) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.user?.profilePicture != null) {
                                AsyncImage(
                                    model = state.user!!.profilePicture,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initial = state.user?.fullName?.take(1)?.uppercase() ?: "M"
                                Text(
                                    text = initial,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    // Leaving the AI view when a tab is tapped keeps the bottom bar behaving like tabs.
                    aiProvider = null
                    if (tab == AppTab.BABY) {
                        // Baby Progress is its own full screen, like Profile.
                        navController.navigate(Screen.BabyProgress.route)
                    } else {
                        selectedIndex = tab.ordinal
                    }
                }
            )
        },
        floatingActionButton = {
            // "AI" assistant launcher — hidden while the assistant is already open.
            if (aiProvider == null && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { showAiChooser = true },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    text = { Text("AI") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            if (state.isLoading) {
                // While first-load data is still resolving: if we're offline with nothing to show,
                // a friendly offline screen beats an endless shimmer.
                if (!isOnline) {
                    OfflineScreen(
                        onRetry = { viewModel.sendIntent(DashboardIntent.Refresh) },
                        contentPadding = paddingValues
                    )
                } else {
                    DashboardShimmer()
                }
            } else if (aiProvider != null) {
                // AI assistant opens inside the scaffold so the bottom bar stays visible.
                AiWebView(
                    url = aiProvider!!.urlForPrompt(
                        buildAiHealthPrompt(state, preferenceManager.selectedLanguage)
                    ),
                    onClose = { aiProvider = null },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                // Smooth crossfade transition when switching between tabs.
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(300),
                    label = "DashboardTab"
                ) { tab ->
                    when (tab) {
                        AppTab.HOME -> HomeTabContent(
                            state = state,
                            contentPadding = paddingValues,
                            onToggleDone = { type, refId, isDone ->
                                viewModel.sendIntent(DashboardIntent.ToggleUpcomingDone(type, refId, isDone))
                            }
                        )
                        AppTab.ACTIONS -> QuickActionsTabContent(
                            navController = navController,
                            hasFullAccess = state.hasFullAccess,
                            contentPadding = paddingValues
                        )
                        AppTab.HEALTH -> YourHealthTabContent(
                            state = state,
                            contentPadding = paddingValues,
                            onUpdateWater = { viewModel.sendIntent(DashboardIntent.UpdateWater(it)) },
                            onUpdateSleep = { viewModel.sendIntent(DashboardIntent.UpdateSleep(it)) },
                            onUpdateWeight = { viewModel.sendIntent(DashboardIntent.UpdateWeight(it)) },
                            onUpdateSteps = { viewModel.sendIntent(DashboardIntent.UpdateSteps(it)) },
                            onIncrementKicks = { viewModel.sendIntent(DashboardIntent.IncrementKicks) }
                        )
                        // Unreachable: tapping Baby navigates to its own screen instead of
                        // selecting the tab, but the when must stay exhaustive.
                        AppTab.BABY -> HomeTabContent(
                            state = state,
                            contentPadding = paddingValues,
                            onToggleDone = { type, refId, isDone ->
                                viewModel.sendIntent(DashboardIntent.ToggleUpcomingDone(type, refId, isDone))
                            }
                        )
                    }
                }
            }

            // Floating offline banner under the top app bar — appears whenever connectivity drops,
            // so the user always knows when data may be stale (owner and family alike).
            OfflineBanner(
                isOnline = isOnline,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun AiProviderChooser(
    onSelect: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Open AI Assistant") },
        text = {
            Column {
                Text(
                    text = "Choose an assistant to chat with:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AiProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(provider) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ----------------------------------------------------------------------------
 * HOME TAB
 * Week progress · Your body this week ·
 * Upcoming things · Contact family/owner · Motivation & quote.
 * -------------------------------------------------------------------------- */
@Composable
private fun HomeTabContent(
    state: DashboardState,
    contentPadding: PaddingValues,
    onToggleDone: (type: String, refId: String, isDone: Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // A. Week Progress (current week + progress indicator/timeline)
        item { HeaderSection(state) }

        // C. Upcoming Things (appointments, medicines, reminders, checkups…)
        item {
            UpcomingThingsSection(
                appointments = state.appointments,
                medicines = state.medicines,
                reminders = state.reminders,
                hasAccess = state.hasFullAccess,
                doneToday = state.doneToday,
                onToggleDone = onToggleDone
            )
        }

        // G. Contact Family / Owner
        if (!state.hasFullAccess && state.ownerMobile.isNotBlank()) {
            item { OwnerContactCard(state.ownerName, state.ownerMobile) }
        }
        if (state.hasFullAccess) {
            item { FamilyQuickView(state.familyMembers, state.hasFullAccess) }
        }

        // H. Motivation / Quote
        item { DailyQuoteSection(state.quote) }

        item { AppFooter() }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * Upcoming appointments, medicines, reminders and checkups grouped together.
 * Reuses the existing appointment & medicine cards without altering their logic.
 */
@Composable
private fun UpcomingThingsSection(
    appointments: List<AppointmentEntity>,
    medicines: List<MedicineEntity>,
    reminders: List<ReminderEntity>,
    hasAccess: Boolean,
    doneToday: Set<String>,
    onToggleDone: (type: String, refId: String, isDone: Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(icon = Icons.Default.Upcoming, title = "Upcoming")

        // Show every appointment, medicine and daily reminder that hasn't passed yet
        appointments.forEach { appointment ->
            UpcomingAppointmentCard(
                appointment = appointment,
                hasAccess = hasAccess,
                isDone = doneToday.contains(appointment.appointmentId),
                onToggleDone = { checked ->
                    onToggleDone(UPCOMING_TYPE_APPOINTMENT, appointment.appointmentId, checked)
                }
            )
        }
        medicines.forEach { medicine ->
            MedicineReminderCard(
                medicine = medicine,
                hasAccess = hasAccess,
                isDone = doneToday.contains(medicine.medicineId),
                onToggleDone = { checked ->
                    onToggleDone(UPCOMING_TYPE_MEDICINE, medicine.medicineId, checked)
                }
            )
        }
        reminders.forEach { reminder ->
            UpcomingReminderCard(
                reminder = reminder,
                hasAccess = hasAccess,
                isDone = doneToday.contains(reminder.id.toString()),
                onToggleDone = { checked: Boolean ->
                    onToggleDone(UPCOMING_TYPE_REMINDER, reminder.id.toString(), checked)
                }
            )
        }
        if (appointments.isEmpty() && medicines.isEmpty() && reminders.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBG)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Nothing scheduled yet. You're all caught up!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------------
 * QUICK ACTIONS TAB
 * All action-oriented features as a modern grid of cards.
 * -------------------------------------------------------------------------- */
@Composable
private fun QuickActionsTabContent(
    navController: NavController,
    hasFullAccess: Boolean,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { SectionHeader(icon = Icons.Default.Widgets, title = "Quick Actions") }

        // Existing quick-action grid (Medicines, Food, Appointments, Reports,
        // Reminders, Bills, Contraction, Journal) — unchanged navigation targets.
        item { QuickActionsGrid(navController) }

        // Emergency action, surfaced for the mother.
        if (hasFullAccess) {
            item { EmergencyActionCard(navController) }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun EmergencyActionCard(navController: NavController) {
    Card(
        onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:102")
            navController.context.startActivity(intent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emergency SOS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Call ambulance (102)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/* ----------------------------------------------------------------------------
 * YOUR HEALTH TAB
 * Health metrics, kick counter, symptoms and quick links to nutrition/reports.
 * -------------------------------------------------------------------------- */
@Composable
private fun YourHealthTabContent(
    state: DashboardState,
    contentPadding: PaddingValues,
    onUpdateWater: (Int) -> Unit,
    onUpdateSleep: (Float) -> Unit,
    onUpdateWeight: (Float) -> Unit,
    onUpdateSteps: (Int) -> Unit,
    onIncrementKicks: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Health metrics: water, sleep, weight, steps.
        item {
            MotherHealthSection(
                healthData = state.healthData,
                hasAccess = state.hasFullAccess,
                onUpdateWater = onUpdateWater,
                onUpdateSleep = onUpdateSleep,
                onUpdateWeight = onUpdateWeight,
                onUpdateSteps = onUpdateSteps
            )
        }

        // Today's Schedule: daily medicine/meal/routine checklist (self-contained, owner-mark only).
        item { TodayScheduleSection() }

        // Baby movement tracking.
        if (state.pregnancyWeek >= 18) {
            item {
                KickCounterCard(state.kickCount, state.hasFullAccess, onIncrementKicks)
            }
        }

        // Symptoms.
        if (state.symptoms.isNotEmpty()) {
            item { RecentSymptomsSection(state.symptoms) }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/** Reusable section header with a leading icon, matching the dashboard style. */
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun QuickActionsGrid(navController: NavController) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                title = "Medicines", 
                icon = Icons.Default.MedicalServices, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Medicine.route) }
            )
            ActionCard(
                title = "Food", 
                icon = Icons.Default.Restaurant, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Food.route) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                title = "Appointments", 
                icon = Icons.Default.Event, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Appointment.route) }
            )
            ActionCard(
                title = "Reports", 
                icon = Icons.Default.BarChart, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Reports.route) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                title = "Reminders", 
                icon = Icons.Default.Alarm, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Reminders.route) }
            )
            ActionCard(
                title = "Bills", 
                icon = Icons.Default.AccountBalanceWallet, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Billing.route) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                title = "Contraction", 
                icon = Icons.Default.Timer, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.ContractionTimer.route) }
            )
            ActionCard(
                title = "Journal", 
                icon = Icons.Default.Book, 
                modifier = Modifier.weight(1f), 
                onClick = { navController.navigate(Screen.Journal.route) }
            )
        }
    }
}

@Composable
fun ActionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(110.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, 
                contentDescription = title, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title, 
                fontWeight = FontWeight.ExtraBold, 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HeaderSection(state: DashboardState) {
    val trimesterColor = when (state.trimester) {
        1 -> Color(0xFFFFD1DC)
        2 -> Color(0xFFB2EBF2)
        else -> Color(0xFFC8E6C9)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = trimesterColor.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Week ${state.pregnancyWeek} Day ${state.pregnancyDay}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Baby is the size of a ${state.weekData.babySize}", style = MaterialTheme.typography.bodyMedium)
                    }
                    AsyncImage(
                        model = state.weekData.babyIllustrationUrl ?: "https://cdn-icons-png.flaticon.com/512/3209/3209114.png",
                        contentDescription = "Baby Size",
                        modifier = Modifier.size(60.dp)
                    )
                }

                if (state.weekData.babyWeight.isNotBlank() || state.weekData.babyLength.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.weekData.babySize.isNotBlank()) {
                            BabyStatPill(
                                icon = Icons.Default.Eco,
                                label = "Size",
                                value = state.weekData.babySize,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (state.weekData.babyWeight.isNotBlank()) {
                            BabyStatPill(
                                icon = Icons.Default.MonitorWeight,
                                label = "Weight",
                                value = state.weekData.babyWeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (state.weekData.babyLength.isNotBlank()) {
                            BabyStatPill(
                                icon = Icons.Default.Straighten,
                                label = "Length",
                                value = state.weekData.babyLength,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { state.pregnancyWeek / 40f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Trimester ${state.trimester}", style = MaterialTheme.typography.labelSmall)
                    state.user?.dueDate?.let {
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        Text(text = "EDD: ${sdf.format(Date(it))}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BabyStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MotherHealthSection(
    healthData: MotherHealthData,
    hasAccess: Boolean,
    onUpdateWater: (Int) -> Unit,
    onUpdateSleep: (Float) -> Unit,
    onUpdateWeight: (Float) -> Unit,
    onUpdateSteps: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf<HealthDialogType?>(null) }

    if (showDialog != null && hasAccess) {
        HealthUpdateDialog(
            type = showDialog!!,
            currentValue = when(showDialog!!) {
                HealthDialogType.WATER -> healthData.waterIntake.toString()
                HealthDialogType.SLEEP -> healthData.sleepHours.toString()
                HealthDialogType.WEIGHT -> healthData.weight.toString()
                HealthDialogType.STEPS -> healthData.steps.toString()
            },
            onDismiss = { showDialog = null },
            onSave = { value ->
                when(showDialog!!) {
                    HealthDialogType.WATER -> onUpdateWater(value.toIntOrNull() ?: 0)
                    HealthDialogType.SLEEP -> onUpdateSleep(value.toFloatOrNull() ?: 0f)
                    HealthDialogType.WEIGHT -> onUpdateWeight(value.toFloatOrNull() ?: 0f)
                    HealthDialogType.STEPS -> onUpdateSteps(value.toIntOrNull() ?: 0)
                }
                showDialog = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Your Health", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Water is now a visual 8-glass tracker: tapping the Nth glass fills glasses 1..N.
        // The underlying value still flows through onUpdateWater(glasses) — logic unchanged.
        WaterGlassesCard(
            glasses = healthData.waterIntake,
            hasAccess = hasAccess,
            onSetGlasses = onUpdateWater
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthCard(
                title = "Sleep",
                value = healthData.sleepHours.toString(),
                unit = "hours",
                icon = Icons.Default.Bedtime,
                modifier = Modifier.weight(1f),
                color = Color(0xFF9575CD),
                onClick = { if (hasAccess) showDialog = HealthDialogType.SLEEP }
            )
            HealthCard(
                title = "Weight",
                value = healthData.weight.toString(),
                unit = "kg",
                icon = Icons.Default.MonitorWeight,
                modifier = Modifier.weight(1f),
                color = Color(0xFFF06292),
                onClick = { if (hasAccess) showDialog = HealthDialogType.WEIGHT }
            )
            HealthCard(
                title = "Steps",
                value = healthData.steps.toString(),
                unit = "steps",
                icon = Icons.Default.DirectionsWalk,
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFB74D),
                onClick = { if (hasAccess) showDialog = HealthDialogType.STEPS }
            )
        }
    }
}

/**
 * Visual water tracker showing [goal] glasses. Tapping the Nth glass fills 1..N (i.e. sets the
 * intake to N); tapping the current last filled glass clears it (N-1). The value is reported
 * through [onSetGlasses] which maps to the existing absolute water-update logic — no logic change.
 */
@Composable
fun WaterGlassesCard(
    glasses: Int,
    hasAccess: Boolean,
    onSetGlasses: (Int) -> Unit,
    goal: Int = 8
) {
    val waterColor = Color(0xFF64B5F6)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = waterColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Water", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${glasses.coerceIn(0, goal)}/$goal glasses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..goal).forEach { index ->
                    val filled = index <= glasses
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (hasAccess) Modifier.clickable {
                                    onSetGlasses(if (index == glasses) index - 1 else index)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (filled) Icons.Default.LocalDrink else Icons.Outlined.LocalDrink,
                            contentDescription = "Glass $index",
                            tint = if (filled) waterColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

enum class HealthDialogType { WATER, SLEEP, WEIGHT, STEPS }

/** Sensible real-world bounds + input rules for each health metric. */
private data class HealthInputSpec(
    val isDecimal: Boolean,
    val min: Double,
    val max: Double,
    val unit: String,
    val maxLength: Int
)

private fun healthInputSpec(type: HealthDialogType): HealthInputSpec = when (type) {
    HealthDialogType.WATER -> HealthInputSpec(isDecimal = false, min = 0.0, max = 30.0, unit = "glasses", maxLength = 2)
    HealthDialogType.SLEEP -> HealthInputSpec(isDecimal = true, min = 0.0, max = 24.0, unit = "hours", maxLength = 4)
    HealthDialogType.WEIGHT -> HealthInputSpec(isDecimal = true, min = 20.0, max = 250.0, unit = "kg", maxLength = 6)
    HealthDialogType.STEPS -> HealthInputSpec(isDecimal = false, min = 0.0, max = 100000.0, unit = "steps", maxLength = 6)
}

private fun formatBound(value: Double, isDecimal: Boolean): String =
    if (isDecimal) value.toString().removeSuffix(".0") else value.toInt().toString()

@Composable
fun HealthUpdateDialog(
    type: HealthDialogType,
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val spec = remember(type) { healthInputSpec(type) }
    // Start from the current value, but drop any junk so the field begins clean.
    var value by remember { mutableStateOf(currentValue.filter { it.isDigit() || it == '.' }) }

    val number = value.toDoubleOrNull()
    val errorMessage: String? = when {
        value.isBlank() -> null
        number == null -> "Enter a valid number"
        number < spec.min -> "Must be at least ${formatBound(spec.min, spec.isDecimal)} ${spec.unit}"
        number > spec.max -> "Must not exceed ${formatBound(spec.max, spec.isDecimal)} ${spec.unit}"
        else -> null
    }
    val isValid = value.isNotBlank() && errorMessage == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update ${type.name.lowercase().replaceFirstChar { it.uppercase() }}") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    // Allow only digits (and a single dot for decimal metrics), capped in length.
                    var filtered = if (spec.isDecimal) {
                        input.filter { it.isDigit() || it == '.' }
                    } else {
                        input.filter { it.isDigit() }
                    }
                    if (spec.isDecimal) {
                        val firstDot = filtered.indexOf('.')
                        if (firstDot >= 0) {
                            filtered = filtered.substring(0, firstDot + 1) +
                                filtered.substring(firstDot + 1).replace(".", "")
                        }
                    }
                    if (filtered.length <= spec.maxLength) value = filtered
                },
                label = { Text("Enter new value") },
                suffix = { Text(spec.unit) },
                isError = errorMessage != null,
                supportingText = {
                    Text(
                        text = errorMessage
                            ?: "Allowed: ${formatBound(spec.min, spec.isDecimal)}–${formatBound(spec.max, spec.isDecimal)} ${spec.unit}"
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (spec.isDecimal) KeyboardType.Decimal else KeyboardType.Number
                ),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value) }, enabled = isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun HealthCard(
    title: String, 
    value: String, 
    unit: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    color: Color,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() }.shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** daily_schedule_status `type` values for the Upcoming cards (kept distinct from the schedule feature's enum). */
private const val UPCOMING_TYPE_APPOINTMENT = "APPOINTMENT"
private const val UPCOMING_TYPE_MEDICINE = "MEDICINE"
private const val UPCOMING_TYPE_REMINDER = "REMINDER"

/**
 * Done control for an Upcoming card: the OWNER gets a tappable checkbox (turns into a green tick),
 * while read-only family members see the resulting state as a green check (done) or a faint
 * pending circle — so they see the owner's reflection without being able to change it.
 */
@Composable
private fun UpcomingDoneControl(
    isDone: Boolean,
    hasAccess: Boolean,
    onToggleDone: (Boolean) -> Unit
) {
    val doneGreen = Color(0xFF2E7D32)
    if (hasAccess) {
        Checkbox(
            checked = isDone,
            onCheckedChange = { onToggleDone(it) },
            colors = CheckboxDefaults.colors(checkedColor = doneGreen)
        )
    } else {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isDone) "Done" else "Pending",
            tint = if (isDone) doneGreen else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun MedicineReminderCard(
    medicine: MedicineEntity,
    hasAccess: Boolean,
    isDone: Boolean,
    onToggleDone: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Next Medicine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(text = medicine.name, fontWeight = FontWeight.Bold)
                Text(text = medicine.timing, style = MaterialTheme.typography.bodySmall)
            }
            UpcomingDoneControl(isDone = isDone, hasAccess = hasAccess, onToggleDone = onToggleDone)
        }
    }
}

@Composable
fun UpcomingAppointmentCard(
    appointment: AppointmentEntity,
    hasAccess: Boolean,
    isDone: Boolean,
    onToggleDone: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Upcoming Appointment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(text = "Dr. ${appointment.doctorName}", fontWeight = FontWeight.Bold)
                Text(text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(appointment.appointmentTime)), style = MaterialTheme.typography.bodySmall)
            }
            UpcomingDoneControl(isDone = isDone, hasAccess = hasAccess, onToggleDone = onToggleDone)
        }
    }
}

@Composable
fun UpcomingReminderCard(
    reminder: ReminderEntity,
    hasAccess: Boolean,
    isDone: Boolean,
    onToggleDone: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val type = if (reminder.isAutoGenerated) "Daily Schedule" else "Reminder"
                Text(text = type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(text = reminder.title, fontWeight = FontWeight.Bold)
                Text(text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(reminder.time)), style = MaterialTheme.typography.bodySmall)
            }
            UpcomingDoneControl(isDone = isDone, hasAccess = hasAccess, onToggleDone = onToggleDone)
        }
    }
}

@Composable
fun KickCounterCard(count: Int, hasAccess: Boolean, onIncrement: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Baby Kicks Today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Count the movements of your little one",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = count.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (hasAccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onIncrement,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I felt a kick!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RecentSymptomsSection(symptoms: List<SymptomLogEntity>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Recent Symptoms", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(symptoms.take(5)) { symptom ->
                AssistChip(
                    onClick = { },
                    label = { Text(symptom.symptomName) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
fun OwnerContactCard(name: String, mobile: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Contact $name", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text = mobile, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(
                onClick = {
                    val phone = mobile.filter { it.isDigit() }
                    val url = "https://wa.me/$phone"
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(url)
                    }
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_whatsapp),
                    contentDescription = "WhatsApp",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun FamilyQuickView(members: List<FamilyMemberEntity>, hasAccess: Boolean) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Connected Family", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { member ->
                val avatarColors = remember(member.name) {
                    val colors = listOf(
                        Color(0xFFE0F7FA) to Color(0xFF006064), // Cyan
                        Color(0xFFF1F8E9) to Color(0xFF33691E), // Light Green
                        Color(0xFFFFF3E0) to Color(0xFFE65100), // Orange
                        Color(0xFFFCE4EC) to Color(0xFF880E4F), // Pink
                        Color(0xFFF3E5F5) to Color(0xFF4A148C), // Purple
                        Color(0xFFE8EAF6) to Color(0xFF1A237E), // Indigo
                        Color(0xFFE0F2F1) to Color(0xFF004D40)  // Teal
                    )
                    colors[Math.abs(member.name.hashCode()) % colors.size]
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(avatarColors.first),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = avatarColors.second
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = member.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = member.role, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        
                        // Owner can WhatsApp a member directly — only when we have their phone number.
                        if (hasAccess && member.phoneNumber.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val phone = member.phoneNumber.filter { it.isDigit() }
                                    val url = "https://wa.me/$phone"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(url)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_whatsapp),
                                    contentDescription = "WhatsApp",
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }
            }
            if (members.isEmpty()) {
                Text("No family members connected.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DailyQuoteSection(quote: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = quote,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }
    }
}
