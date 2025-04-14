package com.example.cobot

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cobot.bluetooth.HM10BluetoothHelper

@Composable
fun MainScreen(hm10Helper: HM10BluetoothHelper) {
//    var status by remember { mutableStateOf("Not connected") }
    val status by hm10Helper.connectionStatus
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("HM-10 BLE Controller", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hm10Helper.connectDirectly()
            }
        }) {
            Text("Connect to HM-10")
        }

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hm10Helper.sendMessage("Hello\n")
            }
        }) {
            Text("Send Message")
        }
        Text("Status: $status", style = MaterialTheme.typography.bodyLarge)
    }
}
