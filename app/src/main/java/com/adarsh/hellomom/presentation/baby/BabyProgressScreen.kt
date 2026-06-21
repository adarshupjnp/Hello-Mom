package com.adarsh.hellomom.presentation.baby

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.R
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.AppBottomNavBar
import com.adarsh.hellomom.presentation.components.AppTab
import com.adarsh.hellomom.presentation.components.NAV_SELECTED_TAB_KEY
import com.adarsh.hellomom.presentation.components.ShimmerPlaceholder
import com.adarsh.hellomom.presentation.dashboard.HearYourBabyCard
import com.adarsh.hellomom.ui.theme.Accent
import com.adarsh.hellomom.ui.theme.Primary
import com.adarsh.hellomom.ui.theme.PrimaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Baby Progress — a dedicated section opened from the bottom bar's Baby tab.
 * Shows the pregnancy journey as a circular progress ring around a baby icon,
 * the expected delivery date and this week's development highlights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyProgressScreen(
    navController: NavController,
    viewModel: BabyProgressViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Baby Progress",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            // Same bottom bar as the dashboard so Baby Progress feels like a main section.
            AppBottomNavBar(
                selectedTab = AppTab.BABY,
                onSelect = { tab ->
                    if (tab == AppTab.BABY) {
                        // Already here.
                    } else {
                        navigateToDashboardTab(navController, tab.ordinal)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            if (state.isLoading) {
                BabyProgressShimmer()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))

                    BabyProgressRing(
                        progress = state.progress,
                        week = state.week,
                        dayOfWeek = state.dayOfWeek
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "${(state.progress * 100).toInt()}% of the journey complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    TrimesterTracker(currentTrimester = state.trimester)

                    Spacer(Modifier.height(20.dp))

                    DueDateCard(dueDate = state.dueDate, daysToGo = state.daysToGo)

                    Spacer(Modifier.height(16.dp))

                    // Interactive "Hear Your Baby" card (moved here from the Home tab).
                    HearYourBabyCard(
                        week = state.week,
                        weight = state.weekData.babyWeight
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BabyStatCard(
                            icon = Icons.Default.Eco,
                            label = "Size",
                            value = state.weekData.babySize,
                            modifier = Modifier.weight(1f)
                        )
                        BabyStatCard(
                            icon = Icons.Default.MonitorWeight,
                            label = "Weight",
                            value = state.weekData.babyWeight,
                            modifier = Modifier.weight(1f)
                        )
                        BabyStatCard(
                            icon = Icons.Default.Straighten,
                            label = "Length",
                            value = state.weekData.babyLength,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    InfoCard(
                        title = "✨ This Week's Milestone",
                        body = state.weekData.weeklyMilestone
                    )

                    Spacer(Modifier.height(12.dp))

                    InfoCard(
                        title = "🧠 Development",
                        body = state.weekData.organDevelopment
                    )

                    Spacer(Modifier.height(12.dp))

                    InfoCard(
                        title = "💗 Your Body This Week",
                        body = state.motherChanges
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Skeleton shown while [BabyProgressViewModel] resolves the pregnancy data. Mirrors the real
 * layout (progress ring, trimester tracker, due-date hero, stat tiles and info cards) so the
 * screen settles in place instead of jumping from a spinner to a full page.
 */
@Composable
private fun BabyProgressShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // Circular placeholder standing in for the animated progress ring.
        Box(modifier = Modifier.size(300.dp)) {
            ShimmerPlaceholder(height = 300.dp, shape = RoundedCornerShape(150.dp))
        }

        Spacer(Modifier.height(16.dp))

        // "x% of the journey complete" line.
        ShimmerPlaceholder(
            modifier = Modifier.width(220.dp),
            height = 16.dp,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Trimester tracker (three segments).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                ShimmerPlaceholder(
                    modifier = Modifier.weight(1f),
                    height = 8.dp,
                    shape = RoundedCornerShape(4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Due-date hero card.
        ShimmerPlaceholder(height = 82.dp, shape = RoundedCornerShape(20.dp))

        Spacer(Modifier.height(16.dp))

        // Size / weight / length stat tiles.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerPlaceholder(
                    modifier = Modifier.weight(1f),
                    height = 96.dp,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Milestone / development / body-changes info cards.
        repeat(3) {
            ShimmerPlaceholder(height = 96.dp, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Animated gradient ring with a pulsing baby icon and the current week in the center. */
@Composable
private fun BabyProgressRing(
    progress: Float,
    week: Int,
    dayOfWeek: Int
) {
    val context = LocalContext.current
    val babySizeRes = remember(week) {
        val weekFormatted = String.format("%02d", week.coerceIn(1, 40))
        // Map week 1, 2, 3 to sz_01... as per the user's list (1-3 uses poppy seed)
        val namePart = when (week) {
            1, 2, 3 -> "poppy_seed"
            4 -> "sesame_seed"
            5 -> "apple_seed"
            6 -> "sweet_pea"
            7 -> "blueberry"
            8 -> "raspberry"
            9 -> "grape"
            10 -> "kumquat"
            11 -> "fig"
            12 -> "lime"
            13 -> "lemon"
            14 -> "nectarine"
            15 -> "apple"
            16 -> "avocado"
            17 -> "pomegranate"
            18 -> "sweet_potato"
            19 -> "mango"
            20 -> "banana"
            21 -> "carrot"
            22 -> "papaya"
            23 -> "grapefruit"
            24 -> "corn"
            25 -> "rutabaga"
            26 -> "scallion"
            27 -> "cauliflower"
            28 -> "eggplant"
            29 -> "butternut_squash"
            30 -> "cabbage"
            31 -> "coconut"
            32 -> "jicama"
            33 -> "pineapple"
            34 -> "cantaloupe"
            35 -> "honeydew"
            36 -> "romaine_lettuce"
            37 -> "swiss_chard"
            38 -> "leek"
            39 -> "watermelon"
            40 -> "pumpkin"
            else -> "poppy_seed"
        }
        val resourceName = "sz_${if (week in 1..3) "01" else weekFormatted}_$namePart"
        val id = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        if (id != 0) id else R.drawable.ic_baby_transparent
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "RingProgress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "BabyPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BabyPulseScale"
    )

    val trackColor = Color(0xFFE9A57D).copy(alpha = 0.15f)
    val innerBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFFFDF9), // Glowing center
            Color(0xFFFFE0C2), // Warm peach
            Color(0xFFE9A57D).copy(alpha = 0.4f), // Soft edge
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background track.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Gradient progress arc, starting at 12 o'clock.
            rotate(degrees = -90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to Primary,
                            0.5f to PrimaryDark,
                            1.0f to Accent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Glowing knob at the tip of the arc.
            if (animatedProgress > 0f) {
                val angleRad = Math.toRadians(animatedProgress * 360.0 - 90.0)
                val radius = (size.minDimension - strokeWidth) / 2
                val knobCenter = Offset(
                    x = center.x + radius * cos(angleRad).toFloat(),
                    y = center.y + radius * sin(angleRad).toFloat()
                )
                drawCircle(color = Primary.copy(alpha = 0.35f), radius = strokeWidth * 0.9f, center = knobCenter)
                drawCircle(color = Color.White, radius = strokeWidth * 0.45f, center = knobCenter)
            }
        }

    Box(
        modifier = Modifier
            .size(250.dp)
            .clip(CircleShape)
            .background(innerBrush)
            .padding(bottom = 24.dp), // Added margin from bottom ring
        contentAlignment = Alignment.BottomCenter // Push content towards bottom
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // The dynamic illustration: shows the fruit/seed for the current week,
            // or falls back to the baby icon if the week's specific image is missing.
            Image(
                painter = painterResource(id = babySizeRes),
                contentDescription = "Baby Progress Illustration",
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulse)
                    .graphicsLayer(alpha = 0.99f)
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to Color.White,
                                0.95f to Color.White,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )

            Text(
                text = "Week $week",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Day $dayOfWeek • of 40 weeks",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
    }
}

/** Three rounded segments highlighting which trimester the pregnancy is in. */
@Composable
private fun TrimesterTracker(currentTrimester: Int) {
    val labels = listOf("1st Trimester", "2nd Trimester", "3rd Trimester")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val reached = index < currentTrimester
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (reached) {
                                Brush.horizontalGradient(listOf(Primary, PrimaryDark))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == currentTrimester - 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/** Gradient hero card with the expected delivery date and a days-to-go pill. */
@Composable
private fun DueDateCard(dueDate: Long?, daysToGo: Int?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Primary, PrimaryDark)))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Expected Delivery",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    text = dueDate?.let {
                        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(it))
                    } ?: "Set your due date in Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (daysToGo != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$daysToGo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = if (daysToGo == 1) "day to go" else "days to go",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/** Small stat tile (size / weight / length) for the current week. */
@Composable
private fun BabyStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Simple titled content card used for milestone / development / body changes. */
@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Switch to a dashboard tab from this screen: stamp the desired tab on the Home back-stack
 * entry and pop back to it (the dashboard observes this and selects the tab) — the same
 * pattern the Profile screen uses.
 */
private fun navigateToDashboardTab(navController: NavController, tabIndex: Int) {
    runCatching {
        val homeEntry = navController.getBackStackEntry(Screen.Home.route)
        homeEntry.savedStateHandle[NAV_SELECTED_TAB_KEY] = tabIndex
        navController.popBackStack(Screen.Home.route, inclusive = false)
    }.onFailure {
        navController.navigate(Screen.Home.route)
    }
}
