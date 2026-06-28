package com.adarsh.hellomom.presentation.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import com.adarsh.hellomom.data.local.entity.UserEntity
import java.util.Locale
import kotlin.math.*

private const val HUB_RADIUS_FRACTION = 0.72f

/**
 * Modern Family Network Hub showing the owner in the center and family members in a circle
 * with animated heartbeat lines and real-time distance calculation.
 */
@Composable
fun FamilyNetworkHub(
    owner: UserEntity,
    members: List<FamilyMemberEntity>,
    trimester: Int,
    modifier: Modifier = Modifier
) {
    if (members.isEmpty()) return

    val trimesterColor = when (trimester) {
        1 -> Color(0xFFFFD1DC)
        2 -> Color(0xFFB2EBF2)
        else -> Color(0xFFC8E6C9)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "My Family Dashboard",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = trimesterColor.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Removed the redundant inner title
                    Spacer(modifier = Modifier.height(24.dp))

                    // The Circular Network
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background heartbeat paths connecting center to all nodes
                        val infiniteTransition = rememberInfiniteTransition(label = "HeartbeatLines")
                        val progress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "PulseProgress"
                        )

                        // 1. Map-like Background Circle (matches radius with nodes)
                        NetworkMapBackground(radiusFraction = HUB_RADIUS_FRACTION)

                        // 2. Draw heartbeat connections
                        HeartbeatConnections(memberCount = members.size, progress = progress, radiusFraction = HUB_RADIUS_FRACTION)

                        // 3. Central Owner node
                        OwnerNode(owner)

                        // 4. Family member nodes arranged in a circle
                        FamilyNodes(owner, members, radiusFraction = HUB_RADIUS_FRACTION)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkMapBackground(radiusFraction: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f * radiusFraction
        
        // Circular white/gray background
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = radius,
            center = center
        )
        
        // Subtle grid lines (simulating map/texture)
        val gridStep = 40.dp.toPx()
        val gridColor = Color.Gray.copy(alpha = 0.05f)
        
        // Vertical lines
        var x = center.x - radius
        while (x <= center.x + radius) {
            val h = sqrt(max(0f, radius * radius - (x - center.x) * (x - center.x)))
            drawLine(
                color = gridColor,
                start = Offset(x, center.y - h),
                end = Offset(x, center.y + h),
                strokeWidth = 1.dp.toPx()
            )
            x += gridStep
        }
        
        // Horizontal lines
        var y = center.y - radius
        while (y <= center.y + radius) {
            val w = sqrt(max(0f, radius * radius - (y - center.y) * (y - center.y)))
            drawLine(
                color = gridColor,
                start = Offset(center.x - w, y),
                end = Offset(center.x + w, y),
                strokeWidth = 1.dp.toPx()
            )
            y += gridStep
        }

        // Outer white border ring
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

@Composable
private fun OwnerNode(owner: UserEntity) {
    val infiniteTransition = rememberInfiniteTransition(label = "OwnerPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Heart icon background pulse
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer(scaleX = scale * 1.5f, scaleY = scale * 1.5f),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        )
        
        // Circular Avatar - STRICTLY CENTERED
        Surface(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = owner.fullName.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Labels positioned below the avatar without shifting it
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 48.dp)
        ) {
            Text(
                text = owner.fullName.split(" ").firstOrNull() ?: "You",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "(Owner)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun FamilyNodes(owner: UserEntity, members: List<FamilyMemberEntity>, radiusFraction: Float) {
    val ownerLat = owner.latitude ?: 0.0
    val ownerLon = owner.longitude ?: 0.0

    // Enhanced Layout: Avatar on circle, Labels outside
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            members.forEach { member ->
                val distance = calculateDistance(ownerLat, ownerLon, member.latitude ?: 0.0, member.longitude ?: 0.0)
                val formattedDistance = formatDistance(distance)
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
                    colors[abs(member.name.hashCode()) % colors.size]
                }

                // Avatar Composable
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = avatarColors.first,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = member.name.take(1).uppercase(),
                            color = avatarColors.second,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Label Composable
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = member.name.split(" ").firstOrNull() ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = formattedDistance,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
        val radius = min(constraints.maxWidth, constraints.maxHeight) / 2f * radiusFraction
        val angleStep = 2 * PI / members.size

        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            for (i in 0 until members.size) {
                val angle = i * angleStep - PI / 2
                val avatarPlaceable = placeables[i * 2]
                val labelPlaceable = placeables[i * 2 + 1]

                // Place avatar centered on the circle line
                val ax = center.x + radius * cos(angle).toFloat() - avatarPlaceable.width / 2f
                val ay = center.y + radius * sin(angle).toFloat() - avatarPlaceable.height / 2f
                avatarPlaceable.place(ax.toInt(), ay.toInt())

                // Place label outside the circle
                // We use an extra offset to push text beyond the avatar
                val labelPadding = 44.dp.toPx()
                val lx = center.x + (radius + labelPadding) * cos(angle).toFloat() - labelPlaceable.width / 2f
                val ly = center.y + (radius + labelPadding) * sin(angle).toFloat() - labelPlaceable.height / 2f
                labelPlaceable.place(lx.toInt(), ly.toInt())
            }
        }
    }
}

@Composable
private fun HeartbeatConnections(memberCount: Int, progress: Float, radiusFraction: Float) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val pulseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * radiusFraction
        val angleStep = 2 * PI / memberCount

        for (i in 0 until memberCount) {
            val angle = i * angleStep - PI / 2
            val target = Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )

            // Draw base line
            drawLine(
                color = color,
                start = center,
                end = target,
                strokeWidth = 1.dp.toPx()
            )

            // Draw animated heartbeat pulse
            val path = Path().apply {
                moveTo(center.x, center.y)
                
                val segments = 10
                for (s in 1..segments) {
                    val t = s.toFloat() / segments
                    val px = center.x + (target.x - center.x) * t
                    val py = center.y + (target.y - center.y) * t
                    
                    // Add heartbeat spikes (zigzag)
                    if (s == 4 || s == 6) {
                        val spike = 15.dp.toPx()
                        val normalX = -(target.y - center.y) / radius
                        val normalY = (target.x - center.x) / radius
                        val direction = if (s == 4) 1 else -1
                        lineTo(px + normalX * spike * direction, py + normalY * spike * direction)
                    } else {
                        lineTo(px, py)
                    }
                }
            }

            val pathMeasure = PathMeasure()
            pathMeasure.setPath(path, false)
            val pathLength = pathMeasure.length
            
            val pulseLen = 40.dp.toPx()
            val start = (pathLength + pulseLen) * progress - pulseLen
            
            val pulsePath = Path()
            pathMeasure.getSegment(
                startDistance = max(0f, start),
                stopDistance = min(pathLength, start + pulseLen),
                destination = pulsePath
            )

            drawPath(
                path = pulsePath,
                color = pulseColor,
                style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** Haversine formula to calculate distance between two points in km. */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    if (lat1 == 0.0 || lat2 == 0.0) return 0.0
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun formatDistance(distance: Double): String {
    return String.format(Locale.getDefault(), "%.1f km", distance)
}
