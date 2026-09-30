package com.repia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.repia.app.ml.Point
import com.repia.app.ui.theme.NeonCyan
import com.repia.app.ui.theme.NeonEmerald

fun mapPointToScreen(
    pointX: Float,
    pointY: Float,
    screenWidth: Float,
    screenHeight: Float,
    isFrontCamera: Boolean = true,
    imageWidth: Float = 480f,
    imageHeight: Float = 640f
): Offset {
    val xNorm = if (isFrontCamera) 1f - pointX else pointX
    val yNorm = pointY

    val viewAspect = screenWidth / screenHeight
    val imageAspect = imageWidth / imageHeight

    val scaleX: Float
    val scaleY: Float
    val offsetX: Float
    val offsetY: Float

    if (viewAspect > imageAspect) {
        scaleX = screenWidth
        scaleY = screenWidth / imageAspect
        offsetX = 0f
        offsetY = (screenHeight - scaleY) / 2f
    } else {
        scaleY = screenHeight
        scaleX = screenHeight * imageAspect
        offsetX = (screenWidth - scaleX) / 2f
        offsetY = 0f
    }

    val mappedX = offsetX + (xNorm * scaleX)
    val mappedY = offsetY + (yNorm * scaleY)

    return Offset(mappedX, mappedY)
}

@Composable
fun SkeletonOverlay(
    points: List<Point>?,
    modifier: Modifier = Modifier,
    qualityColor: Color = NeonEmerald,
    isFrontCamera: Boolean = true
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        points?.let { pts ->
            val screenWidth = size.width
            val screenHeight = size.height

            // Bones (Connections)
            val connections = listOf(
                Pair(11, 12), // Shoulders
                Pair(11, 13), Pair(13, 15), // Left arm
                Pair(12, 14), Pair(14, 16), // Right arm
                Pair(11, 23), Pair(12, 24), Pair(23, 24), // Torso
                Pair(23, 25), Pair(25, 27), // Left leg
                Pair(24, 26), Pair(26, 28)  // Right leg
            )

            for (conn in connections) {
                if (conn.first < pts.size && conn.second < pts.size) {
                    val p1 = pts[conn.first]
                    val p2 = pts[conn.second]
                    if (p1.visibility > 0.5f && p2.visibility > 0.5f) {
                        val start = mapPointToScreen(p1.x, p1.y, screenWidth, screenHeight, isFrontCamera)
                        val end = mapPointToScreen(p2.x, p2.y, screenWidth, screenHeight, isFrontCamera)

                        // 3D Tube Outer Glow Line
                        drawLine(
                            color = qualityColor.copy(alpha = 0.35f),
                            start = start,
                            end = end,
                            strokeWidth = 16f,
                            cap = StrokeCap.Round
                        )
                        // 3D Tube Inner Highlight Line
                        drawLine(
                            color = NeonCyan,
                            start = start,
                            end = end,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                        // 3D Specular Center Core Line
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f),
                            start = start,
                            end = end,
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 3D Sphere Joints
            for (p in pts) {
                if (p.visibility > 0.5f) {
                    val center = mapPointToScreen(p.x, p.y, screenWidth, screenHeight, isFrontCamera)

                    // Outer 3D Sphere Glow
                    drawCircle(
                        color = qualityColor.copy(alpha = 0.3f),
                        radius = 20f,
                        center = center
                    )
                    // Mid 3D Sphere Ring
                    drawCircle(
                        color = NeonCyan,
                        radius = 11f,
                        center = center
                    )
                    // 3D Specular Top-Left Highlight
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = Offset(center.x - 2f, center.y - 2f)
                    )
                }
            }
        }
    }
}
