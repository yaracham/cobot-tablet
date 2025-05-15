package com.example.cobot.emotion_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.Image
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.MutableState
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// YUV to RGB converter helper class
class YuvToRgbConverter(private val context: Context, private val bitmap: Bitmap) {
    private val rs = android.renderscript.RenderScript.create(context)
    private val scriptYuvToRgb = android.renderscript.ScriptIntrinsicYuvToRGB.create(
        rs,
        android.renderscript.Element.U8_4(rs)
    )

    private var yuvBuffer: ByteBuffer? = null
    private var rgbBuffer: ByteBuffer? = null
    private var yuvMat: android.renderscript.Allocation? = null
    private var rgbMat: android.renderscript.Allocation? = null
    private var width = 0
    private var height = 0

    fun yuvToRgb(image: Image, output: Bitmap) {
        // Dynamically calculate the required buffer size
        val ySize = image.planes[0].buffer.remaining()
        val uSize = image.planes[1].buffer.remaining()
        val vSize = image.planes[2].buffer.remaining()
        val totalSize = ySize + uSize + vSize

        // Allocate or reallocate buffer if needed
        if (yuvBuffer == null || (yuvBuffer?.capacity() ?: 0) < totalSize) {
            yuvBuffer = ByteBuffer.allocateDirect(totalSize)
        }

        // Create allocations if needed (this example keeps the rest of your logic)
        if (yuvMat == null || width != image.width || height != image.height) {
            width = image.width
            height = image.height

            rgbBuffer = ByteBuffer.allocateDirect(width * height * 4)
            yuvMat = android.renderscript.Allocation.createSized(
                rs,
                android.renderscript.Element.U8(rs),
                totalSize
            )
            rgbMat = android.renderscript.Allocation.createFromBitmap(rs, output)
        }

        // Copy YUV data to the buffer
        yuvBuffer?.clear()
        yuvBuffer?.put(image.planes[0].buffer)

        // Reset the positions for U and V buffers before reading
        image.planes[1].buffer.rewind()
        image.planes[2].buffer.rewind()

        // Handle interleaving depending on pixel stride
        val uPixelStride = image.planes[1].pixelStride
        val vPixelStride = image.planes[2].pixelStride

        if (uPixelStride == 2 && vPixelStride == 2) {
            val uvSize = image.planes[1].buffer.remaining() + image.planes[2].buffer.remaining()
            val uvBuffer = ByteArray(uvSize)
            image.planes[1].buffer.get(uvBuffer, 0, image.planes[1].buffer.remaining())
            image.planes[2].buffer.get(
                uvBuffer,
                image.planes[1].buffer.remaining(),
                image.planes[2].buffer.remaining()
            )
            yuvBuffer?.put(uvBuffer)
        } else {
            yuvBuffer?.put(image.planes[1].buffer)
            yuvBuffer?.put(image.planes[2].buffer)
        }

        // Convert YUV to RGB
        yuvBuffer?.rewind()
        val byteArray = yuvBuffer?.let { ByteArray(it.remaining()) }
        if (byteArray != null) {
            yuvBuffer?.get(byteArray)
        }
        yuvMat?.copyFrom(byteArray)
        scriptYuvToRgb.setInput(yuvMat)
        scriptYuvToRgb.forEach(rgbMat)
        rgbMat?.copyTo(output)
    }


    fun release() {
        yuvMat?.destroy()
        rgbMat?.destroy()
        scriptYuvToRgb.destroy()
        rs.destroy()
    }
}

@OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy, context: Context): Bitmap {
    val image = imageProxy.image ?: throw IllegalStateException("Image proxy has no image")
    val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
    val yuvToRgbConverter = YuvToRgbConverter(context, bitmap)
    yuvToRgbConverter.yuvToRgb(image, bitmap)
    return bitmap
}

fun preprocessImage(bitmap: Bitmap): ByteBuffer {
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    val grayscaleBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(grayscaleBitmap)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)

    return ByteBuffer.allocateDirect(48 * 48 * 4).apply {
        order(ByteOrder.nativeOrder())
        for (y in 0 until 48) {
            for (x in 0 until 48) {
                val pixel = grayscaleBitmap.getPixel(x, y)
                val gray = Color.red(pixel) / 255.0f
                putFloat(gray)
            }
        }
        rewind()
    }
}

fun loadModel(context: Context): Interpreter {
    try {
        val assetManager = context.assets
        val modelPath = "models/keypoint_classifier.tflite"
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )

        val options = Interpreter.Options().apply { numThreads = 4 }
        return Interpreter(mappedByteBuffer, options)
    } catch (e: Exception) {
        Log.e("ModelLoading", "Error loading model: ${e.message}", e)
        throw RuntimeException("Error loading model: ${e.message}")
    }
}

