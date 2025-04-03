package com.example.cobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

@Composable
fun EmotionDetectorScreen() {
    val context = LocalContext.current
    val interpreter = remember { loadModel(context) }

    var result by remember { mutableStateOf("Select an image") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var grayscaleBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val emotions = listOf("Angry", "Disgusted", "Fearful", "Happy", "Neutral", "Sad", "Surprised")

    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.surprise)
    }
    previewBitmap = bitmap

    Column(modifier = Modifier.padding(16.dp)) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Original Image", modifier = Modifier.size(150.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            previewBitmap?.let { bitmap ->
                val processedBitmap = preprocessImage1(bitmap)
                grayscaleBitmap = processedBitmap

                val input = ByteBuffer.allocateDirect(1 * 48 * 48 * 4) // 1 batch, 48x48, 1 channel, float32
                    .apply {
                        order(ByteOrder.nativeOrder())
                        for (y in 0 until 48) {
                            for (x in 0 until 48) {
                                val pixel = processedBitmap.getPixel(x, y)
                                val grayscale = (Color.red(pixel) / 255.0f) // Normalize to [0,1]
                                putFloat(grayscale)
                            }
                        }
                    }

                val output = Array(1) { FloatArray(7) }
                interpreter.run(input, output)

                val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
                result = if (maxIndex != -1) emotions[maxIndex] else "Unknown"
            }
        }) {
            Text("Run Model")
        }

        Spacer(modifier = Modifier.height(16.dp))

        grayscaleBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Grayscale Image", modifier = Modifier.size(150.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = result, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

fun loadModel1(context: Context): Interpreter {
    val assetFileDescriptor = context.assets.openFd("models/emotion_model_full.tflite")
    val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
    val fileChannel = fileInputStream.channel
    val startOffset = assetFileDescriptor.startOffset
    val declaredLength = assetFileDescriptor.declaredLength
    val mappedByteBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    return Interpreter(mappedByteBuffer)
}

fun preprocessImage1(bitmap: Bitmap): Bitmap {
    val size = min(bitmap.width, bitmap.height)
    val xOffset = (bitmap.width - size) / 2
    val yOffset = (bitmap.height - size) / 2

    val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
    val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 48, 48, true)
    val grayscaleBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

    for (y in 0 until 48) {
        for (x in 0 until 48) {
            val pixel = resizedBitmap.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val grayscale = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val grayPixel = Color.rgb(grayscale, grayscale, grayscale)
            grayscaleBitmap.setPixel(x, y, grayPixel)
        }
    }

    return grayscaleBitmap
}
