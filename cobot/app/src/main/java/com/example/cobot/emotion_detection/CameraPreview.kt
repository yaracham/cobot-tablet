package com.example.cobot.emotion_detection

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    captureFrame: Int,
    onFrameCaptured: (Bitmap) -> Unit
) {
    // Store the current captureFrame value to access it in the analyzer
    val currentCaptureFrame = remember { mutableIntStateOf(captureFrame) }

    // Update the stored value when captureFrame changes
    LaunchedEffect(captureFrame) {
        currentCaptureFrame.intValue = captureFrame
        Log.d("CameraPreview", "Capture frame updated to: $captureFrame")
    }

    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Add a variable to track the last processed frame
            var lastProcessedFrame = 0

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    // Get the current capture frame value
                    val frameToCapture = currentCaptureFrame.intValue

                    // Only process if this is a new frame request
                    if (frameToCapture > lastProcessedFrame) {
                        Log.d("CameraPreview", "Processing frame: $frameToCapture (last: $lastProcessedFrame)")
                        lastProcessedFrame = frameToCapture

                        val bitmap = imageProxyToBitmap(imageProxy, context)
                        ctx.mainExecutor.execute {
                            onFrameCaptured(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Image analysis error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.d("CameraPreview", "Camera bound to lifecycle")
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
    })
}