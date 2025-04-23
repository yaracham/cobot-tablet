package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawSleepingFace(centerX: Float, centerY: Float) {
    val eyeSpacing = 120f
    val eyeTop = centerY - 100f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing
        drawLine(
            color = Color.Blue,
            start = Offset(x - 40f, eyeTop),
            end = Offset(x + 40f, eyeTop),
            strokeWidth = 12f
        )
    }

    drawZzz(centerX, centerY)
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
        color = Color.Blue,
        style = Stroke(width = 6f)
    )
}
