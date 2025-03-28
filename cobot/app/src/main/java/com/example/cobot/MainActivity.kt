package com.example.cobot

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.cobot.ui.theme.CobotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CobotTheme {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this))
                }

                val py = Python.getInstance()
                val pyObject = py.getModule("test_fer")

                val result = pyObject.callAttr("detect_emotion").toString()

                Log.d("EmotionDetector", "Detected Emotion: $result")
            }
        }
    }
}
