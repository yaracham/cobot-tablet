package com.example.cobot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.Image
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun LiveEmotionDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val interpreter = remember { loadModel(context) }
    val emotions = listOf("Angry", "Disgust", "Fear", "Surprise", "Happy", "Neutral", "Sad")
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(context, lifecycleOwner, cameraExecutor) { bitmap ->
            val faceBitmap = detectAndCropFace(bitmap, context) ?: return@CameraPreview
            val inputBuffer = preprocessImage(faceBitmap)
            val output = Array(1) { FloatArray(7) }
            interpreter.run(inputBuffer, output)
            val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            detectedEmotion = if (maxIndex != -1) emotions[maxIndex] else "Unknown"
        }
        Text(text = "Emotion: $detectedEmotion", modifier = Modifier.padding(16.dp))
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    onFrameCaptured: (Bitmap) -> Unit
) {
    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val bitmap = imageProxyToBitmap(imageProxy, context)
                    ctx.mainExecutor.execute {
                        onFrameCaptured(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Image analysis error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
    })
}

@OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy, context: Context): Bitmap {
    // Get the YUV image
    val image = imageProxy.image ?: throw IllegalStateException("Image proxy has no image")

    // Convert YUV to RGB
    val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)

    // Use YuvToRgbConverter or direct conversion
    val yuvToRgbConverter = YuvToRgbConverter(context, bitmap)
    yuvToRgbConverter.yuvToRgb(image, bitmap)

    return bitmap
}

// YUV to RGB converter helper class
class YuvToRgbConverter(private val context: Context, private val bitmap: Bitmap) {
    private val rs = android.renderscript.RenderScript.create(context)
    private val scriptYuvToRgb = android.renderscript.ScriptIntrinsicYuvToRGB.create(rs, android.renderscript.Element.U8_4(rs))

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
            yuvMat = android.renderscript.Allocation.createSized(rs, android.renderscript.Element.U8(rs), totalSize)
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
            image.planes[2].buffer.get(uvBuffer, image.planes[1].buffer.remaining(), image.planes[2].buffer.remaining())
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

fun detectAndCropFace(bitmap: Bitmap, context: Context): Bitmap? {
    try {
        // Create MediaPipe FaceDetector
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_detection_short_range.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.5f)
            .build()

        val faceDetector = FaceDetector.createFromOptions(context, options)

        // Convert bitmap to MediaPipe image
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Run detection
        val result = faceDetector.detect(mpImage)

        // Close resources
        faceDetector.close()
        mpImage.close()

        // If no faces detected, return null
        if (result.detections().isEmpty()) return null

        // Get the first face bounding box
        val face = result.detections()[0].boundingBox()

        // Ensure bounding box is within bitmap bounds
        val left = 0.coerceAtLeast(face.left.toInt())
        val top = 0.coerceAtLeast(face.top.toInt())
        val width = (bitmap.width - left).coerceAtMost(face.width().toInt())
        val height = (bitmap.height - top).coerceAtMost(face.height().toInt())

        // Crop the face from the bitmap
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("FaceDetection", "Error detecting face", e)
        return null
    }
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
                val gray = Color.red(pixel) / 255.0f  // Since it's grayscale, we can use any channel
                putFloat(gray)
            }
        }
        rewind() // Reset position to beginning of buffer
    }
}

fun loadModel(context: Context): Interpreter {
    try {
        val fileDescriptor = context.assets.openFd("models/emotion_model_full.tflite")
        val fileStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileStream.channel
        val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        return Interpreter(mappedByteBuffer)
    } catch (e: Exception) {
        Log.e("ModelLoading", "Error loading model", e)
        throw RuntimeException("Error loading model: ${e.message}")
    }
}

