package com.adarsh.hellomom.presentation.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adarsh.hellomom.presentation.components.NAV_SELECTED_TAB_KEY
import kotlinx.coroutines.flow.collectLatest

/**
 * App-wide floating voice assistant: a multi-color animated mic button that listens and acts
 * entirely in the background. Hosted once in [MainActivity] above the NavGraph.
 * Hidden on auth/splash screens.
 */
@Composable
fun VoiceAssistantOverlay(
    navController: NavController,
    viewModel: VoiceAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // The mic appears ONLY on the screens that also show the in-app "AI" FAB (Home dashboard and
    // Baby Progress) — and not while an AI web chat is open (micVisible == false).
    val hidden = route !in MIC_ROUTES || !state.micVisible

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.sendIntent(VoiceAssistantIntent.OpenAndListen)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is VoiceAssistantEffect.Navigate ->
                    navController.navigate(effect.route) { launchSingleTop = true }
                is VoiceAssistantEffect.NavigateToTab ->
                    selectDashboardTab(navController, effect.tabIndex)
            }
        }
    }

    if (hidden) return

    // Greet once per app launch, the first time the assistant is visible on a real screen. The VM
    // guards against replays, so re-firing on hidden→visible transitions is harmless.
    LaunchedEffect(Unit) { viewModel.sendIntent(VoiceAssistantIntent.Welcome) }

    fun onMicTap() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.sendIntent(VoiceAssistantIntent.OpenAndListen)
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MicButton(
            listening = state.status != VoiceStatus.IDLE && state.status != VoiceStatus.FALLBACK,
            onClick = { onMicTap() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 168.dp)
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_anim")
    
    // Rotating gradient colors for the "Gemini-style" background
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(if (listening) scale else 1f),
        contentAlignment = Alignment.Center
    ) {
        if (listening) {
            // Multi-color animated ring behind the FAB
            Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF4285F4), // Blue
                            Color(0xFFEA4335), // Red
                            Color(0xFFFBBC05), // Yellow
                            Color(0xFF34A853), // Green
                            Color(0xFF4285F4)
                        )
                    )
                )
            }
        }

        FloatingActionButton(
            onClick = onClick,
            containerColor = if (listening) Color.White else MaterialTheme.colorScheme.primary,
            contentColor = if (listening) Color(0xFF4285F4) else MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice assistant"
            )
        }
    }
}

/**
 * Open the Home dashboard and select one of its tabs (Health / Quick / …). Mirrors the
 * `navigateToDashboardTab` pattern the Baby/Profile screens use: stamp the desired tab on the Home
 * back-stack entry (the dashboard observes it) and pop to Home; if Home isn't in the stack yet,
 * navigate to it and stamp the new entry.
 */
private fun selectDashboardTab(navController: NavController, tabIndex: Int) {
    val homeEntry = runCatching { navController.getBackStackEntry("home") }.getOrNull()
    if (homeEntry != null) {
        homeEntry.savedStateHandle[NAV_SELECTED_TAB_KEY] = tabIndex
        navController.popBackStack("home", false) // no-op if already on Home; the tab flow still fires
    } else {
        navController.navigate("home") { launchSingleTop = true }
        runCatching {
            navController.getBackStackEntry("home").savedStateHandle[NAV_SELECTED_TAB_KEY] = tabIndex
        }
    }
}

// Routes that show the in-app "AI" FAB — the mic is shown only here, alongside it.
private val MIC_ROUTES = setOf("home", "baby_progress")
