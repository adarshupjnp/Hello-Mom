package com.adarsh.hellomom.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Drives [FeedbackOverlay]. */
sealed interface UiFeedback {
    data class Loading(val message: String) : UiFeedback
    data class Success(val message: String) : UiFeedback
    data class Error(val message: String) : UiFeedback
}

/**
 * Animated, theme-aware success / error / loading overlay.
 *
 * Success and error states scale-in with a bounce and auto-dismiss; loading stays until
 * the caller clears it. Used to confirm add / edit / delete / download outcomes.
 */
@Composable
fun FeedbackOverlay(
    feedback: UiFeedback?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // Capture the latest non-null feedback so content survives the exit animation.
        val current = remember(feedback) { feedback } ?: return@AnimatedVisibility
        val isLoading = current is UiFeedback.Loading

        LaunchedEffect(current) {
            when (current) {
                is UiFeedback.Success -> { delay(1300); onDismiss() }
                is UiFeedback.Error -> { delay(2400); onDismiss() }
                is UiFeedback.Loading -> { /* stays until cleared */ }
            }
        }

        val scale = remember { Animatable(0.8f) }
        LaunchedEffect(current) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = !isLoading) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(40.dp)
                    .graphicsLayer(scaleX = scale.value, scaleY = scale.value),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (current) {
                        is UiFeedback.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            FeedbackText(current.message)
                        }
                        is UiFeedback.Success -> {
                            FeedbackIcon(
                                background = Color(0xFF2E7D32),
                                icon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                            )
                            FeedbackText(current.message)
                        }
                        is UiFeedback.Error -> {
                            FeedbackIcon(
                                background = MaterialTheme.colorScheme.error,
                                icon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                            )
                            FeedbackText(current.message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackIcon(background: Color, icon: @Composable () -> Unit) {
    // Gentle pulsing ring behind the icon.
    val transition = rememberInfiniteTransition(label = "feedbackPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer(scaleX = pulse, scaleY = pulse)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun FeedbackText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}
