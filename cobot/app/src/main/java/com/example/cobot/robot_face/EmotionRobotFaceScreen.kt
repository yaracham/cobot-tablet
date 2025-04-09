package com.example.cobot.robot_face

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import com.example.cobot.emotion_detection.CameraPreview
import com.example.cobot.emotion_detection.classifyEmotionFromBlendshapes
import com.example.cobot.emotion_detection.createFaceLandmarker
import com.example.cobot.emotion_detection.processFaceWithLandmarker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun RobotFaceEmotionDemo() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State initialization
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    // Initialize face landmarker
    val faceLandmarker = remember { createFaceLandmarker(context) }
    var lastNeutralTimestamp by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(detectedEmotion) {
        Log.d("EMOTION", detectedEmotion)
        if (detectedEmotion == "Neutral") {
            if (lastNeutralTimestamp == null) {
                lastNeutralTimestamp = System.currentTimeMillis()
            } else {
                val elapsed = System.currentTimeMillis() - lastNeutralTimestamp!!
                if (elapsed > 10_000) {
                    detectedEmotion = "sleeping"
                    lastNeutralTimestamp = null // reset timer after sleeping
                }
            }
        } else {
            lastNeutralTimestamp = null // reset if emotion changes
        }
    }
    // Frame processing loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            captureFrame++
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            faceLandmarker?.close()
            cameraExecutor.shutdown()
        }
    }

    // Main container with black background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview with zero zIndex to hide it behind the robot face
        // We still need it for emotion detection
        Box(modifier = Modifier.zIndex(-1f)) {
            CameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                captureFrame = captureFrame
            ) { bitmap ->
                try {
                    val mpImage = BitmapImageBuilder(bitmap).build()

                    // Process the image with face landmarker
                    val landmarkerResult = processFaceWithLandmarker(faceLandmarker, mpImage)

                    // Classify emotion based on blendshapes
                    landmarkerResult?.let { result ->
                        val faceBlendshapes: List<List<Category>>? =
                            result.faceBlendshapes().orElse(null)

                        if (faceBlendshapes != null) {
                            // Debug log to verify content
                            Log.d("Blendshapes", faceBlendshapes.toString())

                            detectedEmotion = classifyEmotionFromBlendshapes(faceBlendshapes)
                            Log.d("EmotionDetection", "Detected emotion: $detectedEmotion")
                        } else {
                            detectedEmotion = "No face detected"
                        }
                    } ?: run {
                        detectedEmotion = "Detection failed"
                    }
                } catch (e: Exception) {
                    detectedEmotion = "Error: ${e.message?.take(20)}..."
                    Log.e("EmotionDetection", "Frame processing error", e)
                } finally {
                    // Recycle the bitmap to free memory
                    bitmap.recycle()
                }
            }
        }

        RobotFace(
            emotion = if (detectedEmotion == "Happy") Emotion.HAPPY else if (detectedEmotion == "sleeping") Emotion.SLEEPING else if (detectedEmotion == "Surprised") Emotion.SURPRISED else Emotion.NEUTRAL,
        )
    }
}
