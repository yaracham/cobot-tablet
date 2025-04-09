package com.example.cobot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cobot.emotion_detection.LiveEmotionDetectionScreen
import com.example.cobot.robot_face.RobotFaceEmotionDemo
import com.example.cobot.ui.theme.CobotTheme
//import com.example.cobot.PersonFollowingScreen2
import com.example.cobot.PersonFollowing.PersonFollowingScreen
class MainActivity : ComponentActivity() {
    private val CAMERA_PERMISSION_CODE = 100

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkCameraPermission()
        setContent {
            CobotTheme {
                // Add tabs for switching between emotion detection and person following
                var selectedTab by remember { mutableIntStateOf(2) }

                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Emotion Detection") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Person Following") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("RobotFace") }
                        )
                    }

                    when (selectedTab) {
                        0 -> LiveEmotionDetectionScreen()
                        1 -> PersonFollowingScreen()
                        2 -> RobotFaceEmotionDemo()
                    }
                }
            }
        }
    }
}

