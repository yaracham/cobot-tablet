package com.example.cobot.emotion_detection

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.example.cobot.bluetooth.BluetoothManager
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import kotlinx.coroutines.delay
import java.util.concurrent.Executors


@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun LiveEmotionDetectionScreen(bluetoothManager: BluetoothManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val debugText = remember { mutableStateOf("") }
    var lastEmotionSent by remember { mutableStateOf("") }


    // State initialization
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    // Initialize face landmarker
    val faceLandmarker = remember { createFaceLandmarker(context) }
    val emotionCommandMap = mapOf(
        "Happy" to "HA\r\n",
        "Angry" to "AN\r\n",
        "Sad" to "SD\r\n",
        "Surprised" to "SP\r\n"
    )

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    val command = emotionCommandMap[detectedEmotion]
                    if (command != null && detectedEmotion != lastEmotionSent) {
                        bluetoothManager.sendCommand(command)
                        lastEmotionSent = detectedEmotion
                        Log.d("BluetoothCommand", "Sent: $command")
                    }

                    val faceBlendshapes: List<List<Category>>? = result.faceBlendshapes().orElse(null)

                    if (faceBlendshapes != null) {

                        // Debug log to verify content
                        Log.d("Blendshapes", faceBlendshapes.toString())

                            detectedEmotion = classifyEmotionFromBlendshapes(faceBlendshapes, debugText)
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
        Text(
            text = "Emotion: $detectedEmotion",
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = "$debugText",
            modifier = Modifier
                .padding(start = 16.dp, top = 80.dp)
        )

    }
}
