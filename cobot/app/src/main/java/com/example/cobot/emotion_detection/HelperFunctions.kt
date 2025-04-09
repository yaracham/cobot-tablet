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
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

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

fun processFaceWithLandmarker(faceLandmarker: FaceLandmarker?, mpImage: MPImage): FaceLandmarkerResult? {
    return faceLandmarker?.detect(mpImage)
}

fun classifyEmotionFromBlendshapes(blendshapeList: List<List<Category>>?): String {
    val blendshapes = mutableMapOf<String, Float>()

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

    return when {
        smile > 0.3f  -> "Happy"
        frown > 0.2f && browDown > 0.3f -> "Angry"
        eyeWide > 0.3f && mouthOpen > 0.3f && browUp > 0.3f -> "Surprised"
        frown > 0.3f && browUp < 0.1f -> "Sad"
        else -> "Neutral"
    }
}

//
//fun detectAndCropFace(originalBitmap: Bitmap, context: Context): Pair<Bitmap, Rect>? {
//    // Validate input bitmap
//    if (originalBitmap.isRecycled) {
//        Log.e("FaceDetection", "Source bitmap is already recycled")
//        return null
//    }
//
//    // Create TWO copies - one for MediaPipe, one for our operations
//    val mpBitmap = originalBitmap.copy(originalBitmap.config, true)
//    val workingBitmap = originalBitmap.copy(originalBitmap.config, true)
//
//    try {
//        Log.d("FaceDetection", "Processing bitmap: ${originalBitmap.width}x${originalBitmap.height}")
//
//        // Face detection model setup
//        val faceDetectionModelPath = "models/face_detector.tflite"
//
//        // Verify model exists
//        try {
//            context.assets.openFd(faceDetectionModelPath).use { fd ->
//                Log.d("FaceDetection", "Model found: ${fd.length} bytes")
//            }
//        } catch (e: Exception) {
//            Log.e("FaceDetection", "Model not found", e)
//            return null
//        }
//
//        // Configure face detector
//        val options = FaceDetector.FaceDetectorOptions.builder()
//            .setBaseOptions(BaseOptions.builder()
//                .setModelAssetPath(faceDetectionModelPath)
//                .build())
//            .setRunningMode(RunningMode.IMAGE)
//            .setMinDetectionConfidence(0.3f)
//            .build()
//
//        // Create and use face detector
//        val faceDetector = FaceDetector.createFromOptions(context, options)
//
//        try {
//            // Create MediaPipe image with the dedicated bitmap
//            val mpImage = BitmapImageBuilder(mpBitmap).build()
//
//            try {
//                // Run face detection
//                val startTime = System.currentTimeMillis()
//                val result = faceDetector.detect(mpImage)
//                Log.d("FaceDetection", "Detection took ${System.currentTimeMillis() - startTime}ms")
//
//                // Process results
//                if (result.detections().isEmpty()) {
//                    Log.d("FaceDetection", "No faces detected")
//                    return null
//                }
//
//                // Get first face bounding box
//                val face = result.detections()[0].boundingBox()
//                val left = 0.coerceAtLeast(face.left.toInt())
//                val top = 0.coerceAtLeast(face.top.toInt())
//                val right = workingBitmap.width.coerceAtMost(face.right.toInt())
//                val bottom = workingBitmap.height.coerceAtMost(face.bottom.toInt())
//                val width = right - left
//                val height = bottom - top
//
//                if (width <= 0 || height <= 0) {
//                    Log.e("FaceDetection", "Invalid face dimensions: $width x $height")
//                    return null
//                }
//
//                // Create Rect object for the bounding box
//                val boundingBox = Rect(left, top, right, bottom)
//
//                // Crop face region from our working bitmap
//                if (workingBitmap.isRecycled) {
//                    Log.e("FaceDetection", "Working bitmap was recycled prematurely")
//                    return null
//                }
//
//                val croppedFace = Bitmap.createBitmap(workingBitmap, left, top, width, height)
//                Log.d("FaceDetection", "Cropped face: ${croppedFace.width}x${croppedFace.height}")
//
//                return Pair(croppedFace, boundingBox)
//            } finally {
//                mpImage.close()
//                mpBitmap.recycle() // Recycle the MediaPipe-specific bitmap
//            }
//        } finally {
//            faceDetector.close()
//        }
//    } catch (e: Exception) {
//        Log.e("FaceDetection", "Error in face detection", e)
//        return null
//    } finally {
//        // Always clean up our working copy
//        if (!workingBitmap.isRecycled) {
//            workingBitmap.recycle()
//        }
//    }
//}



