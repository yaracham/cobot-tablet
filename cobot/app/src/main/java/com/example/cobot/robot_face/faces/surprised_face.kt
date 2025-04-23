package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill

fun DrawScope.drawSurprisedFace(centerX: Float, centerY: Float) {
    val eyeSpacing = 120f
    val eyeTop = centerY - 50f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing
        drawCircle(
            color = Color.Blue,
            radius = 60f,
            center = Offset(x, eyeTop),
            style = Fill
        )
    }

    drawCircle(
        color = Color.Blue,
        radius = 40f,
        center = Offset(centerX, centerY + 120f),
        style = Fill
    )
}
