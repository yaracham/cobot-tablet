//package com.example.cobot
//
//import android.content.Context
//import android.graphics.*
//import android.os.Build
//import android.util.Log
//import androidx.annotation.RequiresApi
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LifecycleOwner
//import kotlinx.coroutines.delay
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import android.graphics.ImageFormat
//import android.graphics.Rect
//import android.graphics.YuvImage
//import android.graphics.BitmapFactory
//import android.util.Size
//import androidx.camera.core.ImageProxy
//import java.io.ByteArrayOutputStream
//import androidx.compose.ui.graphics.Color
//import androidx.compose.runtime.DisposableEffect
//
//@RequiresApi(Build.VERSION_CODES.P)
//@Composable
//fun PersonFollowerScreen() {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//
//    // State for initialization
//    var isInitialized by remember { mutableStateOf(false) }
//    var initError by remember { mutableStateOf<String?>(null) }
//
//    // Create PersonFollower instance with proper error handling
//    val personFollower = remember {
//        try {
//            PersonFollower(context).also { isInitialized = true }
//        } catch (e: Exception) {
//            initError = "Failed to initialize: ${e.localizedMessage}"
//            Log.e("PersonFollower", "Initialization failed", e)
//            null
//        }
//    }
//
//    // State for processed image
//    var processedImage by remember { mutableStateOf<Bitmap?>(null) }
//
//    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
//
//    DisposableEffect(Unit) {
//        onDispose {
//            cameraExecutor.shutdown()
//            personFollower?.release()
//        }
//    }
//
//    // Error display
//    if (initError != null) {
//        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                Text("Error:", color = Color.Red)
//                Text(initError!!)
//            }
//        }
//        return
//    }
//
//    // Loading state
//    if (!isInitialized) {
//        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//            CircularProgressIndicator()
//        }
//        return
//    }
//
//    // Main camera view
//    Box(modifier = Modifier.fillMaxSize()) {
//        AndroidView(
//            factory = { ctx ->
//                val previewView = PreviewView(ctx).apply {
//                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//                    scaleType = PreviewView.ScaleType.FILL_CENTER
//                }
//
//                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
//                cameraProviderFuture.addListener({
//                    try {
//                        val cameraProvider = cameraProviderFuture.get()
//                        val preview = Preview.Builder().build().apply {
//                            setSurfaceProvider(previewView.surfaceProvider)
//                        }
//
//                        val imageAnalysis = ImageAnalysis.Builder()
//                            .setTargetResolution(Size(640, 480)) // Lower resolution for better performance
//                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                            .build()
//
//                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
//                            try {
//                                val bitmap = imageProxy.toBitmap(context)
//                                personFollower?.processFrame(bitmap)?.let { processed ->
//                                    processedImage = processed
//                                }
//                            } catch (e: Exception) {
//                                Log.e("CameraAnalysis", "Frame processing failed", e)
//                            } finally {
//                                imageProxy.close()
//                            }
//                        }
//
//                        cameraProvider.unbindAll()
//                        cameraProvider.bindToLifecycle(
//                            lifecycleOwner,
//                            CameraSelector.DEFAULT_FRONT_CAMERA,
//                            preview,
//                            imageAnalysis
//                        )
//                    } catch (e: Exception) {
//                        initError = "Camera setup failed: ${e.localizedMessage}"
//                        Log.e("CameraSetup", "Camera initialization failed", e)
//                    }
//                }, ContextCompat.getMainExecutor(ctx))
//
//                previewView
//            },
//            modifier = Modifier.fillMaxSize()
//        )
//
//        processedImage?.let { bitmap ->
//            Image(
//                bitmap = bitmap.asImageBitmap(),
//                contentDescription = "Person Detection",
//                modifier = Modifier.fillMaxSize()
//            )
//        }
//    }
//}
//
//@RequiresApi(Build.VERSION_CODES.P)
//private fun ImageProxy.toBitmap(context: Context): Bitmap {
//    val plane = planes[0]
//    val buffer = plane.buffer
//    val pixelStride = plane.pixelStride
//    val rowStride = plane.rowStride
//    val rowPadding = rowStride - pixelStride * width
//
//    // Create bitmap
//    val bitmap = Bitmap.createBitmap(
//        width + rowPadding / pixelStride,
//        height,
//        Bitmap.Config.ARGB_8888
//    ).apply {
//        copyPixelsFromBuffer(buffer)
//    }
//
//    // Create matrix for front camera transformation
//    return Bitmap.createBitmap(
//        bitmap,
//        0, 0,
//        bitmap.width,
//        bitmap.height,
//        Matrix().apply {
//            // Mirror effect for front camera
//            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
//            // Correct rotation
//            postRotate(imageInfo.rotationDegrees.toFloat())
//        },
//        true
//    )
//}
