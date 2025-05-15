package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawFollowingFace(
    centerX: Float,
    centerY: Float,
    direction: String
) {
    val eyeSpacing = 130f
    val eyeTop = centerY - 240f

    // Eye movement based on direction
    val eyeMoveOffset = when (direction) {
        "LEFT" -> -50f
        "RIGHT" -> 50f
        else -> 0f
    }

    val fullHeight = 220f

    // Draw static eyes (no blinking)
    for (side in listOf(-1f, 1f)) {
        val x = centerX + side * eyeSpacing + eyeMoveOffset
        val topOffset = eyeTop

        drawRoundRect(
            color = Color.Blue,
            topLeft = Offset(x - 20f, topOffset),
            size = Size(80f, fullHeight),
            cornerRadius = CornerRadius(50f, 50f)
        )
    }

    // Slightly right-shifted, rounded smiling mouth
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
        style = Stroke(width = 30f, cap = StrokeCap.Round)
    )
}
