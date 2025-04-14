package com.example.cobot.PersonFollowing

import android.graphics.RectF
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
import com.example.cobot.bluetooth.BluetoothManager
import com.example.cobot.EyesAnimation
import com.example.cobot.bluetooth.HM10BluetoothHelper
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private const val TAG = "PersonFollowingScreen"

@Composable
fun PersonFollowingScreen(hM10BluetoothHelper: HM10BluetoothHelper) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedPosition by remember { mutableStateOf("Detecting...") }
    var lastSentCommand by remember { mutableStateOf("") }

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
    LaunchedEffect(Unit) {
        while (true) {
            val command = when (detectedPosition) {
                "RIGHT" -> "RR\r\n"
                "LEFT" -> "LL\r\n"
                "CENTER" -> "FF\r\n"
                else -> "SS\r\n"
            }

//            bluetoothManager.sendCommand(command)
//            delay(1000) // Send every 2 seconds
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