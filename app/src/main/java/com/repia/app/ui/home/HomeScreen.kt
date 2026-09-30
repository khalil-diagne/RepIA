package com.repia.app.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repia.app.R
import com.repia.app.ui.theme.*

data class ExerciseItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onSelectExercise: (String) -> Unit
) {
    val exercises = remember {
        listOf(
            ExerciseItem("pompes", "Pompes", "Push-ups avec analyse d'amplitude", "Pectoraux & Triceps", Icons.Default.PlayArrow, NeonEmerald),
            ExerciseItem("dips", "Dips", "Dips guidés par vision artificielle", "Triceps & Épaules", Icons.Default.PlayArrow, NeonCyan),
            ExerciseItem("tractions", "Tractions", "Comptage des chin-ups / pull-ups", "Dos & Biceps", Icons.Default.PlayArrow, NeonPurple),
            ExerciseItem("squats", "Squats", "Contrôle de posture & profondeur", "Cuisses & Fessiers", Icons.Default.PlayArrow, NeonOrange)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = NeonEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_repia_logo),
                                    contentDescription = "Logo",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "RepIA",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonEmerald.copy(alpha = pulseAlpha))
                                )
                                Text(
                                    text = "AI MOTION ENGINE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = onToggleTheme,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                                contentDescription = "Thème",
                                tint = if (isDarkMode) NeonCyan else NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // High-Tech Hero Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(NeonEmerald.copy(alpha = 0.6f), NeonCyan.copy(alpha = 0.3f))),
                        RoundedCornerShape(24.dp)
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    NeonEmerald.copy(alpha = 0.12f),
                                    NeonCyan.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ANALYSE EN TEMPS RÉEL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sélectionnez votre exercice",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "L'IA détecte la répétition, valide l'angle et compte vos séries avec précision.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXERCICES DISPONIBLES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "${exercises.size} MODULES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exercise Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(exercises) { exo ->
                    ExerciseCard(exercise = exo, onClick = { onSelectExercise(exo.id) })
                }
            }
        }
    }
}

@Composable
fun ExerciseCard(
    exercise: ExerciseItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    exercise.color.copy(alpha = 0.4f),
                    GlassBorder.copy(alpha = 0.2f)
                )
            )
        ),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            exercise.color.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 300f
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = exercise.color.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, exercise.color.copy(alpha = 0.3f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = exercise.icon,
                                contentDescription = null,
                                tint = exercise.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Lancer",
                        tint = exercise.color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = exercise.color.copy(alpha = 0.12f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = exercise.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = exercise.color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = exercise.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = exercise.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
