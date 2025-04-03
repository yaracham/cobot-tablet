package com.example.cobot

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.*

class PersonFollower(private val context: Context) {
    // TFLite interpreter
    private var interpreter: Interpreter? = null

    // Model parameters
    private val modelPath = "yolov5s.tflite"
    private val inputSize = 640 // YOLOv5s input size
    private val numClasses = 80 // COCO dataset has 80 classes
    private val personClassId = 0 // Person class ID in COCO dataset

    // Detection parameters
    private val confidenceThreshold = 0.5f
    private val iouThreshold = 0.45f

    // State for UI
    private var detectionResults = mutableStateOf<List<DetectionResult>>(emptyList())

    init {
        try {
            // Load the TFLite model
            val tfliteModel = loadModelFile()

            // Configure the interpreter
            val options = Interpreter.Options().apply {
                // Use GPU if available
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    this.addDelegate(GpuDelegate(delegateOptions))
                } else {
                    // Number of threads for the interpreter
                    this.setNumThreads(4)
                }
            }

            // Create the interpreter
            interpreter = Interpreter(tfliteModel, options)

            Log.d("PersonFollower", "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e("PersonFollower", "Error loading TFLite model", e)
        }
        Log.d("PersonFollower", "Initialized simple person follower")
    }

    private fun loadModelFile(): ByteBuffer {
        try {
            // Open the model file from assets
            val fileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength

            // Load the model into a ByteBuffer
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            Log.d("PersonFollower", "Model file loaded successfully")
            return modelBuffer
        } catch (e: Exception) {
            Log.e("PersonFollower", "Error loading model file", e)
            throw e
        }
    }

    fun processFrame(inputBitmap: Bitmap): Bitmap {
        try {
            // Resize and prepare the input bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, inputSize, inputSize, true)

            // Prepare input tensor
            val inputBuffer = prepareInputBuffer(resizedBitmap)

            // Run inference
            val outputBuffer = runInference(inputBuffer)

            // Process the output to get detection results
            val detections = processOutput(outputBuffer)

            // Filter for person detections only
            val personDetections = detections.filter { it.classId == personClassId }

            // Update state
            detectionResults.value = personDetections

            // Draw detections on the original image
            return drawDetections(inputBitmap, personDetections)
        } catch (e: Exception) {
            Log.e("PersonFollower", "Error processing frame", e)

            // Return a simple error bitmap instead of crashing
            val errorBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(errorBitmap)
            val paint = Paint().apply {
                color = Color.RED
                textSize = 40f
            }
            canvas.drawColor(Color.BLACK)
            canvas.drawText("Error: ${e.message}", 20f, 100f, paint)
            canvas.drawText("Check logs for details", 20f, 150f, paint)

            return errorBitmap
        }
    }

    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {
        // Allocate a ByteBuffer for the input tensor
        val inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())

        // Get pixel values
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Normalize pixel values and add to buffer
        for (pixelValue in pixels) {
            // Extract RGB values (0-255)
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Normalize to 0-1 range and add to buffer
            inputBuffer.putFloat(r / 255.0f)
            inputBuffer.putFloat(g / 255.0f)
            inputBuffer.putFloat(b / 255.0f)
        }

        // Reset position to start
        inputBuffer.rewind()

