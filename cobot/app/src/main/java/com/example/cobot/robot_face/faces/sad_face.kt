package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.drawSadFace(centerX: Float, centerY: Float) {
    val eyeOffsetX = 130f
    val eyeRadius = 45f
    val eyeY = centerY - 100f
    // Left Eye
    drawCircle(
        color = Color.Blue,
        radius = eyeRadius,
        center = Offset(centerX - eyeOffsetX, eyeY)
    )
    drawCircle(
        color = Color.White,
        radius = eyeRadius * 0.4f,
        center = Offset(centerX - eyeOffsetX - 10f, eyeY - 10f) // upper-left white highlight
    )

    // Right Eye
    drawCircle(
        color = Color.Blue,
        radius = eyeRadius,
        center = Offset(centerX + eyeOffsetX, eyeY)
    )
    drawCircle(
        color = Color.White,
        radius = eyeRadius * 0.4f,
        center = Offset(centerX + eyeOffsetX - 10f, eyeY - 10f) // upper-left white highlight
    )

    // Optional: Add small tear drops under each eye
    drawCircle(
        color = Color.Cyan,
        radius = 12f,
        center = Offset(centerX - eyeOffsetX, eyeY + 60f)
    )
    drawCircle(
        color = Color.Cyan,
        radius = 12f,
        center = Offset(centerX + eyeOffsetX, eyeY + 60f)
    )


    // Sad Mouth - strong downward curve
    val mouthWidth = 280f
    val mouthY = centerY + 160f
    val curveHeight = -90f
    val mouthShift = 0f

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

