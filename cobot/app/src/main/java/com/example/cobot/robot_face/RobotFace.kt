package com.example.cobot.robot_face

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Emotion {
    NEUTRAL, HAPPY, SLEEPING, SURPRISED
}

@Composable
fun RobotFace(emotion: Emotion) {
    val transition = updateTransition(targetState = emotion, label = "FaceTransition")

    val eyeCurveProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600) },
        label = "EyeCurve"
    ) {
        when (it) {
            Emotion.HAPPY -> 1f
            Emotion.NEUTRAL -> 0f
            Emotion.SLEEPING -> -1f
            Emotion.SURPRISED -> 2f
        }
    }

    val eyeScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600) },
        label = "EyeScale"
    ) {
        when (it) {
            Emotion.SURPRISED -> 1.5f  // Scale the eyes up for surprise
            else -> 1f
        }
    }

    val markPositionX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600) },
        label = "MarkPositionX"
    ) {
        when (it) {
            Emotion.SURPRISED -> -50f  // Move the surprise mark closer
            else -> 0f
        }
    }

    // Blinking logic (only if NEUTRAL)
    val blinkProgress = remember { Animatable(1f) } // 1f = open, 0f = fully closed
    val context = LocalContext.current

    LaunchedEffect(emotion) {
        while (true) {
            if (emotion == Emotion.NEUTRAL) {
                // Random delay between 500 ms and 6 seconds
                delay(Random.nextLong(500L, 6000L))

                // Blink animation
                blinkProgress.animateTo(
                    targetValue = 0f, animationSpec = tween(100)
                )
//                playBlinkSound(context)  // Play sound during blink

                blinkProgress.animateTo(
                    targetValue = 1f, animationSpec = tween(100)
                )
            } else {
                blinkProgress.snapTo(1f) // Keep eyes open if not neutral
                delay(500L)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        drawEyes(centerX, centerY, eyeCurveProgress, blinkProgress.value, eyeScale)

        if (emotion == Emotion.SLEEPING) {
            drawZzz(centerX, centerY)
        }
    }
}

fun DrawScope.drawEyes(centerX: Float, centerY: Float, curve: Float, blink: Float, scale: Float) {
    val eyeSpacing = 100f
    val eyeTop = centerY - 100f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing

        when {
            curve > 1f -> {
                // Surprised: Circle eyes with animation
                drawCircle(
                    color = Color.Cyan,
                    radius = 40f * scale, // Scaled eye for surprise
                    center = Offset(x, eyeTop + 50f),
                    style = Fill
                )
                drawEyebrows(centerX, centerY, curve)

            }
            curve > 0f -> {
                // Happy: Curve upwards
                val path = Path().apply {
                    moveTo(x - 40f, eyeTop + 50f)
                    quadraticBezierTo(
                        x, eyeTop - 40f * curve,
                        x + 40f, eyeTop + 50f
                    )
                }
                drawPath(
                    path = path,
                    color = Color.Cyan,
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }
            curve < 0f -> {
                // Sleeping: Curve downwards
                val path = Path().apply {
                    moveTo(x - 40f, eyeTop + 10f)
                    quadraticBezierTo(
                        x, eyeTop - 40f * curve,
                        x + 40f, eyeTop + 10f
                    )
                }
                drawPath(
                    path = path,
                    color = Color.Cyan,
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }
            else -> {
                // Neutral: Vertical rectangles
                val fullHeight = 140f
                val visibleHeight = fullHeight * blink
                val topOffset = eyeTop + (fullHeight - visibleHeight) / 2

                drawRoundRect(
                    color = Color.Cyan,
                    topLeft = Offset(x - 20f, topOffset),
                    size = Size(60f, visibleHeight),
                    cornerRadius = CornerRadius(30f, 30f)
                )
            }
        }
    }
}

fun DrawScope.drawEyebrows(centerX: Float, centerY: Float, curve: Float) {
    val eyeSpacing = 100f
    val eyeTop = centerY - 100f
    val eyebrowHeight = 30f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing

        if (curve > 1f) {
            // Surprised: Draw eyebrows
            val eyebrowPath = Path().apply {
                moveTo(x - 50f, eyeTop - eyebrowHeight)
                quadraticBezierTo(
                    x, eyeTop - 2 * eyebrowHeight * curve,
                    x + 50f, eyeTop - eyebrowHeight
                )
            }
            drawPath(
                path = eyebrowPath,
                color = Color.Cyan,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}



fun DrawScope.drawZzz(centerX: Float, centerY: Float) {
    val zSize = 40f
    val zSpacing = 20f
    val startX = centerX + 120f
    val startY = centerY - 100f

    for (i in 0 until 3) {
        val yOffset = i * (zSize + zSpacing)
        drawZ(startX, startY + yOffset, zSize)
    }
}

fun DrawScope.drawZ(x: Float, y: Float, size: Float) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x + size, y + size)
    }
    drawPath(
        path = path,
        color = Color.Cyan,
        style = Stroke(width = 6f, cap = StrokeCap.Round)
    )
}

// Uncomment this to play blink sound:
//fun playBlinkSound(context: Context) {
//    try {
//        val mediaPlayer = MediaPlayer.create(context, R.raw.blink_sound)
//        mediaPlayer.start()
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//}
