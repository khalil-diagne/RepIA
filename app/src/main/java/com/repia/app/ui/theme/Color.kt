package com.repia.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Couleurs sémantiques Cyber-Fitness
val NeonEmerald = Color(0xFF10B981)
val NeonEmeraldDark = Color(0xFF047857)
val NeonCyan = Color(0xFF06B6D4)
val NeonPurple = Color(0xFF8B5CF6)
val NeonOrange = Color(0xFFF97316)
val NeonRed = Color(0xFFEF4444)
val NeonYellow = Color(0xFFFACC15)

// Fonds & Surfaçage Dark Space
val BackgroundDark = Color(0xFF0B0F19)
val SurfaceDark = Color(0xFF151D2A)
val SurfaceVariantDark = Color(0xFF1E293B)
val GlassBorder = Color(0xFF334155)
val GlassBackground = Color(0xEE0F172A)

// Textes
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

// Legacy / Support
val PrimaryGreen = NeonEmerald
val PrimaryGreenDark = NeonEmeraldDark
val AccentCyan = NeonCyan
val OnSurfaceDark = TextPrimary
val CardBackground = SurfaceVariantDark

// Dégradés Néon
val NeonGradient = Brush.horizontalGradient(listOf(NeonEmerald, NeonCyan))
val DangerGradient = Brush.horizontalGradient(listOf(NeonRed, NeonOrange))
val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0B0F19)))
val CardGlowGradient = Brush.radialGradient(listOf(NeonEmerald.copy(alpha = 0.15f), Color.Transparent))
