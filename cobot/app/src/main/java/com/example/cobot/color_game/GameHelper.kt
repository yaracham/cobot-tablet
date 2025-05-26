package com.example.cobot.color_game
/**
 * This file defines the `GameHelper` class which handles Bluetooth communication
 * for visual feedback sequences during the color game.
 *
 * It sends commands to an HM-10 Bluetooth module to trigger LED patterns that reflect:
 * - A win (`sendWinningFlashSequence`)
 * - A loss (`sendLoosing`)
 * - A new high score celebration (`sendHighScoreCelebration`)
 *
 * Each function uses delays to control the timing of LED color changes.
 * Requires API level 31 (Android S) or higher.
 */

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
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun sendHighScoreCelebration(hm10helper: HM10BluetoothHelper) {
        hm10helper.sendMessage("CM\r\n")
        delay(2000)
        hm10helper.sendMessage("CF\r\n")
    }
}