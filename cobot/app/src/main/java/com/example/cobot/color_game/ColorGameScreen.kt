package com.example.cobot.color_game

import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.example.cobot.R
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

    val gameHelper = GameHelper()
    LaunchedEffect(Unit) {
        scores.clear()
        scores.addAll(getPlayerScores(context))
    }

    val colors = listOf(
        Color(0xFFFF9800), // 0 = Orange
        Color(0xFF4CAF50), // 1 = Green
        Color(0xFF00BCD4), // 2 = Blue
        Color(0xFFFF00FF), // 3 = Fuchsia
        Color(0xFFF44336), // 4 = Red
        Color(0xFF800080)  // 5 = Purple
    )
    val commands = listOf("CO", "CG", "CB", "CY", "CR", "CP")

    var level by remember { mutableIntStateOf(1) }
    val sequence = remember { mutableStateListOf<Int>() }
    var userIndex by remember { mutableIntStateOf(0) }
    var tapsRemaining by remember { mutableIntStateOf(1) }
    var inputEnabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var gameOver by remember { mutableStateOf(false) }
    var restart by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    val highlightStates = remember { List(6) { mutableStateOf(false) } }

    fun resetGame() {
        level = 1
        sequence.clear()
        userIndex = 0
        tapsRemaining = 1
        message = ""
        gameOver = false
        restart = true
        highlightStates.forEach { it.value = false }
    }

    LaunchedEffect(level, gameStarted,restart) {
        if (!gameStarted) return@LaunchedEffect
        highlightStates.forEach { it.value = false }

        message = "Level $level"
        inputEnabled = false
        delay(500)

        var next = Random.nextInt(6)
        while (sequence.isNotEmpty() && next == sequence.last()) {
            next = Random.nextInt(6)
        }
        sequence.add(next)
        Log.d("GAME", "Current sequence: ${sequence.joinToString()}")

        for (index in sequence) {
            hm10helper.sendMessage(commands[index] + "\r\n")
            delay(700)
        }
        hm10helper.sendMessage("CF\r\n")
        delay(200)

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
    if (!gameStarted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAFAFA)),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    gameStarted = true
                    level = 1
                    message = ""
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3) // Blue
                ),
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
            ) {
                Text("Start Game", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }
        return
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
    )  {
        Spacer(Modifier.height(90.dp))


            Text("Level $level", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text("Guess the colors in the correct order!", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .height(60.dp)
                    .padding(top = 2.dp),

                contentAlignment = Alignment.Center
            ) {
                if (!inputEnabled && message.startsWith("Game Over")) {
                    Button(
                        onClick = { resetGame() },
                        modifier = Modifier
                            .height(64.dp)
                            .width(200.dp)
                    ) {
                        Text("Restart", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Taps left: $tapsRemaining",
                style = MaterialTheme.typography.displaySmall,
                color = Color.DarkGray,
            )

        Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(2 * radiusDp + circleSize)
                    .height(280.dp),
                contentAlignment = Alignment.Center
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
                                    hm10helper.sendMessage(commands[i] + "\r\n")

                                    if (userIndex >= sequence.size) {
                                        inputEnabled = false

                                        val currentHighScore = scores.maxOfOrNull { it.score } ?: 0
                                        if (level + 1 > currentHighScore) {
                                            message = "🎉 New High Score!"
                                            coroutineScope.launch {
                                                gameHelper.sendWinningFlashSequence(hm10helper)
                                                delay(800)
                                                level += 1
                                            }
                                        } else {
                                            message = "Correct! Next level…"
                                            coroutineScope.launch {
                                                gameHelper.sendWinningFlashSequence(hm10helper)
                                                Log.d("GAME", "WINNING")
                                                delay(800)
                                                level += 1
                                            }
                                        }
                                    }

                                } else {
                                    inputEnabled = false
                                    val currentHighScore = scores.maxOfOrNull { it.score } ?: 0

                                    coroutineScope.launch {
                                        if (level > currentHighScore) {
                                            gameHelper.sendHighScoreCelebration(hm10helper)
                                            val mediaPlayer = MediaPlayer.create(context, R.raw.highscore)
                                            mediaPlayer.start()

                                            // Optional: clean up
                                            mediaPlayer.setOnCompletionListener {
                                                it.release()
                                            }
                                            Log.d("GAME", "LOSS: highscore")
                                            delay(500)
                                        }
                                        else {
                                            gameHelper.sendLoosing(hm10helper)
                                            val mediaPlayer = MediaPlayer.create(context, R.raw.gameover)
                                            mediaPlayer.start()

                                            // Optional: clean up
                                            mediaPlayer.setOnCompletionListener {
                                                it.release()
                                            }
                                            Log.d("GAME", "LOSS: Player failed at level $level")
                                        }
                                    }
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
        GameOverDialog(
            playerName = playerName,
            level = level,
            scores = scores,
            onNameChange = { playerName = it },
            onSave = {
                coroutineScope.launch {
                    savePlayerScore(context, PlayerScore(playerName, level))
                    scores.clear()
                    scores.addAll(getPlayerScores(context))
                    playerName = ""
                    showDialog = false
                }
            },
            onCancel = {
                playerName = ""
                showDialog = false
            }
        )
    }
}

