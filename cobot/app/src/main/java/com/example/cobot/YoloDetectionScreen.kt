package com.example.cobot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

private const val TAG = "YoloDetectionScreen"
private const val MODEL_FILE = "models/yolov5s.tflite"
private const val DETECTION_THRESHOLD = 0.5f
private const val PERSON_CLASS_ID = 0 // In COCO dataset, person is class 0

data class Detection(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int,
    val className: String
)

@Composable
fun YoloDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedPosition by remember { mutableStateOf("Detecting...") }
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }

    // Create an executor for background tasks
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Initialize the YOLOv5 detector
    val yoloDetector = remember { YoloV5Detector(context) }

    // Clean up when leaving the composition
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            yoloDetector.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with bounding box overlay
        CameraWithBoundingBox(
            modifier = Modifier.fillMaxSize(),
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            yoloDetector = yoloDetector,
            onDetectionsUpdated = { newDetections ->
                detections = newDetections
                // Calculate position based on the person detection
                detectedPosition = calculatePosition(newDetections)
            }
        )

        // Draw bounding boxes
        BoundingBoxOverlay(
            detections = detections,
            modifier = Modifier.fillMaxSize()
        )

        // Animated eyes overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        ) {
            EyesAnimation(
                position = detectedPosition,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.Center)
                    .background(ComposeColor.Black.copy(alpha = 0.3f))
            )
        }

        // Position indicator
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = "YOLO Position: $detectedPosition",
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun BoundingBoxOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 8f
            textSize = 48f
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 48f
        }

        val textBackgroundPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            alpha = 128
        }

        drawContext.canvas.nativeCanvas.apply {
            detections.forEach { detection ->
                // Scale the bounding box to the canvas size
                val scaledBox = RectF(
                    detection.boundingBox.left * size.width,
                    detection.boundingBox.top * size.height,
                    detection.boundingBox.right * size.width,
                    detection.boundingBox.bottom * size.height
                )

                // Draw bounding box
                drawRect(scaledBox, paint)

                // Draw label background
                val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
                val textWidth = textPaint.measureText(label)
                val textHeight = 60f
                val textBackground = RectF(
                    scaledBox.left,
                    scaledBox.top - textHeight,
                    scaledBox.left + textWidth + 10f,
                    scaledBox.top
                )
                drawRect(textBackground, textBackgroundPaint)

                // Draw label text
                drawText(
                    label,
                    scaledBox.left + 5f,
                    scaledBox.top - 15f,
                    textPaint
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraWithBoundingBox(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    yoloDetector: YoloV5Detector,
    onDetectionsUpdated: (List<Detection>) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_START
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Set up the image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageWithYolo(
                                imageProxy,
                                yoloDetector,
                                onDetectionsUpdated
                            )
                        }
                    }

                // Select front camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageWithYolo(
    imageProxy: ImageProxy,
    yoloDetector: YoloV5Detector,
    onDetectionsUpdated: (List<Detection>) -> Unit
) {
    imageProxy.use { proxy ->
        val mediaImage = proxy.image ?: return@use

        try {
            // Convert the image to a bitmap
            val bitmap = mediaImageToBitmap(mediaImage)
            val rotatedBitmap = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())

            // Detect objects using YOLOv5
            val detections = yoloDetector.detect(rotatedBitmap)

            // Filter for person detections only
            val personDetections = detections.filter { it.classId == PERSON_CLASS_ID }

            // Update the UI with the detections
            onDetectionsUpdated(personDetections)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with YOLO: ${e.message}")
        }
    }
}

private fun calculatePosition(detections: List<Detection>): String {
    // If no person is detected, return "No Person"
    if (detections.isEmpty()) {
        return "No Person"
    }

    // Get the person with highest confidence
    val person = detections.maxByOrNull { it.confidence } ?: return "No Person"

    // Calculate the center X coordinate of the bounding box (normalized 0-1)
    val centerX = (person.boundingBox.left + person.boundingBox.right) / 2

    // Determine position based on the center X coordinate
    return when {
        centerX < 0.4f -> "LEFT"
        centerX > 0.6f -> "RIGHT"
        else -> "CENTER"
    }
}

