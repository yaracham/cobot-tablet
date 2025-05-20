package com.example.cobot.color_game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GameOverDialog(
    playerName: String,
    level: Int,
    scores: List<PlayerScore>,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val highScore = scores.maxOfOrNull { it.score } ?: 0
    val isNewHighScore = level > highScore

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                if (isNewHighScore) "🎉 New High Score!" else "Game Over",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
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
                                Text(it.name)
                                Text("Level ${it.score}")
                            }
                        }
                    }
                }
            }
        }
    )
}
