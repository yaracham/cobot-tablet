package com.example.cobot

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val CAMERA_PERMISSION_CODE = 100

    //    private val bluetoothManager = MyBluetoothManager()
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
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                // Log or handle permission result if needed
            }
        launcher.launch(permissions.toTypedArray())
    }

    @RequiresApi(value = 31)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkCameraPermission()

        hm10Helper = HM10BluetoothHelper(this)
        requestAllPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hm10Helper.connectDirectly()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    200
                )
            }
        }

        setContent {
            CobotTheme {
                var selectedTab by remember { mutableStateOf("AOF") }
                val context = LocalContext.current
                val state by hm10Helper.connectionState

                val bluetoothAdapter: BluetoothAdapter? =
                    remember {
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    }

                if (bluetoothAdapter?.isEnabled == false) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, 1)
                }
                LaunchedEffect(hm10Helper.receivedMessage.value) {
                    when {
                        hm10Helper.receivedMessage.value.contains("AOF") -> selectedTab = "AOF" // Follow tab
                        hm10Helper.receivedMessage.value.contains("AON") -> selectedTab = "AON" // Emotion tab
                    }
                }
                BluetoothConnectionDialog(
                    state = state,
                    onRetry = { hm10Helper.connectDirectly() },
                    onDismiss = { hm10Helper.connectionState.value = BluetoothConnectionState.Disconnected }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                ) {
                    TabRow(selectedTabIndex = if (selectedTab == "AON") 0 else 1) {
                        Tab(
                            selected = selectedTab == "AON",
                            onClick = { selectedTab = "AON" },
                            text = { Text("Automated Driving") }
                        )
                        Tab(
                            selected = selectedTab == "AOF",
                            onClick = { selectedTab = "AOF" },
                            text = { Text("Emotion Detection") }
                        )
                    }

                    when (selectedTab) {
                        "AOF" -> RobotFaceEmotionDemo(hm10Helper)
                        "AON" -> PersonFollowingScreen(hm10Helper)
                    }
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun connectToHM10(macAddress: String) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        val socket = device.createRfcommSocketToServiceRecord(uuid)

        Thread {
            try {
                bluetoothAdapter.cancelDiscovery()
                socket.connect()
                Log.d("Bluetooth", "Connected to HM-10/1C")

                val outputStream = socket.outputStream
                val inputStream = socket.inputStream

                outputStream.write("Hello from Android\n".toByteArray())

                val buffer = ByteArray(1024)
                val bytes = inputStream.read(buffer)
                val receivedMessage = String(buffer, 0, bytes)
                Log.d("Bluetooth", "Received: $receivedMessage")

            } catch (e: Exception) {
                Log.e("Bluetooth", "Connection failed", e)
            }
        }.start()
    }
}