        return inputBuffer
    }

    private fun runInference(inputBuffer: ByteBuffer): Array<Array<FloatArray>> {
        // Prepare output tensor
        // YOLOv5s output shape is [1, 25200, 85] where:
        // - 25200 is the number of predictions (anchors)
        // - 85 is [x, y, w, h, confidence, 80 class probabilities]
        val outputShape = arrayOf(1, 25200, 85)
        val outputBuffer = Array(outputShape[0]) {
            Array(outputShape[1]) {
                FloatArray(outputShape[2])
            }
        }

        // Run inference
        interpreter?.run(inputBuffer, outputBuffer)

        return outputBuffer
    }

    private fun processOutput(outputBuffer: Array<Array<FloatArray>>): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        // Process each prediction
        for (i in 0 until outputBuffer[0].size) {
            val prediction = outputBuffer[0][i]

            // Get confidence score
            val confidence = prediction[4]

            // Skip low confidence detections
            if (confidence < confidenceThreshold) continue

            // Find the class with highest probability
            var maxClassProb = 0f
            var classId = -1

            for (c in 0 until numClasses) {
                val classProb = prediction[5 + c]
                if (classProb > maxClassProb) {
                    maxClassProb = classProb
                    classId = c
                }
            }

            // Skip if no class found or class probability is low
            if (classId == -1 || maxClassProb < confidenceThreshold) continue

            // Calculate the final confidence
            val finalConfidence = confidence * maxClassProb

            // Skip low final confidence detections
            if (finalConfidence < confidenceThreshold) continue

            // Get bounding box coordinates (normalized 0-1)
            val x = prediction[0] // center x
            val y = prediction[1] // center y
            val w = prediction[2] // width
            val h = prediction[3] // height

            // Convert to top-left, bottom-right format (still normalized 0-1)
            val xmin = x - w / 2
            val ymin = y - h / 2
            val xmax = x + w / 2
            val ymax = y + h / 2

            // Add to detections list
            detections.add(
                DetectionResult(
                    classId = classId,
                    confidence = finalConfidence,
                    boundingBox = RectF(xmin, ymin, xmax, ymax)
                )
            )
        }

        // Apply non-maximum suppression to remove overlapping boxes
        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        // Sort by confidence (descending)
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<DetectionResult>()

        // Keep track of which detections to keep
        val keep = BooleanArray(sortedDetections.size) { true }

        for (i in sortedDetections.indices) {
            // Skip if this detection is already removed
            if (!keep[i]) continue

            // Add this detection to the selected list
            selectedDetections.add(sortedDetections[i])

            // Compare with all remaining detections
            for (j in i + 1 until sortedDetections.size) {
                // Skip if this detection is already removed
                if (!keep[j]) continue

                // Calculate IoU between boxes
                val iou = calculateIoU(sortedDetections[i].boundingBox, sortedDetections[j].boundingBox)

                // If IoU is above threshold and same class, remove the lower confidence detection
                if (iou > iouThreshold && sortedDetections[i].classId == sortedDetections[j].classId) {
                    keep[j] = false
                }
            }
        }

        return selectedDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        // Calculate intersection area
        val xmin = max(box1.left, box2.left)
        val ymin = max(box1.top, box2.top)
        val xmax = min(box1.right, box2.right)
        val ymax = min(box1.bottom, box2.bottom)

        if (xmin >= xmax || ymin >= ymax) return 0f // No intersection

        val intersectionArea = (xmax - xmin) * (ymax - ymin)

        // Calculate union area
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return intersectionArea / unionArea
    }

    private fun drawDetections(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        // Create a copy of the bitmap to draw on
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // Paint for drawing bounding boxes
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        // Paint for drawing text
        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 40f
            style = Paint.Style.FILL
        }

        // Draw each detection
        for (detection in detections) {
            // Convert normalized coordinates to actual pixel coordinates
            val left = detection.boundingBox.left * bitmap.width
            val top = detection.boundingBox.top * bitmap.height
            val right = detection.boundingBox.right * bitmap.width
            val bottom = detection.boundingBox.bottom * bitmap.height

            // Draw bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Draw label
            val label = "Person: ${(detection.confidence * 100).toInt()}%"
            canvas.drawText(label, left, top - 10, textPaint)
        }

        return resultBitmap
    }

    fun getDetectionResults(): List<DetectionResult> {
        return detectionResults.value
    }

    fun release() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e("PersonFollower", "Error releasing TFLite interpreter", e)
        }
    }

    // Data class to represent a detection result
    data class DetectionResult(
        val classId: Int,
        val confidence: Float,
        val boundingBox: RectF
    )
}