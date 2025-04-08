package com.example.cobot

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.Image
import android.os.Build
import android.os.Looper
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun LiveEmotionDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Add logging for model loading
    val interpreter = remember {
        Log.d("EmotionDetection", "Loading model...")
        try {
            val model = loadModel(context)
            Log.d("EmotionDetection", "Model loaded successfully")
            model
        } catch (e: Exception) {
            Log.e("EmotionDetection", "Failed to load model", e)
            throw e
        }
    }

    val emotions = listOf("Angry", "Disgust", "Fear", "Surprise", "Happy", "Neutral", "Sad")
    var detectedEmotion by remember { mutableStateOf("Detecting...") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // State to trigger frame capture
    var captureFrame by remember { mutableIntStateOf(0) }

    // This is the correct way to use LaunchedEffect in Compose
    LaunchedEffect(Unit) {
        Log.d("EmotionDetection", "Starting frame capture loop")
        while(true) {
            delay(2000) // 2 seconds interval
            captureFrame++ // Trigger a new frame capture
            Log.d("EmotionDetection", "Triggering frame capture: $captureFrame")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("EmotionDetection", "Shutting down camera executor")
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The CameraPreview is now called directly in the Composable context
        CameraPreview(
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            captureFrame = captureFrame
        ) { bitmap ->
            Log.d("EmotionDetection", "Frame received: ${bitmap.width}x${bitmap.height}")

            try {
                val faceBitmap = detectAndCropFace(bitmap, context)
                if (faceBitmap == null) {
                    Log.d("EmotionDetection", "No face detected")
                    return@CameraPreview
                }

                Log.d("EmotionDetection", "Face detected: ${faceBitmap.width}x${faceBitmap.height}")
                val inputBuffer = preprocessImage(bitmap)

                try {
                    val output = Array(1) { FloatArray(7) }
                    interpreter.run(inputBuffer, output)

                    // Log the raw emotion scores
                    val scoresStr = output[0].mapIndexed { index, score ->
                        "${emotions[index]}: $score"
                    }.joinToString(", ")
                    Log.d("EmotionDetection", "Emotion scores: $scoresStr")

                    val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
                    detectedEmotion = if (maxIndex != -1) emotions[maxIndex] else "Unknown"
                    Log.d("EmotionDetection", "Detected emotion: $detectedEmotion")
                } catch (e: Exception) {
                    Log.e("EmotionDetection", "Error running inference", e)
                }
            } catch (e: Exception) {
                Log.e("EmotionDetection", "Error processing frame", e)
            }
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
    captureFrame: Int,
    onFrameCaptured: (Bitmap) -> Unit
) {
    // Store the current captureFrame value to access it in the analyzer
    val currentCaptureFrame = remember { mutableIntStateOf(captureFrame) }

    // Update the stored value when captureFrame changes
    LaunchedEffect(captureFrame) {
        currentCaptureFrame.intValue = captureFrame
        Log.d("CameraPreview", "Capture frame updated to: $captureFrame")
    }

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

            // Add a variable to track the last processed frame
            var lastProcessedFrame = 0

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    // Get the current capture frame value
                    val frameToCapture = currentCaptureFrame.intValue

                    // Only process if this is a new frame request
                    if (frameToCapture > lastProcessedFrame) {
                        Log.d("CameraPreview", "Processing frame: $frameToCapture (last: $lastProcessedFrame)")
                        lastProcessedFrame = frameToCapture

                        val bitmap = imageProxyToBitmap(imageProxy, context)
                        ctx.mainExecutor.execute {
                            onFrameCaptured(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Image analysis error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.d("CameraPreview", "Camera bound to lifecycle")
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
    })
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
        val modelPath = "models/emotion_model_full.tflite"
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


fun detectAndCropFace(originalBitmap: Bitmap, context: Context): Bitmap? {
    // Validate input bitmap
    if (originalBitmap.isRecycled) {
        Log.e("FaceDetection", "Source bitmap is already recycled")
        return null
    }

    // Create TWO copies - one for MediaPipe, one for our operations
    val mpBitmap = originalBitmap.copy(originalBitmap.config, true)
    val workingBitmap = originalBitmap.copy(originalBitmap.config, true)

    try {
        Log.d("FaceDetection", "Processing bitmap: ${originalBitmap.width}x${originalBitmap.height}")

        // Face detection model setup
        val faceDetectionModelPath = "models/face_detection_short_range.tflite"

        // Verify model exists
        try {
            context.assets.openFd(faceDetectionModelPath).use { fd ->
                Log.d("FaceDetection", "Model found: ${fd.length} bytes")
            }
        } catch (e: Exception) {
            Log.e("FaceDetection", "Model not found", e)
            return null
        }

        // Configure face detector
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder()
                .setModelAssetPath(faceDetectionModelPath)
                .build())
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.3f)
            .build()

        // Create and use face detector
        val faceDetector = FaceDetector.createFromOptions(context, options)

        try {
            // Create MediaPipe image with the dedicated bitmap
            val mpImage = BitmapImageBuilder(mpBitmap).build()

            try {
                // Run face detection
                val startTime = System.currentTimeMillis()
                val result = faceDetector.detect(mpImage)
                Log.d("FaceDetection", "Detection took ${System.currentTimeMillis() - startTime}ms")

                // Process results
                if (result.detections().isEmpty()) {
                    Log.d("FaceDetection", "No faces detected")
                    return null
                }

                // Get first face bounding box
                val face = result.detections()[0].boundingBox()
                val left = 0.coerceAtLeast(face.left.toInt())
                val top = 0.coerceAtLeast(face.top.toInt())
                val right = workingBitmap.width.coerceAtMost(face.right.toInt())
                val bottom = workingBitmap.height.coerceAtMost(face.bottom.toInt())
                val width = right - left
                val height = bottom - top

                if (width <= 0 || height <= 0) {
                    Log.e("FaceDetection", "Invalid face dimensions: $width x $height")
                    return null
                }

                // Crop face region from our working bitmap
                if (workingBitmap.isRecycled) {
                    Log.e("FaceDetection", "Working bitmap was recycled prematurely")
                    return null
                }

                return Bitmap.createBitmap(workingBitmap, left, top, width, height).also {
                    Log.d("FaceDetection", "Cropped face: ${it.width}x${it.height}")
                }
            } finally {
                mpImage.close()
                mpBitmap.recycle() // Recycle the MediaPipe-specific bitmap
            }
        } finally {
            faceDetector.close()
        }
    } catch (e: Exception) {
        Log.e("FaceDetection", "Error in face detection", e)
        return null
    } finally {
        // Always clean up our working copy
        if (!workingBitmap.isRecycled) {
            workingBitmap.recycle()
        }
    }
}


fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String) {
    Log.d("hereeeeeeeeee=========","entered bitmap saving function")
    // Get your external files directory in a "debug" subfolder.
    val debugDir = File(context.getExternalFilesDir(null), "debug")
    if (!debugDir.exists()) {
        debugDir.mkdirs()
    }
    val file = File(debugDir, fileName)
    try {
        FileOutputStream(file).use { out ->
            // Save as PNG with full quality
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.d("DEBUG", "Bitmap saved to: ${file.absolutePath}")
    } catch (e: Exception) {
        Log.e("DEBUG", "Error saving bitmap", e)
    }
}


