package com.example.cobot.color_game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ColorGameScreen() {
    // Define your 6 colors
    val colors = listOf(
        Color(0xFFFF9800), // Orange
        Color(0xFF4CAF50), // Green
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFFFF00), // Yellow
        Color(0xFFF44336), // Red
        Color(0xFFE91E63)  // Pink
    )

    // Mutable states to track which are selected
    val selectedStates = remember { List(6) { mutableStateOf(false) } }

    // Precompute pixel radius
    val radiusDp = 150.dp
    val circleSize = 120.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(400.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 - 90).toDouble())
                val xOffset = (cos(angle) * radiusPx).toInt()
                val yOffset = (sin(angle) * radiusPx).toInt()

                val selected = selectedStates[i]

                // 1) The tappable circle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOffset, yOffset) }
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(if (selected.value) colors[i] else Color.Transparent)
                        .border(5.dp, colors[i], CircleShape)
                        .clickable {
                            selected.value = true
                        }
                )

                // 2) Launch effect *outside* of the Box, keyed on selected.value
                if (selected.value) {
                    LaunchedEffect(selected.value) {
                        delay(700)
                        selected.value = false
                    }
                }
            }
        }
    }
}




