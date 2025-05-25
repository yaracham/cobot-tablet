package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawNeutralFace(centerX: Float, centerY: Float, blink: Float) {
    val eyeSpacing = 130f
    val eyeTop = centerY - 240f

    // Eyes with blink animation
    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing
        val fullHeight = 220f
        val visibleHeight = fullHeight * blink
        val topOffset = eyeTop + (fullHeight - visibleHeight) / 2

        drawRoundRect(
            color = Color.Blue,
            topLeft = Offset(x - 20f, topOffset),
            size = Size(80f, visibleHeight),
            cornerRadius = CornerRadius(50f, 50f)
        )
    }

    val mouthWidth = 280f
    val mouthY = centerY + 140f
    val curveHeight = 60f
    val mouthShift = 20f

    val mouth = Path().apply {
        moveTo(centerX - mouthWidth / 2 + mouthShift, mouthY)
        quadraticBezierTo(centerX + mouthShift, mouthY + curveHeight, centerX + mouthWidth / 2 + mouthShift, mouthY)
    }
    drawPath(
        path = mouth,
        color = Color.Blue,
        style = Stroke(width = 30f, cap = StrokeCap.Round) // rounded stroke ends
    )
}