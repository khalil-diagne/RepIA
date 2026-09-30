package com.repia.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.repia.app.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    glowColor: Color = NeonEmerald,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && onClick != null) 0.95f else 1.0f, label = "3dScale")

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RepIAShapes.large)
            .border(
                1.5.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        borderColor.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.80f)
                    )
                ),
                RepIAShapes.large
            )
            .then(clickableModifier),
        color = SurfaceDark,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            glowColor.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}
