package com.example.cobot.robot_face

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
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
import com.example.cobot.R
import com.example.cobot.bluetooth.BluetoothConnectionState
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.emotion_detection.CameraPreview
import com.example.cobot.emotion_detection.classifyEmotionFromBlendshapes
import com.example.cobot.emotion_detection.createFaceLandmarker
import com.example.cobot.emotion_detection.processFaceWithLandmarker
import com.example.cobot.gesture_detection.GestureRecognizerHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.delay
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
@RequiresApi(31)
@Composable
fun EmotionRobotFaceScreen(
    hM10BluetoothHelper: HM10BluetoothHelper,
    onRequestConnect: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by hM10BluetoothHelper.connectionState

    var detectedGesture by remember { mutableStateOf("Detecting gesture...") }
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    var lastNeutralTimestamp by remember { mutableStateOf<Long?>(null) }
    var emotionOverride by remember { mutableStateOf<Emotion?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    val faceLandmarker = remember { createFaceLandmarker(context) }
    val gestureHelper = remember {
        GestureRecognizerHelper(
            runningMode = RunningMode.IMAGE,
            context = context
        ).apply {
            minHandDetectionConfidence = 0.3f
            setupGestureRecognizer()
        }
    }

    @Suppress("unused")
    LaunchedEffect(detectedEmotion) {
        if (detectedEmotion == "Neutral") {
            if (lastNeutralTimestamp == null) lastNeutralTimestamp = System.currentTimeMillis()
            else if (System.currentTimeMillis() - lastNeutralTimestamp!! > 10_000) {
                detectedEmotion = "sleeping"
                lastNeutralTimestamp = null
            }
        } else {
            lastNeutralTimestamp = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (emotionOverride == null && detectedEmotion !in listOf("neutral", "detecting")) {
                val dur = when (detectedEmotion.lowercase()) {
                    "happy" -> 7_000L
                    "sad" -> 10_000L
                    "angry" -> 10_000L
                    "surprised" -> 5_000L
                    else -> 0L
                }
                val cmd = when (detectedEmotion.lowercase()) {
                    "happy" -> "EA\r\n"
                    "sad" -> "ES\r\n"
                    "surprised" -> "EU\r\n"
                    "angry" -> "EY\r\n"
                    else -> ""
                }
                emotionOverride = when (detectedEmotion.lowercase()) {
                    "happy" -> Emotion.HAPPY
                    "sad" -> Emotion.SAD
                    "angry" -> Emotion.ANGRY
                    "surprised" -> Emotion.SURPRISED
                    else -> Emotion.NEUTRAL
                }
                if (cmd.isNotEmpty()) hM10BluetoothHelper.sendMessage(cmd)
                Log.d("EMOTIONCMD", cmd)
                delay(dur)
                // clear override so live detection resumes
                emotionOverride = null
            }
            delay(500)
        }
    }


    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            captureFrame++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceLandmarker?.close()
            gestureHelper.clearGestureRecognizer()
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CameraPreview(
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            captureFrame = captureFrame
        ) { bitmap ->
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val landmarkerResult = processFaceWithLandmarker(faceLandmarker, mpImage)
                landmarkerResult?.let { result ->
                    val blendshapes: List<List<Category>>? = result.faceBlendshapes().orElse(null)
                    if (blendshapes != null) {
                        if (emotionOverride == null) {
                            detectedEmotion =
                                classifyEmotionFromBlendshapes(blendshapes, mutableStateOf(""))
                        }
                        Log.d("EmotionDetection", "Detected: $detectedEmotion")
                    } else {
                        detectedEmotion = "No face detected"
                    }
                } ?: run {
                    detectedEmotion = "Detection failed"
                }

                val matrix = Matrix().apply {
                    postRotate(270f)
                    postScale(-1f, 1f, bitmap.width.toFloat(), bitmap.height.toFloat())
                }
                val corrected: Bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                val gestureBundle = gestureHelper.recognizeImage(corrected)
                detectedGesture = gestureBundle
                    ?.results
                    ?.firstOrNull()
                    ?.gestures()
                    ?.flatten()
                    ?.firstOrNull()
                    ?.categoryName()
                    ?: "None"
                Log.d("GestureDetection", "Detected: $detectedGesture")

            } catch (e: Exception) {
                Log.e("LiveDetection", "Error: ${e.localizedMessage}", e)
                detectedEmotion = "Error"
                detectedGesture = "Error"
            } finally {
                bitmap.recycle()
            }
        }

        LaunchedEffect(detectedGesture) {
            if (detectedGesture.equals("Open_Palm", ignoreCase = true)) {
                hM10BluetoothHelper.sendMessage("HH\r\n")
            }
        }

        val finalEmotion = when {
            emotionOverride != null -> emotionOverride!!
            detectedEmotion.equals("Happy", true) -> Emotion.HAPPY
            detectedEmotion.equals("Surprised", true) -> Emotion.SURPRISED
            detectedEmotion.equals("sleeping", true) -> Emotion.SLEEPING
            detectedEmotion.equals("Angry", true) -> Emotion.ANGRY
            detectedEmotion.equals("Sad", true) -> Emotion.SAD
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
                    contentDescription = null,
                    tint = Color.Blue
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 16.dp)
                    .clickable { onRequestConnect() }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_bluetooth_disabled_24),
                    contentDescription = null,
                    tint = Color.Blue
                )
            }
        }
    }
}
