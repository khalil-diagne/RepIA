package com.repia.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repia.app.ui.theme.*

data class WorkoutRecord(
    val date: String,
    val exercise: String,
    val reps: Int,
    val durationMin: Int,
    val accuracyPct: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    val historyItems = remember {
        listOf(
            WorkoutRecord("Aujourd'hui, 14:30", "Pompes", 32, 8, 98),
            WorkoutRecord("Hier, 18:15", "Squats", 45, 12, 95),
            WorkoutRecord("24 Sep, 10:00", "Tractions", 15, 6, 92),
            WorkoutRecord("22 Sep, 17:45", "Dips", 24, 10, 96),
            WorkoutRecord("20 Sep, 16:20", "Pompes", 28, 7, 94)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "HISTORIQUE",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly Progress Bar Chart Simulation Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVITÉ DE LA SEMAINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "144 REPS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bar Chart Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val days = listOf("L" to 0.4f, "M" to 0.7f, "M" to 0.3f, "J" to 0.9f, "V" to 0.6f, "S" to 0.8f, "D" to 0.5f)
                    days.forEach { (day, pct) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .fillMaxHeight(pct)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(NeonEmerald, NeonCyan)
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = day, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DERNIÈRES SÉANCES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(historyItems) { item ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.5f)),
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
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NeonEmerald.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.3f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = NeonEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = item.exercise,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = item.date,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${item.reps} REPS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                            Text(
                                text = "${item.durationMin} min • Forme ${item.accuracyPct}%",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
