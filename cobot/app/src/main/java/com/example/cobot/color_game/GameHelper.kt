package com.example.cobot.color_game

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.cobot.bluetooth.HM10BluetoothHelper
import kotlinx.coroutines.delay

class GameHelper {
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun sendWinningFlashSequence(hm10helper: HM10BluetoothHelper) {
        repeat(3) {
            hm10helper.sendMessage("CG\r\n")
            delay(166)
            hm10helper.sendMessage("CF\r\n")
            delay(166)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun sendLoosing(hm10helper: HM10BluetoothHelper){
        repeat(3) {
            hm10helper.sendMessage("CR\r\n")
            delay(166)
            hm10helper.sendMessage("CF\r\n")
            delay(166)
        }
    }
}