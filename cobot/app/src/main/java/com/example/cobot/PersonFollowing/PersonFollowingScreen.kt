package com.example.cobot.PersonFollowing

import android.graphics.RectF
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cobot.EyesAnimation
import java.util.concurrent.Executors

@Composable
fun PersonFollowingScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseLandmarker = remember { setupPoseLandmarker(context) }

    var poseLandmarks by remember { mutableStateOf<List<SimpleLandmark>>(emptyList()) }
    var boundingBox by remember { mutableStateOf<RectF?>(null) }
    var estimatedDistance by remember { mutableStateOf("Estimating...") }
    var detectedPosition by remember { mutableStateOf("Detecting...") }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseLandmarker?.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Camera preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                processImageProxy(
                                    imageProxy,
                                    poseLandmarker,
                                    onPositionDetected = { detectedPosition = it },
                                    onBoundingBoxUpdated = { box ->
                                        boundingBox = box
                                        estimatedDistance = box?.let { estimateDistance(it) } ?: "Estimating..."
                                    },
                                    onLandmarksUpdated = { poseLandmarks = it }
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
                        e.printStackTrace()
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Canvas for landmarks and bounding box
        Canvas(modifier = Modifier.fillMaxSize().align(Alignment.Center)) {
            boundingBox?.let { box ->
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(box.left * size.width, box.top * size.height),
                    size = Size(
                        (box.right - box.left) * size.width,
                        (box.bottom - box.top) * size.height
                    ),
                    style = Stroke(width = 4f)
                )
            }

            poseLandmarks.forEach {
                drawCircle(
                    color = Color.Green,
                    radius = 6f,
                    center = Offset(it.x * size.width, it.y * size.height)
                )
            }
        }

        // Eyes Animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        ) {
            EyesAnimation(
                position = detectedPosition,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        // Info card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = "Position: $detectedPosition",
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Distance: $estimatedDistance",
                modifier = Modifier.padding(4.dp).fillMaxWidth(),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color.Black
            )
        }
    }
}
