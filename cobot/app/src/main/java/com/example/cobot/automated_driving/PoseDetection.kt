package com.example.cobot.automated_driving

import android.content.Context
import android.graphics.RectF
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

data class SimpleLandmark(val x: Float, val y: Float, val z: Float)

fun setupPoseLandmarker(context: Context): PoseLandmarker? {
    return try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .build()

        PoseLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        null
    }
}

fun determinePositionFromLandmarks(
    landmarks: List<SimpleLandmark>,
    onPositionDetected: (String) -> Unit
) {
    val leftShoulder = landmarks.getOrNull(11)
    val rightShoulder = landmarks.getOrNull(12)

    if (leftShoulder == null || rightShoulder == null) {
        onPositionDetected("Not visible")
        return
    }

    val centerX = (leftShoulder.x + rightShoulder.x) / 2f
    val position = when {
        centerX < 0.35f -> "RIGHT"
        centerX > 0.65f -> "LEFT"
        else -> "CENTER"
    }
    onPositionDetected(position)
}

fun getBoundingBoxFromLandmarks(landmarks: List<SimpleLandmark>): RectF {
    val focusedIndices = listOf(0, 11, 12, 23, 24) // Nose, L/R shoulders, L/R hips
    val points = landmarks.filterIndexed { index, _ -> index in focusedIndices }

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }

    return RectF(minX, minY, maxX, maxY)
}

fun estimateDistance(boundingBox: RectF): String {
    val boxHeight = boundingBox.height()
    return when {
        boxHeight > 0.4f -> "Very Close"
        boxHeight > 0.25f -> "Close"
        boxHeight > 0.15f -> "Medium"
        boxHeight > 0.05f -> "Far"
        else -> "Very Far"
    }
}