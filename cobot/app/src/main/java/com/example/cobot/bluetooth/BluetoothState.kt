package com.example.cobot.bluetooth


import android.bluetooth.BluetoothDevice

data class BluetoothState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectedDevice: BluetoothDevice? = null,
    val message: String = ""
)