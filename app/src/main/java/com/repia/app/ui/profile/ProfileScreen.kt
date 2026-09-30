package com.repia.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repia.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    var voiceFeedbackEnabled by remember { mutableStateOf(true) }
    var frontCameraPreferred by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableFloatStateOf(0.7f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "PROFIL & RÉGLAGES",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Avatar Header Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
            color = SurfaceDark
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = NeonCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NeonCyan),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Athlète RepIA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Niveau : Avancé • IA Calibrée",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RECORDS PERSONNELS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Personal Records Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordChip(title = "Pompes", value = "42 reps", color = NeonEmerald, modifier = Modifier.weight(1f))
            RecordChip(title = "Squats", value = "60 reps", color = NeonOrange, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordChip(title = "Dips", value = "28 reps", color = NeonCyan, modifier = Modifier.weight(1f))
            RecordChip(title = "Tractions", value = "18 reps", color = NeonPurple, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "PARAMÈTRES IA & CAPTEURS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Setting Item 1: Voice Feedback
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = NeonCyan)
                    Column {
                        Text(text = "Retour vocal IA", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(text = "Annonce vocale des répétitions", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Switch(
                    checked = voiceFeedbackEnabled,
                    onCheckedChange = { voiceFeedbackEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonEmerald
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Setting Item 2: Front Camera Preferred
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CameraFront, contentDescription = null, tint = NeonCyan)
                    Column {
                        Text(text = "Caméra avant par défaut", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(text = "Préférer la caméra faciale", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Switch(
                    checked = frontCameraPreferred,
                    onCheckedChange = { frontCameraPreferred = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonEmerald
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Setting Item 3: Sensitivity Slider
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Sensibilité de détection IA", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonEmerald,
                        activeTrackColor = NeonEmerald,
                        inactiveTrackColor = SurfaceVariantDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RecordChip(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
