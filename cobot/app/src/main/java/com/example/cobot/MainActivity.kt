package com.example.cobot

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cobot.emotion_detection.LiveEmotionDetectionScreen
import com.example.cobot.PersonFollowing.PersonFollowingScreen
//import com.example.cobot.bluetooth.BluetoothTestScreen
import com.example.cobot.robot_face.RobotFaceEmotionDemo
import com.example.cobot.ui.theme.CobotTheme

class MainActivity : ComponentActivity() {
    private val CAMERA_PERMISSION_CODE = 100
    private val bluetoothManager = com.example.cobot.Bluetooth.BluetoothManager()


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

        bluetoothManager.initialize(this)

        setContent {
            CobotTheme {
                var selectedTab by remember { mutableIntStateOf(2) }

                val bluetoothState by bluetoothManager.bluetoothState

                // Automatically try to connect to HC-06 if paired
                LaunchedEffect(Unit) {
                    bluetoothManager.pairedDevices.collect { devices ->
                        val hcDevice = devices.find { it.name?.contains("HC-06") == true }
                        if (hcDevice != null && !bluetoothState.isConnected && !bluetoothState.isConnecting) {
                            bluetoothManager.connectToDevice(hcDevice, this@MainActivity)
                        }
                    }
                }
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
                        1 -> PersonFollowingScreen(bluetoothManager)
                        2 -> RobotFaceEmotionDemo(bluetoothManager)
                    }
                }
            }
        }
    }
}


