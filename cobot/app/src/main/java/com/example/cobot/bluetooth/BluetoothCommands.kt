package com.example.cobot.bluetooth


object BluetoothCommands {

    object Control {
        // Obstacle commands
        const val OBSTACLE_ON = "NO\r\n"
        const val OBSTACLE_OFF = "YE\r\n"

        // Joystick direction commands
        const val FORWARD = "FF\r\n"
        const val FORWARD_RIGHT = "FR\r\n"
        const val FORWARD_LEFT = "FL\r\n"
        const val BACKWARD = "BB\r\n"
        const val BACKWARD_RIGHT = "BR\r\n"
        const val BACKWARD_LEFT = "BL\r\n"
        const val RIGHT = "RR\r\n"
        const val LEFT = "LL\r\n"
        const val STOP = "SS\r\n"

        // Speed command prefix
        const val SPEED_PREFIX = "V\r\n"

        // Map joystick direction to command
        fun getDirectionCommand(direction: String): String {
            return when (direction) {
                "Forward" -> FORWARD
                "Forward Right" -> FORWARD_RIGHT
                "Forward Left" -> FORWARD_LEFT
                "Backward" -> BACKWARD
                "Backward Right" -> BACKWARD_RIGHT
                "Backward Left" -> BACKWARD_LEFT
                "Right" -> RIGHT
                "Left" -> LEFT
                else -> STOP // Center or unknown
            }
        }

        // Convert speed percentage to ASCII character
        fun getSpeedCommand(speed: Int): String {
            // Ensure speed is within valid range (0-100)
            val safeSpeed = speed.coerceIn(0, 100)

            // Convert speed directly to the ASCII character with that decimal value
            // For example, if speed is 99, we want ASCII character 'c' (decimal 99)
            val speedChar = safeSpeed.toChar()

            return SPEED_PREFIX + speedChar
        }
    }

    // Effects screen commands
    object Effects {
        // Pump commands
        const val PUMP_ON = "CZ\r\n"

        // Arm commands
        const val ARM_ON = "AR\r\n"

        // LED commands
        const val LED_OFF = "CO\r\n"
        const val LED_BLUE = "CB\r\n"
        const val LED_RED = "CR\r\n"
        const val LED_GREEN = "CG\r\n"
        const val LED_BLACK = "CA\r\n"
    }

    // Auto mode commands
    object Auto {
        // Auto mode ON
        const val AUTO_ON = "AO\r\n"
        // Auto mode OFF
        const val AUTO_OFF = "AF\r\n"
    }

    object Emotion {
        const val HAPPY = "HA\r\n"
        const val ANGRY ="AN\r\n"
        const val SAD = "SD\r\n"
        const val SURPRISED = "SP\r\n"
    }
}
