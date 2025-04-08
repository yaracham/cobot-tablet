package com.example.cobot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "PersonFollowingScreen"

data class SimpleLandmark(val x: Float, val y: Float, val z: Float)

@Composable
fun PersonFollowingScreen2() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedPosition by remember { mutableStateOf("Detecting...") }
    var boundingBox by remember { mutableStateOf<RectF?>(null) }
    var poseLandmarks by remember { mutableStateOf<List<SimpleLandmark>>(emptyList()) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseLandmarker = remember { setupPoseLandmarker(context) }

    var estimatedDistance by remember { mutableStateOf("Estimating...") }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseLandmarker?.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            poseLandmarker = poseLandmarker,
            onPositionDetected = { detectedPosition = it },
            onBoundingBoxUpdated = { box ->
                boundingBox = box
                estimatedDistance = box?.let { estimateDistance(it) } ?: "Estimating..."
            },

            onLandmarksUpdated = { poseLandmarks = it }
        )

        // Bounding box & landmarks overlay
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
            poseLandmarks.forEach { landmark ->
                drawCircle(
                    color = Color.Green,
                    radius = 6f,
                    center = Offset(
                        x = landmark.x * size.width,
                        y = landmark.y * size.height
                    )
                )
            }
        }

        // Animated eyes overlay
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

        // Position indicator
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
    val context = LocalContext.current

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
                    it.setSurfaceProvider(previewView.surfaceProvider)
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

private fun setupPoseLandmarker(context: Context): PoseLandmarker? {
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
        Log.e(TAG, "PoseLandmarker setup error: ${e.message}")
        null
    }
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

private fun determinePositionFromLandmarks(
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
        centerX < 0.4f -> "RIGHT"
        centerX > 0.6f -> "LEFT"
        else -> "CENTER"
    }
    onPositionDetected(position)
}

private fun getBoundingBoxFromLandmarks(landmarks: List<SimpleLandmark>): RectF {
    val focusedIndices = listOf(0, 11, 12, 23, 24) // Nose, L/R shoulders, L/R hips
    val points = landmarks.filterIndexed { index, _ -> index in focusedIndices }

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }

    return RectF(minX, minY, maxX, maxY)
}

private fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    val yBuffer = mediaImage.planes[0].buffer
    val uBuffer = mediaImage.planes[1].buffer
    val vBuffer = mediaImage.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotation)
    matrix.postScale(-1f, 1f) // Mirror for front camera
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
