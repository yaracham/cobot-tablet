package com.example.cobot

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.cobot.bluetooth.BluetoothConnectionDialog
import com.example.cobot.bluetooth.BluetoothConnectionState
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.ui.theme.CobotTheme


class MainActivity : ComponentActivity() {

    private lateinit var hm10Helper: HM10BluetoothHelper

    private fun requestPermissions(onPermissionsResult: () -> Unit) {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val launcher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val allGranted = result.values.all { it }
                Log.d("Permissions", "All permissions granted? $allGranted")
                onPermissionsResult()
            }

        launcher.launch(permissions.toTypedArray())
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions {
            hm10Helper = HM10BluetoothHelper(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hm10Helper.connectDirectly()
            }

            setContent {
                CobotTheme {
                    val state by hm10Helper.connectionState
                    val message by hm10Helper.receivedMessage
                    val cleanedMessage = message.trim()

                    BluetoothConnectionDialog(
                        state = state,
                        onRetry = { hm10Helper.connectDirectly() },
                        onDismiss = {
                            hm10Helper.connectionState.value = BluetoothConnectionState.Disconnected
                        }
                    )

                    LaunchedEffect(message) {
                        // If you need to perform side effects here, you still can.
                        Log.d("BLE", "New cleaned message: $cleanedMessage")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        AppView(automation = cleanedMessage, hm10Helper)
                    }
                }
            }
        }


    }
}

