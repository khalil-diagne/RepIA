package com.repia.app.ml

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseAnalyzer(
    context: Context,
    private val onResult: (List<Point>?, Long) -> Unit
) : ImageAnalysis.Analyzer {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker(context)
    }

    private fun setupPoseLandmarker(context: Context) {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .build()

        try {
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val frameTime = imageProxy.imageInfo.timestamp
        val mediaImage = imageProxy.image
        if (mediaImage != null && poseLandmarker != null) {
            try {
                // Conversion ImageProxy -> Bitmap
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()
                
                val result = poseLandmarker?.detectForVideo(mpImage, frameTime)
                val points = extractPoints(result)
                onResult(points, frameTime)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        imageProxy.close()
    }

    private fun extractPoints(result: PoseLandmarkerResult?): List<Point>? {
        if (result == null || result.landmarks().isEmpty()) return null
        val landmarks = result.landmarks()[0]
        return landmarks.map { landmark ->
            Point(
                x = landmark.x(),
                y = landmark.y(),
                visibility = landmark.visibility().orElse(1f)
            )
        }
    }

    fun close() {
        try {
            poseLandmarker?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        poseLandmarker = null
    }
}
