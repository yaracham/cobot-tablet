package com.example.cobot.automated_driving
/**
 * EyesAnimation.kt
 *
 * This file defines the `EyesAnimation` composable, a visual component that displays animated robot eyes which
 * react to the user's position by shifting the pupils smoothly to the LEFT, CENTER, or RIGHT.
 *
 * Core Components:
 * - `EyesAnimation`: A Jetpack Compose Composable that draws two circular eyes and animates the pupils using
 *   a spring-based offset based on directional input.
 * - Direction tracking: Interprets string input ("LEFT", "CENTER", "RIGHT") and updates the pupil position accordingly.
 * - Smooth animation: Uses Jetpack Compose’s spring animation for fluid eye movement transitions.
 *
 * Parameters:
 * @param modifier Modifier for layout customization in Compose.
 * @param direction String indicating user position (LEFT, CENTER, RIGHT).
 *
 * This component is designed for use in interactive or robot-assisted UI, providing a responsive and intuitive
 * feedback mechanism that mimics human-like attention behavior.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun EyesAnimation(position: String, modifier: Modifier = Modifier) {
    // Create animatable values for the x-position of each pupil
    val leftEyeX = remember { Animatable(0f) }
    val rightEyeX = remember { Animatable(0f) }

    // Define the target positions based on the detected position
    val targetX = when (position) {
        "LEFT" -> 0.3f
        "RIGHT" -> -0.3f
        else -> 0f // CENTER
    }

    // Animate the pupils to the target position with a spring animation
    LaunchedEffect(position) {
        launch {
            leftEyeX.animateTo(
                targetValue = targetX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            rightEyeX.animateTo(
                targetValue = targetX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Draw the eyes
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(16.dp)
    ) {
        val eyeRadius = size.height / 2.5f
        val pupilRadius = eyeRadius / 2.5f

        // Calculate eye positions
        val leftEyeCenter = Offset(size.width * 0.3f, size.height / 2)
        val rightEyeCenter = Offset(size.width * 0.7f, size.height / 2)

        // Calculate pupil positions with animation
        val maxPupilOffset = eyeRadius - pupilRadius - 4f
        val leftPupilCenter = Offset(
            leftEyeCenter.x + leftEyeX.value * maxPupilOffset,
            leftEyeCenter.y
        )
        val rightPupilCenter = Offset(
            rightEyeCenter.x + rightEyeX.value * maxPupilOffset,
            rightEyeCenter.y
        )

        // Draw eye whites
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = leftEyeCenter,
            style = Stroke(width = 8f)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = leftEyeCenter
        )

        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = rightEyeCenter,
            style = Stroke(width = 8f)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = rightEyeCenter
        )

        // Draw pupils
        drawCircle(
            color = Color.Black,
            radius = pupilRadius,
            center = leftPupilCenter
        )

        drawCircle(
            color = Color.Black,
            radius = pupilRadius,
            center = rightPupilCenter
        )
    }
}

