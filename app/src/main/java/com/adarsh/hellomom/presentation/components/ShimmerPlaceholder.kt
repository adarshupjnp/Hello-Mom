package com.adarsh.hellomom.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush, shape)
    )
}

/**
 * Generic list/content loading placeholder: a stack of shimmering cards.
 * Use this in place of a CircularProgressIndicator for a modern loading feel.
 */
@Composable
fun ListShimmer(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    itemHeight: Dp = 88.dp
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            ShimmerPlaceholder(height = itemHeight, shape = RoundedCornerShape(16.dp))
        }
    }
}

@Composable
fun DashboardShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShimmerPlaceholder(height = 180.dp, shape = RoundedCornerShape(24.dp))
        ShimmerPlaceholder(height = 140.dp, shape = RoundedCornerShape(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerPlaceholder(modifier = Modifier.weight(1f), height = 120.dp)
            ShimmerPlaceholder(modifier = Modifier.weight(1f), height = 120.dp)
        }
        ShimmerPlaceholder(height = 100.dp)
        ShimmerPlaceholder(height = 80.dp)
    }
}
