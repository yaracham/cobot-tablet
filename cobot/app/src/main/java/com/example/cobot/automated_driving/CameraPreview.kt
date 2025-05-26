package com.example.cobot.automated_driving

/**
 * CameraPreview.kt
 *
 * This file defines the `CameraPreview` composable function which sets up a live camera feed using Android's CameraX
 * and integrates MediaPipe's PoseLandmarker to perform real-time human pose detection.
 *
 * Core Components:
 * - `CameraPreview`: A Jetpack Compose Composable that displays the camera preview and analyzes each frame.
 * - ImageAnalysis pipeline: Captures frames from the front camera, converts them to bitmaps, and passes them to
 *   MediaPipe’s PoseLandmarker.
 * - PoseLandmarker integration: Extracts landmarks, bounding box, and determines user pose position (e.g., standing, sitting).
 * - Callback architecture: Allows external components to receive updates for detected pose position, bounding box,
 *   and landmarks for further use (e.g., UI, logic, logging).
 *
 * Parameters:
 * @param modifier Modifier for layout customization in Compose.
 * @param lifecycleOwner Lifecycle owner used to bind the camera lifecycle.
 * @param cameraExecutor ExecutorService for background camera analysis work.
 * @param poseLandmarker Optional instance of MediaPipe PoseLandmarker for human pose detection.
 * @param onPositionDetected Callback triggered with a string indicating the user's pose position.
 * @param onBoundingBoxUpdated Callback triggered with the bounding box surrounding detected landmarks.
 * @param onLandmarksUpdated Callback triggered with a list of detected pose landmarks.
 *
 * This module is designed to be modular and reusable for applications involving real-time motion tracking,
 * gesture-based interaction, or fitness-related use cases.
 */

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.graphics.RectF
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.ExecutorService

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview(
    modifier: Modifier,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    poseLandmarker: PoseLandmarker?,
    onPositionDetected: (String) -> Unit,
    onBoundingBoxUpdated: (RectF?) -> Unit,
    onLandmarksUpdated: (List<SimpleLandmark>) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(
                                imageProxy,
                                poseLandmarker,
                                onPositionDetected,
                                onBoundingBoxUpdated,
                                onLandmarksUpdated
                            )
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    poseLandmarker: PoseLandmarker?,
    onPositionDetected: (String) -> Unit,
    onBoundingBoxUpdated: (RectF?) -> Unit,
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
                    SimpleLandmark(x = it.x(), y = it.y(), z = it.z())
                } ?: emptyList()

                onLandmarksUpdated(landmarks)
                onBoundingBoxUpdated(getBoundingBoxFromLandmarks(landmarks))
                determinePositionFromLandmarks(landmarks, onPositionDetected)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}")
        }
    }
}