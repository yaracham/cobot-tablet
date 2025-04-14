package com.example.cobot.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.lang.ref.WeakReference

class BluetoothManager : ViewModel() {
    private val TAG = "BluetoothViewModel"

    // Bluetooth state
    private val _bluetoothState = mutableStateOf(BluetoothState())
    val bluetoothState: State<BluetoothState> = _bluetoothState

    // Paired devices
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // Error messages
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage

    // Permission request trigger
    private val _permissionRequest = MutableSharedFlow<List<String>>()
    val permissionRequest: SharedFlow<List<String>> = _permissionRequest

    // Bluetooth adapter
    private var bluetoothAdapter: BluetoothAdapter? = null

    // Bluetooth socket for communication
    private var bluetoothSocket: BluetoothSocket? = null

    // Output stream for sending commands
    private var outputStream: OutputStream? = null

    // UUID for SPP (Serial Port Profile)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Alternative UUIDs to try if the standard one fails
    private val ALTERNATIVE_UUIDS = listOf(
        UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB"), // Health Device
        UUID.fromString("00001106-0000-1000-8000-00805F9B34FB"), // OBEX File Transfer
        UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB"), // Audio Source
        UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB"), // A/V Remote Control Target
        UUID.fromString("00001112-0000-1000-8000-00805F9B34FB"), // Headset
        UUID.fromString("00001115-0000-1000-8000-00805F9B34FB"), // PANU
        UUID.fromString("00001116-0000-1000-8000-00805F9B34FB"), // NAP
        UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB")  // Handsfree
    )

    // Bluetooth state receiver
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var receiverRegistered = false
    private var applicationContext: WeakReference<Context> = WeakReference(null)

    // Initialize Bluetooth
    fun initialize(context: Context) {
        // Store application context for later use
        applicationContext = WeakReference(context.applicationContext)

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            viewModelScope.launch {
                _errorMessage.emit("Bluetooth is not supported on this device")
            }
            _bluetoothState.value = _bluetoothState.value.copy(isAvailable = false)
            return
        }

        // Update Bluetooth state
        val isEnabled = bluetoothAdapter?.isEnabled == true
        _bluetoothState.value = _bluetoothState.value.copy(
            isAvailable = true,
            isEnabled = isEnabled
        )

        // If Bluetooth is disabled, clear the paired devices list
        if (!isEnabled) {
            _pairedDevices.value = emptyList()
            _bluetoothState.value = _bluetoothState.value.copy(
                isConnected = false,
                connectedDevice = null
            )
        }

        // If Bluetooth is enabled, check permissions and get paired devices
        if (isEnabled) {
            checkAndRequestPermissions(context)
        }