//fun detectFaceLandmarks(mpImage: MPImage, context: Context): FaceLandmarkerResult? {
//    return try {
//        val interpreter = loadModel(context)
//
//        val inputTensor = interpreter.getInputTensor(0)
//        Log.d("ModelInput", "Shape: ${inputTensor.shape().contentToString()}, Type: ${inputTensor.dataType()}")
//
//        // First create face detector separately
////        val faceDetector = FaceDetector.createFromOptions(
////            context,
////            FaceDetector.FaceDetectorOptions.builder()
////                .setBaseOptions(
////                    BaseOptions.builder()
////                        .setModelAssetPath("models/face_detector.tflite")
////                        .build()
////                )
////                .setRunningMode(RunningMode.IMAGE)
////                .build()
////        )
//
//        // Then create face landmarker
//        FaceLandmarker.createFromOptions(
//            context,
//            FaceLandmarker.FaceLandmarkerOptions.builder()
//                .setBaseOptions(
//                    BaseOptions.builder()
//                        .setModelAssetPath("models/face_landmarker.task")
//                        .build()
//                )
//                .setRunningMode(RunningMode.IMAGE)
//                .setNumFaces(1)
//                .build()
//        ).use { faceLandmarker ->
//            // Perform detection
//            val result = faceLandmarker.detect(mpImage)
////            faceDetector.close() // Clean up detector
//            result
//        }
//    } catch (e: Exception) {
//        Log.e("FaceLandmark", "Detection failed", e)
//        null
//    }
//}
//// Updated preprocessing function
//fun preprocessLandmarks(landmarks: List<NormalizedLandmark>): ByteBuffer {
//    // Expected feature count: 478 landmarks * 2 (x and y only) = 956.
//    val floatCount = 956
//    val bufferSize = floatCount * 4
//
//    // Get a mutable list of x and y coordinates from each landmark.
//    // (Discard z.)
//    val rawList = landmarks.flatMap { listOf(it.x(), it.y()) }.toMutableList()
//
//    // Convert to relative coordinates: subtract the first landmark's (x, y) from each coordinate.
//    if (rawList.size >= 2) {
//        val baseX = rawList[0]
//        val baseY = rawList[1]
//        for (i in rawList.indices step 2) {
//            rawList[i] = rawList[i] - baseX       // relative x
//            rawList[i + 1] = rawList[i + 1] - baseY // relative y
//        }
//    }
//
//    // Normalize: divide every coordinate by the maximum absolute value.
//    val maxVal = rawList.map { kotlin.math.abs(it) }.maxOrNull() ?: 1f
//    val normalizedList = rawList.map { it / maxVal }
//
//    // Create a ByteBuffer of fixed size (956 floats).
//    val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
//        order(ByteOrder.nativeOrder())
//
//        // Put the normalized floats into the buffer (taking only the first 956 values).
//        normalizedList.take(floatCount).forEach { putFloat(it) }
//
//        // If there are fewer than 956 values, pad the buffer with zeros.
//        repeat(floatCount - minOf(normalizedList.size, floatCount)) {
//            putFloat(0f)
//        }
//        rewind()
//    }
//    return buffer
//}
//
//
//
//
//// Alternative version if you need FloatArray instead
//fun preprocessLandmarksToFloatArray(landmarks: List<NormalizedLandmark>): FloatArray {
//    val features = FloatArray(468 * 3) // Fixed size array
//    landmarks.take(468).forEachIndexed { i, landmark ->
//        features[i * 3] = landmark.x()
//        features[i * 3 + 1] = landmark.y()
//        features[i * 3 + 2] = landmark.z()
//    }
//    return features
//}