package com.example.cobot
/**
 * AppView.kt
 *
 * Main Composable that manages navigation between different app screens based on the current state.
 * Supports switching between automation driving, emotion detection with robot face,
 * automation robot face screen, and a color game screen.
 *
 * Integrates with HM10BluetoothHelper for Bluetooth communication across screens.
 *
 * Requires Android S (API 31) or higher.
 *
 */

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.example.cobot.automated_driving.PersonFollowingScreen
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.color_game.ColorGameScreen
import com.example.cobot.robot_face.AutomationRobotFaceScreen
import com.example.cobot.robot_face.EmotionRobotFaceScreen

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppView(
    screen: MainActivity.ScreenState,
    hM10BluetoothHelper: HM10BluetoothHelper,
    onRequestConnect: () -> Unit,
    onShowRobotFace: () -> Unit,
    onShowRobotCamera: () -> Unit
) {
    when (screen) {
        MainActivity.ScreenState.AUTOMATION -> PersonFollowingScreen(
            hM10BluetoothHelper,
            onShowRobotFace
        )

        MainActivity.ScreenState.EMOTION -> EmotionRobotFaceScreen(
            hM10BluetoothHelper,
            onRequestConnect = onRequestConnect
        )

        MainActivity.ScreenState.AUTOMATION_FACE -> AutomationRobotFaceScreen(
            hM10BluetoothHelper = hM10BluetoothHelper,
            onShowRobotCamera
        )

        MainActivity.ScreenState.GAME -> ColorGameScreen(hm10helper = hM10BluetoothHelper)
    }
}
