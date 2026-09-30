package com.repia.app.ui.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.repia.app.ml.Point
import com.repia.app.ml.PoseAnalyzer
import com.repia.app.ml.RepetitionCounter
import com.repia.app.ml.Stats
import com.repia.app.ui.components.SkeletonOverlay
import com.repia.app.ui.theme.*
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(exerciseId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var isFrontCamera by remember { mutableStateOf(true) }
    var stats by remember {
        mutableStateOf(Stats(exerciseId, exerciseId, 0, "haut", null, true))
    }
    var currentPoints by remember { mutableStateOf<List<Point>?>(null) }
    var showSummary by remember { mutableStateOf(false) }

    val counter = remember(exerciseId) { RepetitionCounter(exerciseId) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    // TextToSpeech for Voice Feedback
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    var previousRepCount by remember { mutableStateOf(0) }

    // Audio & Haptic feedback on rep increment
    LaunchedEffect(stats.compteur) {
        if (stats.compteur > previousRepCount) {
            previousRepCount = stats.compteur
            try {
                // Haptic feedback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }

                // Voice feedback in French
                tts?.speak("${stats.compteur}", TextToSpeech.QUEUE_FLUSH, null, "rep_${stats.compteur}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var poseAnalyzer by remember { mutableStateOf<PoseAnalyzer?>(null) }

    DisposableEffect(key1 = true) {
        poseAnalyzer = PoseAnalyzer(context) { points, _ ->
            currentPoints = points
            val updatedStats = counter.analyser(points)
            stats = updatedStats
        }
        onDispose {
            poseAnalyzer?.close()
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (hasCameraPermission) {
            // Camera Feed Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                poseAnalyzer?.let { analyzer ->
                                    it.setAnalyzer(cameraExecutor, analyzer)
                                }
                            }

                        val cameraSelector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                update = { previewView ->
                    // Re-bind when camera flips
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                poseAnalyzer?.let { analyzer ->
                                    it.setAnalyzer(cameraExecutor, analyzer)
                                }
                            }

                        val cameraSelector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )

            // High-Tech Neon Skeleton Overlay with Exact Alignment
            SkeletonOverlay(
                points = currentPoints,
                isFrontCamera = isFrontCamera,
                qualityColor = if (stats.positionValide) NeonEmerald else NeonOrange
            )

            // Top Floating Navigation Bar with Camera Flip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Surface(
                    onClick = { showSummary = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                }

                // Exercise Name Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stats.nom.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.2.sp,
                        color = NeonEmerald,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Camera Switch Button (Flip Camera)
                    Surface(
                        onClick = { isFrontCamera = !isFrontCamera },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Changer de caméra", tint = NeonCyan)
                        }
                    }

                    // Stop Button
                    Surface(
                        onClick = { showSummary = true },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder.copy(alpha = 0.6f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stop, contentDescription = "Terminer", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // High-Tech Glassmorphic Top HUD Overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(NeonEmerald.copy(alpha = 0.6f), NeonCyan.copy(alpha = 0.4f))),
                        RoundedCornerShape(28.dp)
                    ),
                color = Color(0xDD0F172A)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repetition Counter Column
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "RÉPÉTITIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${stats.compteur}",
                                color = NeonEmerald,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 48.sp
                            )
                            Text(
                                text = "REPS",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp)
                            .background(GlassBorder)
                    )

                    // Position State Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "POSTURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (stats.stage.lowercase() == "bas") NeonCyan.copy(alpha = 0.2f) else NeonEmerald.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (stats.stage.lowercase() == "bas") NeonCyan else NeonEmerald
                            )
                        ) {
                            Text(
                                text = stats.stage.uppercase(),
                                color = if (stats.stage.lowercase() == "bas") NeonCyan else NeonEmerald,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp)
                            .background(GlassBorder)
                    )

                    // Angle Column
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ANGLE IA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.2.sp
                        )
                        val angleText = stats.angle?.let { "${it.toInt()}°" } ?: "--"
                        Text(
                            text = angleText,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permission caméra requise pour l'analyse IA.",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // High-Tech Summary Dialog
    if (showSummary) {
        Dialog(onDismissRequest = { }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(listOf(NeonEmerald, NeonCyan))
                ),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NeonEmerald.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SÉANCE TERMINÉE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = stats.nom,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stat Cards Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceVariantDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "RÉPÉTITIONS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${stats.compteur}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceVariantDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "CALORIES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                val calories = (stats.compteur * 0.5).toInt()
                                Text(
                                    text = "~$calories kcal",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showSummary = false
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Retour au menu",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
