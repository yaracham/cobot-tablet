package com.example.cobot.emotion_detection

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.gesture_detection.GestureRecognizerHelper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun LiveEmotionDetectionScreen(
    hM10BluetoothHelper: HM10BluetoothHelper
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for emotions and gestures
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    var debugText by remember { mutableStateOf("") }
    var detectedGesture by remember { mutableStateOf("Detecting gesture...") }
    var lastEmotionSent by remember { mutableStateOf("") }

    // Executors and frame counter
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureFrame by remember { mutableIntStateOf(0) }

    // Initialize face landmarker (existing function)
    val faceLandmarker = remember { createFaceLandmarker(context) }

    // Initialize gesture recognizer helper in IMAGE mode
    val gestureHelper = remember {
        GestureRecognizerHelper(
            runningMode = RunningMode.IMAGE,
            context = context
        ).apply {
            // lower threshold for initial testing
            minHandDetectionConfidence = 0.3f
            setupGestureRecognizer()
        }
    }

    // Emotion to Bluetooth command map
    val emotionCommandMap = mapOf(
        "Happy" to "EA\r\n",
        "Angry" to "EY\r\n",
        "Sad" to "ES\r\n",
        "Surprised" to "EU\r\n"
    )

    // Frame trigger loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            captureFrame++
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            faceLandmarker?.close()
            gestureHelper.clearGestureRecognizer()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                captureFrame = captureFrame
            ) { bitmap ->
                try {
                    // ---- Face detection & emotion classification ----
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val landmarkerResult = processFaceWithLandmarker(faceLandmarker, mpImage)
                    landmarkerResult?.faceBlendshapes()?.orElse(null)?.let { blendshapes ->
                        detectedEmotion = classifyEmotionFromBlendshapes(blendshapes, debugText = mutableStateOf(""))
                        // send via Bluetooth
                        if (detectedEmotion != "Neutral" && detectedEmotion != lastEmotionSent) {
                            emotionCommandMap[detectedEmotion]?.let { cmd ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    hM10BluetoothHelper.sendMessage(cmd)
                                }
                                lastEmotionSent = detectedEmotion
                                Log.d("BluetoothCommand", "Sent: $cmd")
                            }
                        } else if (detectedEmotion == "Neutral") {
                            lastEmotionSent = ""
                        } else {

                        }
                    } ?: run {
                        detectedEmotion = "No face detected"
                    }

                    // ---- Gesture detection: rotate & mirror -> MPImage ----
                    val matrix = Matrix().apply {
                        // rotate to match model expectation
                        postRotate(270f)
                        // mirror for front-camera
                        postScale(-1f, 1f, bitmap.width.toFloat(), bitmap.height.toFloat())
                    }
                    val corrected = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    val gestureMpImage = BitmapImageBuilder(corrected).build()

                    // run recognition
                    val gestureBundle = gestureHelper.recognizeImage(corrected)
                    Log.d("BundleGesture", gestureBundle.toString())
                    detectedGesture = gestureBundle
                        ?.results
                        ?.firstOrNull()
                        ?.gestures()
                        ?.flatten()
                        ?.firstOrNull()
                        ?.categoryName()
                        ?: "None"
                    Log.d("GestureDetection", "Detected gesture: $detectedGesture")

                } catch (e: Exception) {
                    Log.e("LiveDetection", "Error processing frame", e)
                    detectedEmotion = "Error: ${e.message?.take(20)}..."
                    detectedGesture = "Error"
                } finally {
                    bitmap.recycle()
                }
            }

            // Display results
            Text(
                text = "Emotion: $detectedEmotion",
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "Gesture: $detectedGesture",
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = debugText,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
        }
    }
}
