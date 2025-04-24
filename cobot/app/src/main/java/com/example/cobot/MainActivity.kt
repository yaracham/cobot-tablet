package com.example.cobot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cobot.PersonFollowing.PersonFollowingScreen
import com.example.cobot.bluetooth.BluetoothConnectionDialog
import com.example.cobot.bluetooth.BluetoothConnectionState
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.robot_face.RobotFaceEmotionDemo
import com.example.cobot.ui.theme.CobotTheme

// === Constants for easier maintenance ===
private const val TAB_AFF = "-AFF"
private const val TAB_AON = "-AON"

class MainActivity : ComponentActivity() {

    private val CAMERA_PERMISSION_CODE = 100
    private lateinit var hm10Helper: HM10BluetoothHelper

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val launcher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
        launcher.launch(permissions.toTypedArray())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkCameraPermission()
        requestAllPermissions()

        hm10Helper = HM10BluetoothHelper(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hm10Helper.connectDirectly()
        }

        setContent {
            CobotTheme {
                var selectedTab by remember { mutableStateOf(TAB_AFF) }
                val state by hm10Helper.connectionState

                BluetoothConnectionDialog(
                    state = state,
                    onRetry = { hm10Helper.connectDirectly() },
                    onDismiss = { hm10Helper.connectionState.value = BluetoothConnectionState.Disconnected }
                )

                LaunchedEffect(hm10Helper.receivedMessage.value) {
                    val cleanedMessage = hm10Helper.receivedMessage.value.trim()
                    when {
                        cleanedMessage.contains(TAB_AFF) -> selectedTab = TAB_AFF
                        cleanedMessage.contains(TAB_AON) -> selectedTab = TAB_AON
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                ) {
                    TabRow(selectedTabIndex = if (selectedTab == TAB_AON) 0 else 1) {
                        Tab(
                            selected = selectedTab == TAB_AON,
                            onClick = { selectedTab = TAB_AON },
                            text = { Text("Automated Driving") }
                        )
                        Tab(
                            selected = selectedTab == TAB_AFF,
                            onClick = { selectedTab = TAB_AFF },
                            text = { Text("Emotion Detection") }
                        )
                    }

                    when (selectedTab) {
                        TAB_AFF -> RobotFaceEmotionDemo(hm10Helper)
                        TAB_AON -> PersonFollowingScreen(hm10Helper)
                    }
                }
            }
        }
    }
}

