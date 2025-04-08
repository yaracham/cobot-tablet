package com.example.cobot.PersonFollowing

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.cobot.PersonFollowing.SimpleLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.lang.Exception

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    poseLandmarker: PoseLandmarker?,
    onPositionDetected: (String) -> Unit,
    onBoundingBoxUpdated: (android.graphics.RectF?) -> Unit,
    onLandmarksUpdated: (List<SimpleLandmark>) -> Unit
) {
    imageProxy.use { proxy ->
        val mediaImage = proxy.image ?: return@use
        try {
            val bitmap = mediaImageToBitmap(mediaImage)
            val rotatedBitmap = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            poseLandmarker?.let { detector ->
                val result = detector.detect(mpImage)
                val landmarks = result.landmarks().firstOrNull()?.map {
                    SimpleLandmark(it.x(), it.y(), it.z())
                } ?: emptyList()

                onLandmarksUpdated(landmarks)
                onBoundingBoxUpdated(getBoundingBoxFromLandmarks(landmarks))
                determinePositionFromLandmarks(landmarks, onPositionDetected)
            }
        } catch (e: Exception) {
            Log.e("PoseProcessor", "Error processing frame: ${e.message}")
        }
    }
}
