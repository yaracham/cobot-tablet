package com.example.cobot.bluetooth
/**
 * BluetoothConnectionState.kt
 *
 * This file defines a sealed class hierarchy representing various states of the Bluetooth connection process,
 * providing a structured way to manage connection status across the application.
 *
 * Core Components:
 * - `Idle`: No active connection.
 * - `Connecting`: Attempting to establish a connection.
 * - `Connected`: Successfully connected to the device.
 * - `Disconnected`: Device is no longer connected.
 * - `Error`: Encountered an issue during the connection process, with a descriptive error message.
 *
 * These states enable reactive UI updates and robust error handling, ensuring a predictable and user-friendly
 * Bluetooth connection experience.
 */


sealed class BluetoothConnectionState {
    object Idle : BluetoothConnectionState()
    object Connecting : BluetoothConnectionState()
    object Connected : BluetoothConnectionState()
    object Disconnected : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}
