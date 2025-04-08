package com.example.cobot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.LifecycleOwner
import androidx.room.util.copy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "PersonFollowingScreen"

@Composable
fun PersonFollowingScreen2() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedPosition by remember { mutableStateOf("Detecting...") }
    var boundingBox by remember { mutableStateOf<RectF?>(null) }
    var poseLandmarks by remember { mutableStateOf<List<SimpleLandmark>>(emptyList()) }

    // Create an executor for background tasks
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Remember the pose landmarker
    val poseLandmarker = remember { setupPoseLandmarker(context) }

    // Clean up when leaving the composition
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
            onPositionDetected = { position ->
                detectedPosition = position
            },
            onBoundingBoxUpdated = { box -> boundingBox = box },
            onLandmarksUpdated = { landmarks -> poseLandmarks = landmarks }


        )
        Canvas(modifier = Modifier
            .fillMaxSize()
            .align(Alignment.Center)
        ) {
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
                poseLandmarks?.forEach { landmark ->
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
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = "Position: $detectedPosition",
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
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
                scaleType = PreviewView.ScaleType.FILL_START
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Set up the image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, poseLandmarker, onPositionDetected, onBoundingBoxUpdated,onLandmarksUpdated)
                        }
                    }

                // Select front camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
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
        Log.e(TAG, "Error setting up pose landmarker: ${e.message}")
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
            // Convert the image to a bitmap
            val bitmap = mediaImageToBitmap(mediaImage)
            val rotatedBitmap = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())

            // Create an MPImage from the bitmap
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            // Process the image with MediaPipe
            poseLandmarker?.let { detector ->
                val result = detector.detect(mpImage)


                val flippedLandmarks = result.landmarks()[0].map {
                    SimpleLandmark(x = it.x(), y = it.y(), z = it.z())
                }
                onLandmarksUpdated(flippedLandmarks)
                determinePositionFromLandmarks(flippedLandmarks, onPositionDetected)
                val box = getBoundingBoxFromLandmarks(flippedLandmarks)
                onBoundingBoxUpdated(box)

                onBoundingBoxUpdated(box)
            }


        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
        }
    }
}
data class SimpleLandmark(val x: Float, val y: Float, val z: Float)
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
        centerX < 0.4f -> "LEFT"
        centerX > 0.6f -> "RIGHT"
        else -> "CENTER"
    }

    onPositionDetected(position)
}

//private fun getBoundingBox(result: PoseLandmarkerResult): RectF? {
//    if (result.landmarks().isEmpty()) return null
//
//    val original = result.landmarks()[0]
//
//    // Flip X for front camera mirror
//    val landmarks = original.map {
//        SimpleLandmark(
//            x = 1f - it.x(), // flip X axis
//            y = it.y(),
//            z = it.z()
//        )
//    }
//
//    val minX = landmarks.minOf { it.x }
//    val maxX = landmarks.maxOf { it.x }
//    val minY = landmarks.minOf { it.y }
//    val maxY = landmarks.maxOf { it.y }
//
//    return RectF(minX, minY, maxX, maxY)
//}
private fun getBoundingBoxFromLandmarks(landmarks: List<SimpleLandmark>): RectF {
    val minX = landmarks.minOf { it.x }
    val maxX = landmarks.maxOf { it.x }
    val minY = landmarks.minOf { it.y }
    val maxY = landmarks.maxOf { it.y }
    return RectF(minX, minY, maxX, maxY)
}




//private fun determinePosition(
//    result: PoseLandmarkerResult,
//    onPositionDetected: (String) -> Unit
//) {
//    if (result.landmarks().isEmpty()) return
//    val landmarks = result.landmarks()[0]
//
//    val leftShoulder = landmarks.getOrNull(11)
//    val rightShoulder = landmarks.getOrNull(12)
//
//    if (leftShoulder == null || rightShoulder == null) {
//        onPositionDetected("Not visible")
//        return
//    }
//
//    // Flip X (mirrored camera)
//    val centerX = (leftShoulder.x() + rightShoulder.x()) / 2f
//
//
//    val position = when {
//        centerX < 0.4f -> "LEFT"
//        centerX > 0.6f -> "RIGHT"
//        else -> "CENTER"
//    }
//
//    onPositionDetected(position)
//}

fun estimateDistance(boundingBox: RectF): Float {
    val boxHeight = boundingBox.height()
    return 1f / boxHeight // The taller the box → closer the person
}

// Helper functions for image processing
private fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    // Implementation depends on your specific needs
    // This is a simplified version - you might need a more robust implementation
    val planes = mediaImage.planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(
        nv21, ImageFormat.NV21,
        mediaImage.width, mediaImage.height, null
    )
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        Rect(0, 0, yuvImage.width, yuvImage.height),
        100, out
    )
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotation)
    // Mirror for front camera
    matrix.postScale(-1f, 1f)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

