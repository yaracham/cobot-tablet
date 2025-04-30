package com.example.cobot.robot_face

import android.annotation.SuppressLint
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
import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class Emotion {
    NEUTRAL,
    HAPPY,
    SAD,
    ANGRY,
    SLEEPING,
    SURPRISED,
}

@SuppressLint("RememberReturnType")
@RequiresApi(value = 31)
@Composable
fun EmotionRobotFaceScreen(hM10BluetoothHelper: HM10BluetoothHelper) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by hM10BluetoothHelper.connectionState
    var lastEmotionChangeTimestamp by remember { mutableStateOf(0L) }
    var emotionLockDuration by remember { mutableStateOf(0L) }

    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    val faceLandmarker = remember { createFaceLandmarker(context) }
    var lastNeutralTimestamp by remember { mutableStateOf<Long?>(null) }

    var emotionOverride by remember { mutableStateOf<Emotion?>(null) }

    val gestureRecognizerHelper = remember {
        GestureRecognizerHelper(
            context = context,
            currentDelegate = GestureRecognizerHelper.DELEGATE_CPU,
            runningMode = RunningMode.LIVE_STREAM,
            gestureRecognizerListener = object : GestureRecognizerHelper.GestureRecognizerListener {
                var gestureLock = false

                override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
                    val gestures = resultBundle.results.firstOrNull()?.gestures() ?: return
                    for (gestureGroup in gestures) {
                        val detected = gestureGroup.firstOrNull()?.categoryName()
                        Log.d("Gesture", "Detected gesture: $detected")
                        if (detected.equals("Open_Palm", ignoreCase = true) && !gestureLock) {
                            gestureLock = true
                            hM10BluetoothHelper.sendMessage("H\r\n")
                            Log.d("Gesture", "Sent H over Bluetooth for Open_Palm")

                            // Reset after 5 seconds
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(5000)
                                gestureLock = false
                            }
                        }
                    }
                }

                override fun onError(error: String, errorCode: Int) {
                    Log.e("Gesture", "Error: $error")
                }
            }
        )
    }

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
        while (true) {
            if (emotionOverride == null && detectedEmotion != "Neutral" && detectedEmotion != "Detecting...") {
                val duration = when (detectedEmotion.lowercase()) {
                    "happy" -> 7000L
                    "sad" -> 10000L
                    "angry" -> 10000L
                    "surprised" -> 5000L
                    else -> 0L
                }

                emotionOverride = when (detectedEmotion.lowercase()) {
                    "happy" -> Emotion.HAPPY
                    "sad" -> Emotion.SAD
                    "angry" -> Emotion.ANGRY
                    "surprised" -> Emotion.SURPRISED
                    else -> null
                }

                // Send Bluetooth command only once
                val command = when (detectedEmotion.lowercase()) {
                    "happy" -> "EA\r\n"
                    "sad" -> "ES\r\n"
                    "surprised" -> "EU\r\n"
                    "angry" -> "EY\r\n"
                    else -> ""
                }

                if (command.isNotEmpty()) {
                    hM10BluetoothHelper.sendMessage(command)
                }

                // Wait the emotion duration
                delay(duration)

                // Reset to neutral
                emotionOverride = Emotion.NEUTRAL
                detectedEmotion = "Neutral"
            }

            delay(500)
            emotionOverride = null// small polling interval
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
            gestureRecognizerHelper.clearGestureRecognizer()
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
                    gestureRecognizerHelper.recognizeAsync(mpImage, System.currentTimeMillis())

                    // Classify emotion based on blendshapes
                    landmarkerResult?.let { result ->
                        val faceBlendshapes: List<List<Category>>? =
                            result.faceBlendshapes().orElse(null)

                        if (faceBlendshapes != null) {
                            // Debug log to verify content
                            Log.d("Blendshapes", faceBlendshapes.toString())

                            if (emotionOverride == null) {
                                detectedEmotion = classifyEmotionFromBlendshapes(
                                    faceBlendshapes,
                                    debugText = mutableStateOf("")
                                )
                            }
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
                    .padding(top = 32.dp, end = 16.dp)
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
                    .padding(top = 36.dp, end = 16.dp)
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
