package com.example.cobot.bluetooth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun BluetoothConnectionDialog(
    state: BluetoothConnectionState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is BluetoothConnectionState.Connecting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Connecting") },
                text = { Text("Connecting to Bluetooth device...") },
                confirmButton = {}
            )
        }

        is BluetoothConnectionState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Connection Error") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            )
        }

        else -> Unit
    }
}
