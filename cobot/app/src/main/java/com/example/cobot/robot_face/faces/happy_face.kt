package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawHappyFace(centerX: Float, centerY: Float, blink: Float, eyeCurve: Float, mouthRadius: Float) {
    val eyeSpacing = 120f
    val eyeTop = centerY - 210f // match neutral positioning

    // Eyes with animated curve matching neutral's eye spacing and top
    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing
        val eyePath = Path().apply {
            moveTo(x - 40f, eyeTop + 50f)
            quadraticBezierTo(x, eyeTop - eyeCurve, x + 40f, eyeTop + 50f)
        }
        drawPath(
            path = eyePath,
            color = Color.Blue,
            style = Stroke(width = 25f, cap = StrokeCap.Round)
        )
    }

    // Mouth matching neutral alignment and center offset
    val mouthCenter = Offset(centerX + 0f, centerY + 60f) // match neutral face offset
    drawArc(
        color = Color.Blue,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(mouthCenter.x - mouthRadius, mouthCenter.y - mouthRadius),
        size = Size(mouthRadius * 2, mouthRadius * 2),
        style = Fill
    )
}
