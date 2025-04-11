package com.example.cobot.robot_face

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

enum class Emotion {
    NEUTRAL, HAPPY, SLEEPING, SURPRISED, CONNECTING, ANGRY, SAD
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
            Emotion.CONNECTING -> 3f
            Emotion.ANGRY -> 4f
            Emotion.SAD -> -2f
        }
    }

    val eyeScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600) },
        label = "EyeScale"
    ) {
        when (it) {
            Emotion.SURPRISED -> 1.5f
            else -> 1f
        }
    }

    val markPositionX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600) },
        label = "MarkPositionX"
    ) {
        when (it) {
            Emotion.SURPRISED -> -50f
            else -> 0f
        }
    }

    val blinkProgress = remember { Animatable(1f) }
    val eyeOffset = remember { Animatable(0f) }
    val context = LocalContext.current

    LaunchedEffect(emotion) {
        when (emotion) {
            Emotion.NEUTRAL -> {
                while (true) {
                    delay(Random.nextLong(500L, 6000L))
                    blinkProgress.animateTo(0f, tween(100))
//                  playBlinkSound(context)
                    blinkProgress.animateTo(1f, tween(100))
                }
            }
            Emotion.CONNECTING -> {
                while (true) {
                    eyeOffset.animateTo(-20f, tween(300))
                    eyeOffset.animateTo(20f, tween(300))
                }
            }
            else -> {
                blinkProgress.snapTo(1f)
                eyeOffset.snapTo(0f)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        when (emotion) {
            Emotion.ANGRY -> drawAngryFace(centerX, centerY)
            Emotion.SAD -> drawSadFace(centerX, centerY)
            else -> drawEyes(centerX, centerY, eyeCurveProgress, blinkProgress.value, eyeScale, eyeOffset.value)
        }

        if (emotion == Emotion.SLEEPING) {
            drawZzz(centerX, centerY)
        }
//        if (emotion == Emotion.HAPPY) {
//            drawContext.canvas.nativeCanvas.apply {
//                drawText(
////                    if (emotion == Emotion.CONNECTING) "✅ I'm connected!" else "🔄 Connecting...",
//                    centerX - 200f,
//                    centerY + 200f,
//                    android.graphics.Paint().apply {
//                        textSize = 50f
//                        color = android.graphics.Color.CYAN
//                    }
//                )
//            }
//        }
    }
}

fun DrawScope.drawAngryFace(centerX: Float, centerY: Float) {
    val radius = min(size.width, size.height) / 2.2f

    // Eye parameters
    val eyeRadius = radius * 0.3f
    val eyeOffsetX = radius * 0.5f
    val eyeOffsetY = radius * 0.3f
    val eyeStrokeWidth = 8f
    val eyeTiltAngle = 195f // Degrees to tilt the eyes downward

    // Draw left eye (filled and rotated)
    withTransform({
        // Rotate around the eye center
        rotate(
            degrees = eyeTiltAngle,
            pivot = Offset(centerX - eyeOffsetX, centerY - eyeOffsetY)
        )
    }) {
        drawArc(
            color = Color.Cyan,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true, // This fills the shape
            topLeft = Offset(centerX - eyeOffsetX - eyeRadius, centerY - eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 2)
        )
    }

    // Draw right eye (filled and rotated)
    withTransform({
        // Rotate around the eye center
        rotate(
            degrees = -eyeTiltAngle,
            pivot = Offset(centerX + eyeOffsetX, centerY - eyeOffsetY)
        )
    }) {
        drawArc(
            color = Color.Cyan,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true, // This fills the shape
            topLeft = Offset(centerX + eyeOffsetX - eyeRadius, centerY - eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 2)
        )
    }

    // Angry Eyebrows (angled down toward center)
//    val browLength = eyeWidth + 10f
//    val browThickness = 8f
//    drawLine(
//        color = Color.Cyan,
//        start = Offset(centerX - eyeOffsetX - eyeWidth / 2, centerY - eyeOffsetY - 15f),
//        end = Offset(centerX - eyeOffsetX + eyeWidth / 2, centerY - eyeOffsetY - 25f),
//        strokeWidth = browThickness,
//        cap = StrokeCap.Round
//    )
//    drawLine(
//        color = Color.Cyan,
//        start = Offset(centerX + eyeOffsetX - eyeWidth / 2, centerY - eyeOffsetY - 25f),
//        end = Offset(centerX + eyeOffsetX + eyeWidth / 2, centerY - eyeOffsetY - 15f),
//        strokeWidth = browThickness,
//        cap = StrokeCap.Round
//    )

//    // Angry Mouth (frown)
//    val mouthWidth = radius * 0.6f
//    val mouthY = centerY + radius * 0.4f
//    val path = Path().apply {
//        moveTo(centerX - mouthWidth / 2, mouthY)
//        quadraticBezierTo(centerX, mouthY - 20f, centerX + mouthWidth / 2, mouthY)
//    }
//    drawPath(
//        path = path,
//        color = Color.Cyan,
//        style = Stroke(width = 8f, cap = StrokeCap.Round)
//    )
}
fun DrawScope.drawSadFace(centerX: Float, centerY: Float) {
    val eyeLength = 90f
    val eyeOffsetX = 100f
    val eyeTop = centerY - 100f

    // Left brow (outer down, inner up)
    drawLine(
        color = Color.Cyan,
        start = Offset(centerX - eyeOffsetX - eyeLength / 2, eyeTop + 30f), // outer (lower)
        end = Offset(centerX - eyeOffsetX + eyeLength / 2, eyeTop),         // inner (higher)
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )

    // Right brow (inner up, outer down)
    drawLine(
        color = Color.Cyan,
        start = Offset(centerX + eyeOffsetX - eyeLength / 2, eyeTop),        // inner (higher)
        end = Offset(centerX + eyeOffsetX + eyeLength / 2, eyeTop + 30f),    // outer (lower)
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )
}


fun DrawScope.drawEyes(centerX: Float, centerY: Float, curve: Float, blink: Float, scale: Float, horizontalOffset: Float = 0f) {
    val eyeSpacing = 100f
    val eyeTop = centerY - 100f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing + horizontalOffset

        when {
            curve > 1f -> {
                drawCircle(
                    color = Color.Cyan,
                    radius = 40f * scale,
                    center = Offset(x, eyeTop + 50f),
                    style = Fill
                )
                drawEyebrows(centerX, centerY, curve)
            }
            curve > 0f -> {
                val path = Path().apply {
                    moveTo(x - 40f, eyeTop + 50f)
                    quadraticBezierTo(x, eyeTop - 40f * curve, x + 40f, eyeTop + 50f)
                }
                drawPath(path = path, color = Color.Cyan, style = Stroke(width = 10f, cap = StrokeCap.Round))
            }
            curve < 0f -> {
                val path = Path().apply {
                    moveTo(x - 40f, eyeTop + 10f)
                    quadraticBezierTo(x, eyeTop - 40f * curve, x + 40f, eyeTop + 10f)
                }
                drawPath(path = path, color = Color.Cyan, style = Stroke(width = 10f, cap = StrokeCap.Round))
            }
            else -> {
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
            val eyebrowPath = Path().apply {
                moveTo(x - 50f, eyeTop - eyebrowHeight)
                quadraticBezierTo(x, eyeTop - 2 * eyebrowHeight * curve, x + 50f, eyeTop - eyebrowHeight)
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