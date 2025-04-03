package com.example.cobot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cobot.ui.theme.CobotTheme

class MainActivity : ComponentActivity() {
    private val CAMERA_PERMISSION_CODE = 100

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkCameraPermission()
        setContent {
            CobotTheme {
//                if (!Python.isStarted()) {
//                    Python.start(AndroidPlatform(this))
//                }
//
//                val py = Python.getInstance()
//                val pyObject = py.getModule("test_fer")
//
//                val result = pyObject.callAttr("detect_emotion").toString()
//
//                Log.d("EmotionDetector", "Detected Emotion: $result")
//                LiveEmotionDetectionScreen()
            }
        }
    }
}