class YoloV5Detector(private val context: Context) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 640 // YOLOv5s input size

    init {
        // Load the model
        val options = Interpreter.Options().apply {
            // Use GPU if available
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate())
            }
            numThreads = 4
        }

        interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE), options)

        // Load COCO labels
        labels = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        // Prepare input image
        val inputTensor = preprocessImage(bitmap)

        // Prepare output buffer
        // YOLOv5s output shape is [1, 25200, 85] where 85 = 4 (bbox) + 1 (confidence) + 80 (classes)
        val outputBuffer = Array(1) {
            Array(25200) {
                FloatArray(85)
            }
        }

        // Run inference
        interpreter.run(inputTensor, outputBuffer)

        // Process results
        return postprocessResults(outputBuffer[0], bitmap.width, bitmap.height)
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Resize the bitmap to the model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Check the model's input tensor shape to determine the correct buffer size
        val inputShape = interpreter.getInputTensor(0).shape()
        Log.d(TAG, "Model input shape: ${inputShape.contentToString()}")

        // Calculate buffer size based on the model's input tensor
        val bufferSize = inputShape.fold(1, Int::times) * 4 // 4 bytes per float
        Log.d(TAG, "Allocating buffer of size: $bufferSize bytes")

        // Allocate a ByteBuffer for the input tensor
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Normalize and convert the bitmap to the input tensor
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // YOLOv5 expects input in the range [0, 1]
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                // Normalize pixel values to [0, 1]
                inputBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun postprocessResults(
        outputData: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val detections = ArrayList<Detection>()

        // Process each detection
        for (i in outputData.indices) {
            val confidence = outputData[i][4]

            // Skip low confidence detections
            if (confidence < DETECTION_THRESHOLD) continue

            // Find the class with highest probability
            var maxClassProb = 0f
            var classId = -1

            for (j in 5 until outputData[i].size) {
                val classProb = outputData[i][j]
                if (classProb > maxClassProb) {
                    maxClassProb = classProb
                    classId = j - 5
                }
            }

            // Skip if no class is detected with high probability
            if (classId == -1 || maxClassProb < DETECTION_THRESHOLD) continue

            // Calculate the combined confidence
            val combinedConfidence = confidence * maxClassProb

            // Extract bounding box coordinates (normalized 0-1)
            val x = outputData[i][0] / inputSize
            val y = outputData[i][1] / inputSize
            val w = outputData[i][2] / inputSize
            val h = outputData[i][3] / inputSize

            // Convert to corners format (left, top, right, bottom)
            val left = max(0f, x - w / 2)
            val top = max(0f, y - h / 2)
            val right = min(1f, x + w / 2)
            val bottom = min(1f, y + h / 2)

            // Create detection object
            val detection = Detection(
                boundingBox = RectF(left, top, right, bottom),
                confidence = combinedConfidence,
                classId = classId,
                className = if (classId < labels.size) labels[classId] else "unknown"
            )

            detections.add(detection)
        }

        // Apply non-maximum suppression to remove overlapping boxes
        return nms(detections)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        // Group detections by class
        val detectionsByClass = detections.groupBy { it.classId }
        val result = ArrayList<Detection>()

        // Apply NMS for each class
        for ((_, classDetections) in detectionsByClass) {
            // Sort by confidence
            val sortedDetections = classDetections.sortedByDescending { it.confidence }
            val selectedDetections = ArrayList<Detection>()

            while (sortedDetections.isNotEmpty()) {
                // Select the detection with highest confidence
                val detection = sortedDetections[0]
                selectedDetections.add(detection)

                // Remove the selected detection
                val rest = sortedDetections.drop(1).toMutableList()

                // Remove detections that have high overlap with the selected one
                val iterator = rest.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (calculateIoU(detection.boundingBox, next.boundingBox) > 0.5) {
                        iterator.remove()
                    }
                }

                val remaining = rest.filter {
                    calculateIoU(detection.boundingBox, it.boundingBox) <= 0.5f
                }
                if (remaining.isEmpty()) break
            }

            result.addAll(selectedDetections)
        }

        return result
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val areaBox1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val areaBox2 = (box2.right - box2.left) * (box2.bottom - box2.top)

        val intersectLeft = max(box1.left, box2.left)
        val intersectTop = max(box1.top, box2.top)
        val intersectRight = min(box1.right, box2.right)
        val intersectBottom = min(box1.bottom, box2.bottom)

        val intersectWidth = max(0f, intersectRight - intersectLeft)
        val intersectHeight = max(0f, intersectBottom - intersectTop)
        val intersectArea = intersectWidth * intersectHeight

        return intersectArea / (areaBox1 + areaBox2 - intersectArea)
    }

    fun close() {
        interpreter.close()
    }
}

// Helper functions for image processing (reused from previous implementation)
private fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    return try {
        val planes = mediaImage.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize).apply {
            yBuffer.get(this, 0, ySize)
            vBuffer.get(this, ySize, vSize)
            uBuffer.get(this, ySize + vSize, uSize)
        }

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            mediaImage.width,
            mediaImage.height,
            null
        )

        val out = ByteArrayOutputStream().apply {
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, mediaImage.width, mediaImage.height),
                100,
                this
            )
        }

        BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())?.also {
            if (it.isRecycled) {
                throw IllegalStateException("Bitmap was recycled")
            }
        } ?: throw IllegalStateException("Failed to decode bitmap")
    } catch (e: Exception) {
        Log.e(TAG, "Error converting Image to Bitmap: ${e.message}")
        throw e
    }
}

private fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotation)
    // Mirror for front camera
    matrix.postScale(-1f, 1f)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

