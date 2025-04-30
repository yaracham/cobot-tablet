package com.example.cobot.robot_face

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.cobot.robot_face.faces.drawAngryFace
import com.example.cobot.robot_face.faces.drawConnectingFace
import com.example.cobot.robot_face.faces.drawHappyFace
import com.example.cobot.robot_face.faces.drawNeutralFace
import com.example.cobot.robot_face.faces.drawSadFace
import com.example.cobot.robot_face.faces.drawSleepingFace
import com.example.cobot.robot_face.faces.drawSurprisedFace
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun RobotFace(emotion: Emotion) {
    val blinkProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    val eyeOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    val transition = updateTransition(targetState = emotion, label = "EmotionTransition")

    val eyeCurve by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 800) },
        label = "EyeCurve"
    ) { target ->
        when (target) {
            Emotion.HAPPY -> 40f
            Emotion.NEUTRAL -> 10f
            else -> 0f
        }
    }

    val mouthRadius by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 800) },
        label = "MouthRadius"
    ) { target ->
        when (target) {
            Emotion.HAPPY -> 140f
            Emotion.NEUTRAL -> 100f
            Emotion.SAD -> 100f
            else -> 80f
        }
    }

    LaunchedEffect(emotion) {
        when (emotion) {
            Emotion.NEUTRAL -> {
                while (true) {
                    delay(Random.nextLong(500L, 6000L))
                    blinkProgress.animateTo(0f, tween(100))
                    blinkProgress.animateTo(1f, tween(100))
                }
            }
            else -> {
                blinkProgress.snapTo(1f)
                eyeOffset.snapTo(0f)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.White)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        when (emotion) {
            Emotion.ANGRY -> drawAngryFace(centerX, centerY)
            Emotion.SAD -> drawSadFace(centerX, centerY)
            Emotion.HAPPY -> drawHappyFace(centerX, centerY, blinkProgress.value, eyeCurve, mouthRadius)
            Emotion.NEUTRAL -> drawNeutralFace(centerX, centerY, blinkProgress.value, eyeCurve, mouthRadius)
            Emotion.SURPRISED -> drawSurprisedFace(centerX, centerY)
            Emotion.SLEEPING -> drawSleepingFace(centerX, centerY)
        }
    }
}
