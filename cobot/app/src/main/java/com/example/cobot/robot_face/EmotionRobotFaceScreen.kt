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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.cobot.Bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.cobot.Bluetooth.BluetoothState

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun RobotFaceEmotionDemo(bluetoothManager: BluetoothManager) {
    val bluetoothState by bluetoothManager.bluetoothState
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State initialization
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    // Initialize face landmarker
    val faceLandmarker = remember { createFaceLandmarker(context) }
    var lastNeutralTimestamp by remember { mutableStateOf<Long?>(null) }

    var emotionOverride by remember { mutableStateOf<Emotion?>(null) }

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
    LaunchedEffect(Unit) {
        bluetoothManager.getPairedDevices(context)
        bluetoothManager.pairedDevices.collect { devices ->
            val hcDevice = devices.find { it.name?.contains("HC-06") == true }
            if (hcDevice != null &&
                !bluetoothManager.bluetoothState.value.isConnected &&
                !bluetoothManager.bluetoothState.value.isConnecting
            ) {
                bluetoothManager.connectToDevice(hcDevice, context)
            }
        }
    }
    LaunchedEffect(bluetoothState.isConnected) {
        if (bluetoothState.isConnected) {
            emotionOverride = Emotion.HAPPY
            delay(1000) // 1 second delay
            emotionOverride = null // go back to detected emotion
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
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            val hasBluetoothPermission = remember {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            }
            Text(
                text = when {
                    bluetoothState.isConnecting -> "🔄 Connecting to HC-06..."
                    bluetoothState.isConnected -> {
                        val name = if (hasBluetoothPermission) {
                            bluetoothState.connectedDevice?.name ?: "device"
                        } else {
                            "device"
                        }
                        "✅ Connected to $name"
                    }
                    else -> "❌ Not Connected"
                },
                color = Color.White
            )
        }
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

                            detectedEmotion = classifyEmotionFromBlendshapes(faceBlendshapes, debugText = mutableStateOf(""))
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
        val finalEmotion = when {
            bluetoothState.isConnecting -> Emotion.CONNECTING
            emotionOverride != null -> emotionOverride!!
            detectedEmotion.equals("Happy", ignoreCase = true) -> Emotion.HAPPY
            detectedEmotion.equals("Surprised", ignoreCase = true) -> Emotion.SURPRISED
            detectedEmotion.equals("sleeping", ignoreCase = true) -> Emotion.SLEEPING
            else -> Emotion.NEUTRAL
        }

        RobotFace(emotion = finalEmotion)
    }
}

fun getRobotEmotion(bluetoothState: BluetoothState, detectedEmotion: String): Emotion {
    return when {
        bluetoothState.isConnecting -> Emotion.CONNECTING
        bluetoothState.isConnected -> Emotion.HAPPY
        detectedEmotion.equals("Surprised", ignoreCase = true) -> Emotion.SURPRISED
        detectedEmotion.equals("sleeping", ignoreCase = true) -> Emotion.SLEEPING
        else -> Emotion.NEUTRAL
    }
}
