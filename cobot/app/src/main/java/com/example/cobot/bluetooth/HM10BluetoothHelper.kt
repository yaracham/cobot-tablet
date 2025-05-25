package com.example.cobot.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import java.util.*

class HM10BluetoothHelper(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private val targetDeviceMacAddress = "3C:A3:08:90:7D:62" // Replace with your device's MAC if changed
    val connectionState = mutableStateOf<BluetoothConnectionState>(BluetoothConnectionState.Idle)
    var receivedMessage = mutableStateOf("") // State for the received message

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val characteristicUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    @RequiresApi(Build.VERSION_CODES.S)
    fun connectDirectly() {
        connectionState.value = BluetoothConnectionState.Connecting

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Permission denied: BLUETOOTH_CONNECT")
            connectionState.value = BluetoothConnectionState.Error("Bluetooth permission denied.")
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(targetDeviceMacAddress)
        if (device != null) {
            connectToDevice(device)
            Log.d("BLE", "Connecting to $targetDeviceMacAddress")
        } else {
            Log.e("BLE", "Device not found or Bluetooth not supported.")
            connectionState.value =
                BluetoothConnectionState.Error("Device not found or Bluetooth not supported.")
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE", "Connected to device. Discovering services...")
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE", "Disconnected from device.")
                }
                connectionState.value = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> BluetoothConnectionState.Connected
                    BluetoothProfile.STATE_DISCONNECTED -> BluetoothConnectionState.Error("Disconnected from device.")
                    else -> BluetoothConnectionState.Error("Unknown connection state.")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    Log.d("BLE", "Services discovered successfully.")
                    for (service in gatt.services) {
                        Log.d("BLE", "Service UUID: ${service.uuid}")
                        for (characteristic in service.characteristics) {
                            Log.d("BLE", "-- Characteristic UUID: ${characteristic.uuid}")
                        }
                    }
                    startReceivingMessages()
                } else {
                    Log.e("BLE", "Service discovery failed with status: $status")
                }
            }

            // This callback is triggered when the characteristic value changes (new message received)
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == characteristicUuid) {
                    val message = String(value)
                    Log.d("BLE", "Received message: $message")
                    receivedMessage.value = message
                }
            }

            // For older Android versions
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (characteristic?.uuid == characteristicUuid) {
                    val value = characteristic?.value
                    val message = java.lang.String(value)
                    Log.d("BLE", "Received message: $message")
                    receivedMessage.value = message.toString()
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun sendMessage(message: String) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Cannot send: BLUETOOTH_CONNECT permission not granted")
            return
        }

        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(characteristicUuid)

        if (characteristic != null) {
            characteristic.value = message.toByteArray()
            val result = bluetoothGatt?.writeCharacteristic(characteristic)
            Log.d("BLE", "Sent message: $message | result: $result")
        } else {
            Log.e("BLE", "Characteristic not found.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startReceivingMessages() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Cannot receive: BLUETOOTH_CONNECT permission not granted")
            return
        }

        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(characteristicUuid)

        if (characteristic != null) {
            // Enable notifications for the characteristic
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)

            // Configure the descriptor to enable notifications on the remote device
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration Descriptor
            )

            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val success = bluetoothGatt?.writeDescriptor(descriptor)
                Log.d("BLE", "Enabled notifications for receiving messages: $success")
            } else {
                Log.e("BLE", "Descriptor not found for enabling notifications")
            }
        } else {
            Log.e("BLE", "Characteristic not found for receiving messages")
        }
    }
}