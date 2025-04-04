package com.example.cobot

import MediaPipePoseHelper
import android.text.Layout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import java.util.concurrent.Executors

@Composable
fun MediaPipePoseScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // UI state: position label
    val positionState = remember { mutableStateOf("Detecting...") }

    // Create the helper
    val mediaPipeHelper = remember {
        MediaPipePoseHelper(context) { result ->
            val landmarks = result?.landmarks()?.firstOrNull()
            if (landmarks != null) {
                val leftShoulder = landmarks[11]
                val rightShoulder = landmarks[12]

                val centerX = (leftShoulder.x() + rightShoulder.x()) / 2

                positionState.value = when {
                    centerX < 0.33f -> "LEFT"
                    centerX > 0.66f -> "RIGHT"
                    else -> "CENTER"
                }
            } else {
                positionState.value = "NO PERSON"
            }
        }
    }

    // Clean up
    DisposableEffect(Unit) {
        onDispose {
            mediaPipeHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MediaPipeCameraPreview(
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            mediaPipeHelper = mediaPipeHelper
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp),
            contentAlignment = Layout.Alignment.TopCenter
        ) {
            Text(
                text = positionState.value,
                fontSize = 28.sp,
                color = Color.White
            )
        }
    }
}
