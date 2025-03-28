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
import androidx.compose.ui.unit.dp
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Composable
fun EmotionDetectorScreen() {
    val context = LocalContext.current
    val interpreter = remember { loadModel(context) }

    var result by remember { mutableStateOf("Select an image") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.happy_guy)
    }
    previewBitmap = bitmap

    Column(modifier = Modifier.padding(16.dp)) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Test Image", modifier = Modifier.size(150.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            previewBitmap?.let { bitmap ->
                val input = preprocessImage(bitmap)
                val output = Array(1) { FloatArray(7) }
                interpreter.run(input, output)
                result = output[0].joinToString()
            }
        }) {
            Text("Run Model")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = result)
    }
}


fun loadModel(context: Context): Interpreter {
    val assetFileDescriptor = context.assets.openFd("model.tflite")
    val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
    val fileChannel = fileInputStream.channel
    val startOffset = assetFileDescriptor.startOffset
    val declaredLength = assetFileDescriptor.declaredLength
    val mappedByteBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    return Interpreter(mappedByteBuffer)
}

fun preprocessImage(bitmap: Bitmap): ByteBuffer {
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)

    val grayscaleArray = ByteBuffer.allocateDirect(48 * 48 * 4)
    grayscaleArray.order(ByteOrder.nativeOrder())

    for (y in 0 until 48) {
        for (x in 0 until 48) {
            val pixel = resizedBitmap.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val grayscale = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0f
            grayscaleArray.putFloat(grayscale.toFloat())
        }
    }

    return grayscaleArray
}
