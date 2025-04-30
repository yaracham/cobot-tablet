package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill

fun DrawScope.drawSurprisedFace(centerX: Float, centerY: Float) {
    val eyeSpacing = 120f
    val eyeTop = centerY - 100f
    val eyeRadius = 60f

    for (side in listOf(-1f, 1f)) {
        val eyeCenterX = centerX + side * eyeSpacing

        // Outer eye (blue)
        drawCircle(
            color = Color.Blue,
            radius = eyeRadius,
            center = Offset(eyeCenterX, eyeTop),
            style = Fill
        )

        // Inner highlight (eyeball / reflection)
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.4f,
            center = Offset(eyeCenterX - 0f, eyeTop - 15f),
            style = Fill
        )
    }

    // Mouth as an oval (ellipse)
    val mouthWidth = 140f
    val mouthHeight = 250f
    drawOval(
        color = Color.Blue,
        topLeft = Offset(centerX - mouthWidth / 2, centerY + 100f),
        size = Size(mouthWidth, mouthHeight),
        style = Fill
    )
}
