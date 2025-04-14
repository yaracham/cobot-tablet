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
    private val targetDeviceMacAddress = "3C:A3:08:90:7D:62" // Replace with your device's MAC
    var connectionStatus = mutableStateOf("Not connected")

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

    private val serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val characteristicUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    @RequiresApi(Build.VERSION_CODES.S)
    fun connectDirectly() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Permission denied: BLUETOOTH_CONNECT")
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(targetDeviceMacAddress)
        if (device != null) {
            connectToDevice(device)
            Log.d("BLE", "Connecting to $targetDeviceMacAddress")
        } else {
            Log.e("BLE", "Device not found or Bluetooth not supported.")
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
                    connectionStatus.value = "Connected"
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE", "Disconnected from device.")
                    connectionStatus.value = "Disconnected"
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Services discovered successfully.")
                } else {
                    Log.e("BLE", "Service discovery failed with status: $status")
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun sendMessage(message: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
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
}
