package com.example.cobot.automated_driving
/**
 * ImageProcessing.kt
 *
 * This file contains utility functions for handling and processing image frames captured via Android’s Camera2 API.
 * It includes conversion from camera-native YUV format to bitmap, and manipulation tools for rotating and mirroring
 * images — particularly useful for front-facing camera analysis.
 *
 * Core Components:
 * - `mediaImageToBitmap`: Converts a YUV_420_888 Image into a JPEG Bitmap by compressing the byte buffer.
 * - `rotateBitmap`: Rotates and mirrors a bitmap image as needed for front-camera orientation correction.
 *
 * Functions:
 * @param image The `Image` object captured from the camera.
 * @param rotationDegrees The angle of rotation to apply to the bitmap.
 * @param mirrorHorizontal Boolean indicating whether the image should be flipped horizontally.
 *
 * These utilities support real-time visual processing workflows, including pose detection, gesture tracking,
 * and augmented reality applications where accurate frame representation is critical.
 */

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream

fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    val yBuffer = mediaImage.planes[0].buffer
    val uBuffer = mediaImage.planes[1].buffer
    val vBuffer = mediaImage.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotation)
    matrix.postScale(-1f, 1f) // Mirror for front camera
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}