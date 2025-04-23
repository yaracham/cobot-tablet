package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

fun DrawScope.drawConnectingFace(centerX: Float, centerY: Float, offset: Float) {
    val eyeSpacing = 120f
    val eyeTop = centerY - 100f

    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing + offset
        drawRoundRect(
            color = Color.Blue,
            topLeft = Offset(x - 20f, eyeTop),
            size = Size(80f, 200f),
            cornerRadius = CornerRadius(50f, 50f)
        )
    }
}
