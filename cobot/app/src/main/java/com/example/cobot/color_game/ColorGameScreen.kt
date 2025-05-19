package com.example.cobot.color_game

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.example.cobot.bluetooth.HM10BluetoothHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ColorGameScreen(
    hm10helper: HM10BluetoothHelper
) {
    val context = LocalContext.current
    val scores = remember { mutableStateListOf<PlayerScore>() }
    var playerName by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    LaunchedEffect(Unit) {
        scores.clear()
        scores.addAll(getPlayerScores(context))
    }

    val colors = listOf(
        Color(0xFFFF9800), // 0 = Orange
        Color(0xFF4CAF50), // 1 = Green
        Color(0xFF00BCD4), // 2 = Blue
        Color(0xFFFFFF00), // 3 = Yellow
        Color(0xFFF44336), // 4 = Red
        Color(0xFF800080)  // 5 = Purple
    )
    val commands = listOf("CO", "CG", "CB", "CY", "CR", "CP")

    var level by remember { mutableIntStateOf(1) }          // 1…10
    val sequence = remember { mutableStateListOf<Int>() }
    var userIndex by remember { mutableIntStateOf(0) }
    var tapsRemaining by remember { mutableIntStateOf(0) }
    var inputEnabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }       // "Level up!", "Game Over", etc.
    var gameOver by remember { mutableStateOf(false) }

    val highlightStates = remember { List(6) { mutableStateOf(false) } }

    LaunchedEffect(level) {
        if (level > 10) {
            message = "🎉 You beat all levels!"
            inputEnabled = false
            return@LaunchedEffect
        }

        highlightStates.forEach { it.value = false }

        message = "Level $level"
        inputEnabled = false
        delay(500)

        val next = Random.nextInt(6)
        sequence.add(next)

        Log.d("GAMEE", commands[next])
        hm10helper.sendMessage(commands[next] + "\r\n")
        delay(700)
        hm10helper.sendMessage("CF\r\n")

        tapsRemaining = sequence.size
        userIndex = 0
        message = "Tap $tapsRemaining times"
        inputEnabled = true
    }

    LaunchedEffect(gameOver) {
        if (gameOver) {
            showDialog = true
            scores.clear()
            scores.addAll(getPlayerScores(context))
        }
    }


    val radiusDp = 150.dp
    val circleSize = 100.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(90.dp))

        Text("Level $level", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Guess the colors in the correct order!",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )

        Box(
            modifier = Modifier
                .height(120.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!inputEnabled && level <= 10 && message.startsWith("Game Over")) {
                Button(
                    onClick = {
                        level = 1
                        message = "Restarting…"
                        sequence.clear()
                        gameOver = false
                    },
                    modifier = Modifier
                        .height(64.dp)
                        .width(200.dp)
                ) {
                    Text(
                        "Restart",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }

        Text(
            text = "Taps left: $tapsRemaining",
            style = MaterialTheme.typography.displaySmall,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .size(2 * radiusDp + circleSize)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((i * 60 - 90).toDouble())
                val xOffset = (cos(angleRad) * radiusPx).toInt()
                val yOffset = (sin(angleRad) * radiusPx).toInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOffset, yOffset) }
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(if (highlightStates[i].value) colors[i] else Color.Transparent)
                        .border(8.dp, colors[i], CircleShape)
                        .clickable(enabled = inputEnabled) {
                            if (!inputEnabled) return@clickable

                            if (i == sequence[userIndex]) {
                                highlightStates[i].value = true
                                userIndex++
                                tapsRemaining--
                                message = "Taps left: $tapsRemaining"

                                if (userIndex >= sequence.size) {
                                    inputEnabled = false
                                    message = "Correct! Next level…"
                                    level += 1
                                }
                            } else {
                                inputEnabled = false
                                message = "Game Over! Tap to restart."
                                gameOver = true
                            }
                        }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        savePlayerScore(context, PlayerScore(playerName, level))
                        scores.clear()
                        scores.addAll(getPlayerScores(context))
                        level = 1
                        message = "Restarting…"
                        sequence.clear()
                        gameOver = false
                        playerName = ""
                        showDialog = false
                    }
                }) {
                    Text("Submit")
                }
            },
            title = { Text("Game Over") },
            text = {
                Column {
                    Text("Enter your name to save your score:")
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("Name") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your score: $level")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Previous Scores:")
                    Column {
                        scores.sortedByDescending { it.score }.forEachIndexed { index, it ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (index == 0) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = it.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "Level ${it.score}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                }
            }
        )
    }


}




