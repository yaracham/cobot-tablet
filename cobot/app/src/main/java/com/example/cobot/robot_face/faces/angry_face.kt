package com.example.cobot.robot_face.faces

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.min

fun DrawScope.drawAngryFace(centerX: Float, centerY: Float) {
    val radius = min(size.width, size.height) / 2.2f
    val eyeRadius = radius * 0.3f
    val eyeOffsetX = radius * 0.5f
    val eyeOffsetY = radius * 0.3f
    val eyeTiltAngle = 195f

    withTransform({
        rotate(degrees = eyeTiltAngle, pivot = Offset(centerX - eyeOffsetX, centerY - eyeOffsetY))
    }) {
        drawArc(
            color = Color.Red,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - eyeOffsetX - eyeRadius, centerY - eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 2)
        )
    }

    withTransform({
        rotate(degrees = -eyeTiltAngle, pivot = Offset(centerX + eyeOffsetX, centerY - eyeOffsetY))
    }) {
        drawArc(
            color = Color.Red,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX + eyeOffsetX - eyeRadius, centerY - eyeOffsetY - eyeRadius),
            size = Size(eyeRadius * 2, eyeRadius * 2)
        )
    }
}