package com.example.cobot.automated_driving

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