        // Register for Bluetooth state changes if not already registered
        if (!receiverRegistered) {
            registerBluetoothStateReceiver(context)
        }
    }

    // Register for Bluetooth state changes
    private fun registerBluetoothStateReceiver(context: Context) {
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        when (state) {
                            BluetoothAdapter.STATE_OFF -> {
                                Log.d(TAG, "Bluetooth turned OFF")
                                _bluetoothState.value = _bluetoothState.value.copy(
                                    isEnabled = false,
                                    isConnected = false,
                                    connectedDevice = null
                                )
                                _pairedDevices.value = emptyList()
                                disconnectDevice()
                            }
                            BluetoothAdapter.STATE_ON -> {
                                Log.d(TAG, "Bluetooth turned ON")
                                _bluetoothState.value = _bluetoothState.value.copy(isEnabled = true)
                                // Refresh paired devices
                                getPairedDevices(context)
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        receiverRegistered = true
    }

    // Check and request necessary permissions
    fun checkAndRequestPermissions(context: Context) {
        viewModelScope.launch {
            val permissionsToRequest = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ permissions
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                }
            } else {
                // Android 6.0 - 11 permissions
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                _permissionRequest.emit(permissionsToRequest)
            } else {
                // All permissions granted, get paired devices
                getPairedDevices(context)
            }
        }
    }

    // Enable Bluetooth
    fun enableBluetooth() {
        // Update message to trigger the Bluetooth settings intent in AppEntryPoint
        _bluetoothState.value = _bluetoothState.value.copy(message = "Opening Bluetooth settings")
    }

    // Get intent for opening Bluetooth settings
    fun getBluetoothSettingsIntent(): Intent {
        return Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // Get intent for app settings (for permissions)
    fun getAppSettingsIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        return intent
    }

    // Get paired devices
    fun getPairedDevices(context: Context) {
        viewModelScope.launch {
            try {
                if (!hasBluetoothPermissions(context)) {
                    _errorMessage.emit("Bluetooth permissions not granted")
                    checkAndRequestPermissions(context)
                    return@launch
                }

                // Check if Bluetooth is enabled
                if (bluetoothAdapter?.isEnabled != true) {
                    _pairedDevices.value = emptyList()
                    return@launch
                }

                try {
                    val pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    _pairedDevices.value = pairedDevices

                    if (pairedDevices.isEmpty()) {
                        _bluetoothState.value = _bluetoothState.value.copy(
                            message = "No paired devices found. Please pair your device first."
                        )
                    }
                } catch (e: SecurityException) {
                    _errorMessage.emit("Permission denied: ${e.message}")
                    checkAndRequestPermissions(context)
                }
            } catch (e: Exception) {
                _errorMessage.emit("Error getting paired devices: ${e.message}")
            }
        }
    }

    // Connect to a device
    fun connectToDevice(device: BluetoothDevice, context: Context) {
        viewModelScope.launch {
            if (!hasBluetoothPermissions(context)) {
                _errorMessage.emit("Bluetooth permissions not granted")
                checkAndRequestPermissions(context)
                return@launch
            }

            _bluetoothState.value = _bluetoothState.value.copy(
                isConnecting = true,
                message = "Connecting to ${try { device.name } catch (e: SecurityException) { "device" }}..."
            )

            try {
                withContext(Dispatchers.IO) {
                    try {
                        // Close existing connection if any
                        disconnectDevice()

                        // Try to connect with the standard SPP UUID first
                        try {
                            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                            bluetoothSocket?.connect()
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to connect with standard UUID: ${e.message}")

                            // Try with alternative UUIDs
                            for (uuid in ALTERNATIVE_UUIDS) {
                                try {
                                    Log.d(TAG, "Trying with alternative UUID: $uuid")
                                    bluetoothSocket?.close()
                                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                                    bluetoothSocket?.connect()
                                    Log.d(TAG, "Connected with UUID: $uuid")
                                    break
                                } catch (e: IOException) {
                                    Log.e(TAG, "Failed to connect with UUID $uuid: ${e.message}")
                                    continue
                                }
                            }

                            // If still not connected, try insecure connection
                            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                                Log.d(TAG, "Trying insecure connection")
                                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                                bluetoothSocket?.connect()
                            }
                        }

                        // Get output stream
                        outputStream = bluetoothSocket?.outputStream

                        // Update connected device
                        withContext(Dispatchers.Main) {
                            _bluetoothState.value = _bluetoothState.value.copy(
                                isConnected = true,
                                isConnecting = false,
                                connectedDevice = device,
                                message = "Connected to ${try { device.name } catch (e: SecurityException) { "device" }}"
                            )
                        }
                    } catch (e: SecurityException) {
                        withContext(Dispatchers.Main) {
                            _bluetoothState.value = _bluetoothState.value.copy(
                                isConnecting = false,
                                message = "Permission denied: ${e.message}"
                            )
                            checkAndRequestPermissions(context)
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            _bluetoothState.value = _bluetoothState.value.copy(
                                isConnecting = false,
                                message = "Failed to connect: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _bluetoothState.value = _bluetoothState.value.copy(
                    isConnecting = false,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    // Disconnect from device
    fun disconnectDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    bluetoothSocket?.close()
                } catch (e: SecurityException) {
                    withContext(Dispatchers.Main) {
                        _bluetoothState.value = _bluetoothState.value.copy(
                            message = "Permission denied: ${e.message}"
                        )
                    }
                }

                bluetoothSocket = null
                outputStream = null

                withContext(Dispatchers.Main) {
                    _bluetoothState.value = _bluetoothState.value.copy(
                        isConnected = false,
                        connectedDevice = null,
                        message = "Disconnected"
                    )
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    _bluetoothState.value = _bluetoothState.value.copy(
                        message = "Error disconnecting: ${e.message}"
                    )
                }
            }
        }
    }

    // Send command to connected device
    fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!_bluetoothState.value.isConnected) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.emit("Not connected to any device")
                    }
                    return@launch
                }

                outputStream?.write(command.toByteArray())
                outputStream?.flush()

                Log.d(TAG, "Command sent successfully: $command")
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    _errorMessage.emit("Error sending command: ${e.message}")
                    disconnectDevice()
                }
            }
        }
    }

    // Check if we have the necessary Bluetooth permissions
    private fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Clean up resources when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        disconnectDevice()

        // Unregister the Bluetooth state receiver
        if (receiverRegistered && bluetoothStateReceiver != null) {
            try {
                // Get the application context from our WeakReference
                val context = applicationContext.get()
                if (context != null) {
                    context.unregisterReceiver(bluetoothStateReceiver)
                    receiverRegistered = false
                    Log.d(TAG, "Bluetooth state receiver unregistered")
                } else {
                    Log.e(TAG, "Context is null, cannot unregister receiver")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering Bluetooth state receiver: ${e.message}")
            }


            // Add this method to the BluetoothViewModel class
// This is just the new method, not the entire file

// Inside the BluetoothViewModel class, add or update these methods:

            // Update the initialize method to reset states when Bluetooth is off
            fun initialize(context: Context) {
                // Existing code...

                // Update Bluetooth state
                val isEnabled = bluetoothAdapter?.isEnabled == true
                val wasEnabled = _bluetoothState.value.isEnabled

                _bluetoothState.value = _bluetoothState.value.copy(
                    isAvailable = true,
                    isEnabled = isEnabled
                )

                // If Bluetooth was enabled and is now disabled, reset all features
                if (wasEnabled && !isEnabled) {
                    disconnectDevice()
                }

                // Rest of the existing code...
            }

            // Update the disconnectDevice method
            fun disconnectDevice() {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        try {
                            bluetoothSocket?.close()
                        } catch (e: SecurityException) {
                            withContext(Dispatchers.Main) {
                                _bluetoothState.value = _bluetoothState.value.copy(
                                    message = "Permission denied: ${e.message}"
                                )
                            }
                        }

                        bluetoothSocket = null
                        outputStream = null

                        withContext(Dispatchers.Main) {
                            _bluetoothState.value = _bluetoothState.value.copy(
                                isConnected = false,
                                connectedDevice = null,
                                message = "Disconnected"
                            )
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            _bluetoothState.value = _bluetoothState.value.copy(
                                message = "Error disconnecting: ${e.message}"
                            )
                        }
                    }
                }
            }
        }
    }
}

