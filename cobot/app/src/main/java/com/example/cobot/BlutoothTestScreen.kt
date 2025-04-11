//package com.example.cobot.bluetooth
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import com.example.cobot.Bluetooth.BluetoothManager
//
//@Composable
//fun BluetoothTestScreen(
//    bluetoothManager: BluetoothManager
//) {
//    var messageToSend by remember { mutableStateOf("") }
//    val receivedMessages = remember { mutableStateListOf<String>() }
//
//    // Collect received messages
//    LaunchedEffect(bluetoothManager) {
//        bluetoothManager.receivedData.collect { message ->
//            receivedMessages.add("Received: $message")
//        }
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        // Message input
//        OutlinedTextField(
//            value = messageToSend,
//            onValueChange = { messageToSend = it },
//            label = { Text("Message to send") },
//            modifier = Modifier.fillMaxWidth()
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Send button
//        Button(
//            onClick = {
//                if (messageToSend.isNotBlank()) {
//                    val success = bluetoothManager.sendData(messageToSend)
//                    receivedMessages.add("Sent: $messageToSend ${if (success) "✓" else "✗"}")
//                    messageToSend = ""
//                }
//            },
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Send Message")
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Connection status
//        Text(
//            text = if (bluetoothManager.isConnected()) "Connected" else "Disconnected",
//            color = if (bluetoothManager.isConnected()) MaterialTheme.colorScheme.primary
//            else MaterialTheme.colorScheme.error
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Received messages list
//        Text("Message Log:", style = MaterialTheme.typography.titleMedium)
//        LazyColumn(
//            modifier = Modifier
//                .fillMaxWidth()
//                .weight(1f)
//        ) {
//            items(receivedMessages.reversed()) { message ->
//                Text(
//                    text = message,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(8.dp)
//                )
//                Divider()
//            }
//        }
//    }
//}