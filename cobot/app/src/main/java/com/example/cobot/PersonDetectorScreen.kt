package com.example.cobot

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun PersonDetectorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for initialization
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }

    // Create PersonDetector instance with error handling
    val personDetector = remember {
        try {
            val detector = PersonDetector(context)
            isInitialized = true
            detector
        } catch (e: Exception) {
            Log.e("PersonDetectorScreen", "Failed to initialize PersonDetector", e)
            initError = e.message ?: "Unknown error initializing person detector"
            null
        }
    }

    // Camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Clean up resources when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            personDetector?.release()
        }
    }

    // Show error if initialization failed
    if (initError != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Error initializing person detector:")
                Text(initError ?: "Unknown error")
                Text("Check logs for details")
            }
        }
        return
    }

    // Show loading until initialized
    if (!isInitialized || personDetector == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Full screen camera preview with person detection
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraPreviewWithDetection(
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            personDetector = personDetector
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun CameraPreviewWithDetection(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    personDetector: PersonDetector
) {
    // State to trigger frame processing
    var frameCounter by remember { mutableIntStateOf(0) }

    // Process frames at a reasonable rate
    LaunchedEffect(Unit) {
        while(true) {
            delay(200) // 5 FPS to reduce processing load
            frameCounter++
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    // Track the last processed frame
                    var lastProcessedFrame = 0

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            // Only process if this is a new frame request
                            if (frameCounter > lastProcessedFrame) {
                                lastProcessedFrame = frameCounter

                                // Convert ImageProxy to Bitmap
                                val bitmap = imageProxyToBitmap2(imageProxy)

                                // Process the bitmap with person detector
                                val processedBitmap = personDetector.detectPersons(bitmap)

                                // Update the preview surface with the processed bitmap
                                ctx.mainExecutor.execute {
                                    // Note: In a real app, you would update a composable state here
                                    // For simplicity, we're just using the camera preview
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

                        // Try to use front camera
                        try {
                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            // Fall back to back camera if front camera is not available
                            Log.w("CameraPreview", "Front camera not available, trying back camera", e)
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", e)
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera initialization error", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun imageProxyToBitmap2(imageProxy: ImageProxy): Bitmap {
    val yBuffer = imageProxy.planes[0].buffer
    val uBuffer = imageProxy.planes[1].buffer
    val vBuffer = imageProxy.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

