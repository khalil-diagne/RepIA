package com.repia.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.repia.app.ui.theme.*

@Composable
fun RepIABottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onQuickStart: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Bar Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp)),
            color = GlassBackground
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Accueil
                BottomNavItem(
                    icon = Icons.Default.Home,
                    isSelected = currentRoute == "home",
                    onClick = { onNavigate("home") }
                )

                // Tab 2: Historique
                BottomNavItem(
                    icon = Icons.Default.BarChart,
                    isSelected = currentRoute == "history",
                    onClick = { onNavigate("history") }
                )

                // Space for Central Prominent Button
                Spacer(modifier = Modifier.width(64.dp))

                // Tab 3: Profil
                BottomNavItem(
                    icon = Icons.Default.Person,
                    isSelected = currentRoute == "profile",
                    onClick = { onNavigate("profile") }
                )
            }
        }

        // Central Prominent START Button
        Box(
            modifier = Modifier
                .offset(y = (-16).dp)
                .scale(pulseScale)
                .size(64.dp)
                .clip(CircleShape)
                .background(NeonGradient)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onQuickStart
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Démarrer séance",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (isSelected) 1.0f else 0.5f

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) NeonEmerald.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) NeonEmerald else TextSecondary.copy(alpha = alpha),
            modifier = Modifier.size(24.dp)
        )
    }
}
