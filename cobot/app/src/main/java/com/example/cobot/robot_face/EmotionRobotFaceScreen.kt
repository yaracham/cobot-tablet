package com.example.cobot.robot_face

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.cobot.R
import com.example.cobot.bluetooth.BluetoothConnectionState
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.emotion_detection.CameraPreview
import com.example.cobot.emotion_detection.classifyEmotionFromBlendshapes
import com.example.cobot.emotion_detection.createFaceLandmarker
import com.example.cobot.emotion_detection.processFaceWithLandmarker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@RequiresApi(value = 31)
@Composable
fun RobotFaceEmotionDemo(hM10BluetoothHelper: HM10BluetoothHelper) {
//    val bluetoothState by bluetoothManager.bluetoothState
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by hM10BluetoothHelper.connectionState

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
//    LaunchedEffect(Unit) {
//        bluetoothManager.getPairedDevices(context)
//        bluetoothManager.pairedDevices.collect { devices ->
//            val hcDevice = devices.find { it.name?.contains("HC-06") == true }
//            if (hcDevice != null &&
//                !bluetoothManager.bluetoothState.value.isConnected &&
//                !bluetoothManager.bluetoothState.value.isConnecting
//            ) {
//                bluetoothManager.connectToDevice(hcDevice, context)
//            }
//        }
//    }
//    LaunchedEffect(bluetoothState.isConnected) {
//        if (bluetoothState.isConnected) {
//            emotionOverride = Emotion.HAPPY
//            delay(1000) // 1 second delay
//            emotionOverride = null // go back to detected emotion
//        }
//    }
    LaunchedEffect(detectedEmotion) {
        while (true) {
            val command = when (detectedEmotion) {
                "Happy" -> "EA\r\n"
                "Sad" -> "ES\r\n"
                "Surprised" -> "EU\r\n"
                "Angry" -> "EY\r\n"
                else -> ""
            }
            hM10BluetoothHelper.sendMessage(command)
            delay(5000) // Send every 5 seconds
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
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

                            detectedEmotion = classifyEmotionFromBlendshapes(
                                faceBlendshapes,
                                debugText = mutableStateOf("")
                            )
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
            emotionOverride != null -> emotionOverride!!
            detectedEmotion.equals("Happy", ignoreCase = true) -> Emotion.HAPPY
            detectedEmotion.equals("Surprised", ignoreCase = true) -> Emotion.SURPRISED
            detectedEmotion.equals("sleeping", ignoreCase = true) -> Emotion.SLEEPING
            detectedEmotion.equals("Angry", ignoreCase = true) -> Emotion.ANGRY
            detectedEmotion.equals("Sad", ignoreCase = true) -> Emotion.SAD

            else -> Emotion.NEUTRAL
        }

        RobotFace(emotion = finalEmotion)
        if (state == BluetoothConnectionState.Connected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_bluetooth_connected_24),
                    contentDescription = "bluetooth connected",
                    tint = Color.Blue
                )
            }
        } else if (state is BluetoothConnectionState.Error || state == BluetoothConnectionState.Disconnected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable { }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_bluetooth_disabled_24),
                    contentDescription = "bluetooth disconnected",
                    tint = Color.Blue
                )
            }
        }

    }
}

//fun getRobotEmotion(bluetoothState: BluetoothState, detectedEmotion: String): Emotion {
//    return when {
//        bluetoothState.isConnecting -> Emotion.CONNECTING
//        bluetoothState.isConnected -> Emotion.HAPPY
//        detectedEmotion.equals("Surprised", ignoreCase = true) -> Emotion.SURPRISED
//        detectedEmotion.equals("sleeping", ignoreCase = true) -> Emotion.SLEEPING
//        else -> Emotion.NEUTRAL
//    }
//}
