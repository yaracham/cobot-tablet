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
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
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
//import androidx.camera.core.ImageProxy
//import java.io.ByteArrayOutputStream
//import androidx.core.graphics.scale
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.unit.dp
//import androidx.compose.foundation.Canvas
//import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
//import androidx.compose.ui.graphics.Paint
//import androidx.compose.ui.graphics.nativeCanvas
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.zIndex
//
//@RequiresApi(Build.VERSION_CODES.P)
//@Composable
//fun PersonDetectorScreen() {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//
//    // State for initialization
//    var isInitialized by remember { mutableStateOf(false) }
//    var initError by remember { mutableStateOf<String?>(null) }
//
//    // Create PersonDetector instance with error handling
//    val personDetector = remember {
//        try {
//            val detector = PersonDetector(context)
//            isInitialized = true
//            detector
//        } catch (e: Exception) {
//            Log.e("PersonDetectorScreen", "Failed to initialize PersonDetector", e)
//            initError = e.message ?: "Unknown error initializing person detector"
//            null
//        }
//    }
//
//    // Camera executor
//    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
//
//    // Clean up resources when the composable is disposed
//    DisposableEffect(Unit) {
//        onDispose {
//            cameraExecutor.shutdown()
//            personDetector?.release()
//        }
//    }
//
//    // Show error if initialization failed
//    if (initError != null) {
//        Box(
//            modifier = Modifier.fillMaxSize(),
//            contentAlignment = Alignment.Center
//        ) {
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
//            ) {
//                Text("Error initializing person detector:")
//                Text(initError ?: "Unknown error")
//                Text("Check logs for details")
//            }
//        }
//        return
//    }
//
//    // Show loading until initialized
//    if (!isInitialized || personDetector == null) {
//        Box(
//            modifier = Modifier.fillMaxSize(),
//            contentAlignment = Alignment.Center
//        ) {
//            CircularProgressIndicator()
//        }
//        return
//    }
//
//    // Full screen camera preview with person detection
//    Box(
//        modifier = Modifier.fillMaxSize()
//    ) {
//        CameraPreviewWithDetection(
//            context = context,
//            lifecycleOwner = lifecycleOwner,
//            cameraExecutor = cameraExecutor,
//            personDetector = personDetector
//        )
//    }
//}
//
//@RequiresApi(Build.VERSION_CODES.P)
//@Composable
//fun CameraPreviewWithDetection(
//    context: Context,
//    lifecycleOwner: LifecycleOwner,
//    cameraExecutor: ExecutorService,
//    personDetector: PersonDetector
//) {
//    // Keeps the detected person's position (LEFT/CENTER/RIGHT)
//    val personPosition = remember { mutableStateOf("Detecting...") }
//
//    // Keeps the current list of detections (for drawing)
//    val detectionsState = remember { mutableStateOf<List<PersonDetector.Detection>>(emptyList()) }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//
//        // Live Camera Preview
//        AndroidView(
//            factory = { ctx ->
//                val previewView = PreviewView(ctx).apply {
//                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//                }
//
//                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
//                cameraProviderFuture.addListener({
//                    val cameraProvider = cameraProviderFuture.get()
//
//                    val preview = Preview.Builder().build().also {
//                        it.setSurfaceProvider(previewView.surfaceProvider)
//                    }
//
//                    val imageAnalysis = ImageAnalysis.Builder()
//                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                        .build()
//
//                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
//                        try {
//                            val bitmap = imageProxyToBitmap2(imageProxy)
//
//                            val resized = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
//                            val inputBuffer = personDetector.prepareInputBuffer(resized)
//                            val outputBuffer = personDetector.runInference(inputBuffer)
//                            val detections = personDetector.processOutput(outputBuffer)
//                                .filter { it.classId == 0 } // only persons
//
//                            // Update detected positions for canvas
//                            detectionsState.value = detections
//
//                            // Update text label
//                            val person = detections.firstOrNull()
//                            person?.let {
//                                val centerX = (it.boundingBox.left + it.boundingBox.right) / 2
//                                personPosition.value = when {
//                                    centerX < 0.33f -> "LEFT"
//                                    centerX > 0.66f -> "RIGHT"
//                                    else -> "CENTER"
//                                }
//                            } ?: run {
//                                personPosition.value = "NO PERSON"
//                            }
//
//                        } catch (e: Exception) {
//                            Log.e("CameraPreview", "Detection error", e)
//                        } finally {
//                            imageProxy.close()
//                        }
//                    }
//
//                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//                    cameraProvider.unbindAll()
//                    cameraProvider.bindToLifecycle(
//                        lifecycleOwner,
//                        cameraSelector,
//                        preview,
//                        imageAnalysis
//                    )
//                }, ContextCompat.getMainExecutor(ctx))
//
//                previewView
//            },
//            modifier = Modifier.fillMaxSize()
//        )
//
//        // Position label
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(top = 32.dp),
//            contentAlignment = Alignment.TopCenter
//        ) {
//            Text(
//                text = personPosition.value,
//                color = Color.White,
//                fontSize = 28.sp
//            )
//        }
//
//        // Canvas Overlay: Red vertical line + Green dot at person center
//        Canvas(
//            modifier = Modifier
//                .fillMaxSize()
//                .zIndex(1f)
//        ) {
//            val paintGreen = android.graphics.Paint().apply {
//                color = android.graphics.Color.GREEN
//                style = android.graphics.Paint.Style.FILL
//            }
//
//            val paintRed = android.graphics.Paint().apply {
//                color = android.graphics.Color.RED
//                strokeWidth = 4f
//            }
//
//            // Vertical red line (middle of the screen)
//            drawContext.canvas.nativeCanvas.drawLine(
//                size.width / 2, 0f,
//                size.width / 2, size.height,
//                paintRed
//            )
//
//            // Green dot: person center
//            val person = detectionsState.value.firstOrNull()
//            person?.let {
//                val cx = it.boundingBox.centerX() * size.width
//                val cy = it.boundingBox.centerY() * size.height
//
//                drawContext.canvas.nativeCanvas.drawCircle(cx, cy, 10f, paintGreen)
//            }
//        }
//    }
//}
//
//
//
//fun imageProxyToBitmap2(imageProxy: ImageProxy): Bitmap {
//    val yBuffer = imageProxy.planes[0].buffer
//    val uBuffer = imageProxy.planes[1].buffer
//    val vBuffer = imageProxy.planes[2].buffer
//
//    val ySize = yBuffer.remaining()
//    val uSize = uBuffer.remaining()
//    val vSize = vBuffer.remaining()
//
//    val nv21 = ByteArray(ySize + uSize + vSize)
//
//    yBuffer.get(nv21, 0, ySize)
//    vBuffer.get(nv21, ySize, vSize)
//    uBuffer.get(nv21, ySize + vSize, uSize)
//
//    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
//    val out = ByteArrayOutputStream()
//    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
//    val imageBytes = out.toByteArray()
//
//    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//}
//
