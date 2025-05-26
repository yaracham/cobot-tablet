package com.example.cobot.bluetooth
/**
 * BluetoothConnectionDialog.kt
 *
 * This file defines the `BluetoothConnectionDialog` composable, which provides user feedback during Bluetooth connection
 * processes. It displays modal dialogs for ongoing connection attempts and error states to ensure the user is informed
 * and can take corrective action.
 *
 * Core Components:
 * - Alert dialogs for Bluetooth connection states (`Connecting`, `Error`)
 * - Retry and dismiss actions to allow user intervention
 *
 * Parameters:
 * @param state Current `BluetoothConnectionState` indicating the connection progress or issue.
 * @param onRetry Callback triggered when the user opts to retry the connection.
 * @param onDismiss Callback triggered when the user cancels or closes the dialog.
 *
 * This composable is intended to improve the user experience by clearly communicating Bluetooth status and allowing
 * retry or exit actions without leaving the screen.
 */

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
                onDismissRequest = { onDismiss() },
                title = { Text("Connecting") },
                text = { Text("Connecting to Bluetooth device...") },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        is BluetoothConnectionState.Error -> {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text("Connection Error") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Close")
                    }
                }
            )
        }

        else -> Unit
    }
}
