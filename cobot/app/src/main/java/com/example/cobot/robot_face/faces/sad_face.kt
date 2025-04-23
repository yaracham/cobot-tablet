package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap

fun DrawScope.drawSadFace(centerX: Float, centerY: Float) {
    val eyeLength = 90f
    val eyeOffsetX = 130f
    val eyeTop = centerY - 100f

    drawLine(
        color = Color.Blue,
        start = Offset(centerX - eyeOffsetX - eyeLength / 2, eyeTop + 30f),
        end = Offset(centerX - eyeOffsetX + eyeLength / 2, eyeTop),
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )

    drawLine(
        color = Color.Blue,
        start = Offset(centerX + eyeOffsetX - eyeLength / 2, eyeTop),
        end = Offset(centerX + eyeOffsetX + eyeLength / 2, eyeTop + 30f),
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )
}
