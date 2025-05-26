package com.example.cobot.robot_face
/**
 * AutomationRobotFaceScreen.kt
 *
 * This composable displays a robot face interface that reacts to human position detection
 * using pose landmarks from a hidden camera feed. It sends directional commands via Bluetooth
 * to a robot based on the detected position (LEFT, RIGHT, CENTER) and shows a UI overlay
 * with the current detection state. It also draws a dynamic robot face that follows movement.
 *
 * Requires: Android S (API 31) or higher due to Bluetooth permissions.
 *
 */

import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cobot.R
import com.example.cobot.automated_driving.CameraPreview
import com.example.cobot.automated_driving.estimateDistance
import com.example.cobot.automated_driving.setupPoseLandmarker
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.robot_face.faces.drawFollowingFace
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@RequiresApi(31)
@Composable
fun AutomationRobotFaceScreen(
    hM10BluetoothHelper: HM10BluetoothHelper,
    onShowRobotCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detection state (boundingBox still tracked internally)
    var detectedPosition by remember { mutableStateOf("Detecting...") }
    var boundingBox by remember { mutableStateOf<RectF?>(null) }
    var estimatedDistance by remember { mutableStateOf("Estimating...") }

    // Executors and detector
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseLandmarker = remember { setupPoseLandmarker(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseLandmarker?.close()
        }
    }

    // Send movement commands based on detected position
    LaunchedEffect(detectedPosition) {
        while (true) {
            val command = when (detectedPosition) {
                "RIGHT" -> "FR\r\n"
                "LEFT" -> "FL\r\n"
                "CENTER" -> "FF\r\n"
                else -> "SS\r\n"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hM10BluetoothHelper.sendMessage(command)
            }
            delay(1000)
        }
    }

    // Hidden camera preview for detection
    CameraPreview(
        modifier = Modifier.size(1.dp),
        lifecycleOwner = lifecycleOwner,
        cameraExecutor = cameraExecutor,
        poseLandmarker = poseLandmarker,
        onPositionDetected = { detectedPosition = it },
        onBoundingBoxUpdated = { box ->
            boundingBox = box
            estimatedDistance = box?.let { estimateDistance(it) } ?: "Estimating..."
        },
        onLandmarksUpdated = { /* no-op */ }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        Canvas(modifier = Modifier
            .fillMaxSize()
            .background(Color.White)) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            drawFollowingFace(centerX,centerY, direction = detectedPosition )
        }


        // Position status and control button
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f)
        ) {
            Card(
                modifier = Modifier
                    .padding(bottom = 60.dp, end = 20.dp)
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
            FilledIconButton(
                onClick = { onShowRobotCamera() },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_camera_alt_24),
                    contentDescription = "Emotion control",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }
    }
}