fun createFaceLandmarker(context: Context): FaceLandmarker? {
    try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/face_landmarker_v2_with_blendshapes.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputFaceBlendshapes(true) // Enable blendshapes output
            .setNumFaces(1) // Process only one face for efficiency
            .build()

        return FaceLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        Log.e("FaceLandmarker", "Error creating face landmarker", e)
        return null
    }
}

fun processFaceWithLandmarker(
    faceLandmarker: FaceLandmarker?,
    mpImage: MPImage
): FaceLandmarkerResult? {
    return faceLandmarker?.detect(mpImage)
}

fun classifyEmotionFromBlendshapes(
    blendshapeList: List<List<Category>>?,
    debugText: MutableState<String>
): String {
    val blendshapes = mutableMapOf<String, Float>()
    Log.d("Blendshapes", blendshapes.toString())

    // Flatten all the categories from each inner list
    blendshapeList?.forEach { categoryList ->
        categoryList.forEach { category ->
            blendshapes[category.categoryName()] = category.score()
        }
    }

    val smile = ((blendshapes["mouthSmileLeft"] ?: 0f) + (blendshapes["mouthSmileRight"] ?: 0f)) / 2
    val frown = ((blendshapes["mouthFrownLeft"] ?: 0f) + (blendshapes["mouthFrownRight"] ?: 0f)) / 2
    val browDown = ((blendshapes["browDownLeft"] ?: 0f) + (blendshapes["browDownRight"] ?: 0f)) / 2
    val browUp = blendshapes["browInnerUp"] ?: 0f
    val eyeWide = ((blendshapes["eyeWideLeft"] ?: 0f) + (blendshapes["eyeWideRight"] ?: 0f)) / 2
    val mouthOpen = blendshapes["jawOpen"] ?: 0f
    val cheekPuff = blendshapes["cheekPuff"] ?: 0f
    val mouthShrugLower = blendshapes["mouthShrugLower"] ?: 0f
    val eyeSquint =
        ((blendshapes["eyeSquintLeft"] ?: 0f) + (blendshapes["eyeSquintRight"] ?: 0f)) / 2
    val mouthPress =
        ((blendshapes["mouthPressLeft"] ?: 0f) + (blendshapes["mouthPressRight"] ?: 0f)) / 2
    val angryScore = listOf(frown, browDown, eyeSquint, mouthPress).count { it > 0.1f }
//    val mouthFrown =
//        ((blendshapes["mouthFrownLeft"] ?: 0f) + (blendshapes["mouthFrownRight"] ?: 0f)) / 2
    val browInnerUp = blendshapes["browInnerUp"] ?: 0f
    val mouthFrown = (blendshapes["mouthFrownLeft"]!! + blendshapes["mouthFrownRight"]!!) / 2f

    debugText.value = """
    Smile: ${"%.2f".format(smile)}
    Frown: ${"%.2f".format(frown)}
    BrowDown: ${"%.2f".format(browDown)}
    BrowUp: ${"%.2f".format(browUp)}
    EyeWide: ${"%.2f".format(eyeWide)}
    MouthOpen: ${"%.2f".format(mouthOpen)}
    CheekPuff: ${"%.2f".format(cheekPuff)}
    EyeSquint: ${"%.2f".format(eyeSquint)}
    MouthPress: ${"%.2f".format(mouthPress)}
    MouthFrown: ${"%.2f".format(mouthFrown)} 
    BrowInnerUp: ${"%.2f".format(browInnerUp)} 
    """.trimIndent()

    Log.d("DEBUGGGGG", debugText.value)
    val emotion = when {
        smile > 0.2f -> "Happy"
        eyeSquint >= 0.35f -> "Angry"
        mouthOpen > 0.2f -> "Surprised"
        mouthShrugLower > 0.7f -> "Sad"
        else -> "Neutral"
    }
    blendshapes["mouthSmileLeft"] = 0f
    blendshapes["mouthSmileRight"] = 0f
    blendshapes["mouthFrownLeft"] = 0f
    blendshapes["mouthFrownRight"] = 0f
    blendshapes["browDownLeft"] = 0f
    blendshapes["browDownRight"] = 0f
    blendshapes["browInnerUp"] = 0f
    blendshapes["eyeWideLeft"] = 0f
    blendshapes["eyeWideRight"] = 0f
    blendshapes["jawOpen"] = 0f
    blendshapes["cheekPuff"] = 0f
    blendshapes["mouthShrugLower"] = 0f
    blendshapes["eyeSquintLeft"] = 0f
    blendshapes["eyeSquintRight"] = 0f
    blendshapes["mouthPressLeft"] = 0f
    blendshapes["mouthPressRight"] = 0f
    return emotion
}






