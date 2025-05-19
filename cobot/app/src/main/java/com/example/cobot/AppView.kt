package com.example.cobot

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

//        MainActivity.ScreenState.EMOTION -> EmotionRobotFaceScreen(
//            hM10BluetoothHelper,
//            onRequestConnect = onRequestConnect
//        )


        MainActivity.ScreenState.AUTOMATIONFACE -> AutomationRobotFaceScreen(
            hM10BluetoothHelper = hM10BluetoothHelper,
            onShowRobotCamera
        )

        MainActivity.ScreenState.EMOTION -> ColorGameScreen()
    }
}
