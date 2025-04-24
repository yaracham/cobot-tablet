package com.example.cobot

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.example.cobot.PersonFollowing.PersonFollowingScreen
import com.example.cobot.bluetooth.HM10BluetoothHelper
import com.example.cobot.robot_face.RobotFaceEmotionDemo

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppView(
    automation: String,
    hM10BluetoothHelper: HM10BluetoothHelper
) {

    when (automation.trim().uppercase()) {
        "-AON" -> PersonFollowingScreen(hM10BluetoothHelper)
        "-AFF" -> RobotFaceEmotionDemo(hM10BluetoothHelper)
        else -> RobotFaceEmotionDemo(hM10BluetoothHelper)
    }
}
